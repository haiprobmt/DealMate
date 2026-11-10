import json
from pathlib import Path

import pytest

from app.ai.query_interpreter import interpret_with_rules
from app.schemas.domain import MatchType
from app.services.matching import is_vaguer_description, match_products, match_query, same_variant
from app.services.normalization import normalize_title as n
from app.services.normalization import query_to_attributes
from app.utils.text import fold
from scripts.eval_matching import evaluate


def mp(a, b):
    return match_products(n(a), n(b))


def mq(query, title):
    return match_query(query_to_attributes(interpret_with_rules(query)), n(title), fold(title))


def test_labeled_dataset_has_at_least_100_pairs_and_meets_thresholds():
    report = evaluate()
    assert report["pairs"] >= 100
    # A false "exact" merges two different products -> it must essentially never happen.
    assert report["exact_precision"] >= 0.98, report["errors"]
    assert report["exact_recall"] >= 0.90, report["errors"]


def test_dataset_covers_every_label():
    pairs = json.loads((Path(__file__).parent / "data" / "matching_pairs.json").read_text())["pairs"]
    counts = {label: sum(1 for p in pairs if p[2] == label) for label in ("exact", "likely", "similar", "different")}
    assert all(c >= 20 for c in counts.values()), counts


@pytest.mark.parametrize("a,b,field", [
    ("Dyson Supersonic HD15 đen", "Dyson Supersonic HD08 đen", "model"),
    ("Anessa Perfect UV Skincare Milk 60ml", "Anessa Perfect UV Skincare Milk 90ml", "volume_ml"),
    ("Nước giặt Ariel Matic túi 3.6kg", "Nước giặt Ariel Matic túi 2.4kg", "weight_g"),
    ("Tã quần Merries L44", "Tã quần Merries L56", "count"),
    ("Tã quần Merries L44", "Tã quần Merries XL44", "size"),
    ("iPhone 16 Pro 256GB Titan Đen", "iPhone 16 Pro 512GB Titan Đen", "storage_gb"),
    ("Anessa Perfect UV Skincare Milk 60ml", "Combo 2 Anessa Perfect UV Skincare Milk 60ml", "pack_quantity"),
    ("iPhone 16 Pro 256GB Titan Đen VN/A", "iPhone 16 Pro 256GB Titan Đen bản Mỹ LL/A", "origin"),
    ("iPhone 16 Pro 256GB Titan Đen", "iPhone 16 Pro 256GB Titan Đen cũ 99%", "condition"),
])
def test_hard_conflicts_are_never_exact(a, b, field):
    result = mp(a, b)
    assert result.match_type in (MatchType.SIMILAR, MatchType.DIFFERENT)
    assert field in result.different_attributes


def test_one_sided_unknown_is_never_exact():
    result = mp("iPhone 16 Pro 256GB", "iPhone 16 Pro 256GB Titan Đen VN/A")
    assert result.match_type == MatchType.LIKELY
    assert {"color", "origin"} <= set(result.unknown_attributes)


def test_different_brand_and_accessory_are_different():
    assert mp("Tã quần Merries L44", "Tã quần Huggies L44").match_type == MatchType.DIFFERENT
    assert mp("iPhone 16 Pro 256GB", "Ốp lưng iPhone 16 Pro").match_type == MatchType.DIFFERENT


def test_gtin_decides_when_present():
    a, b = n("Anessa Milk 60ml"), n("Sữa chống nắng Anessa 60ml")
    a.gtin = b.gtin = "4909978131234"
    assert match_products(a, b).match_type == MatchType.EXACT
    b.gtin = "4909978139999"
    assert match_products(a, b).match_type == MatchType.DIFFERENT


def test_match_result_schema():
    r = mp("Tã quần Merries L44", "Bỉm quần Merries L44")
    assert r.match_type == MatchType.EXACT and 0.9 <= r.confidence <= 1
    assert {"brand", "size", "count"} <= set(r.matched_attributes) and r.reason


def test_query_match_only_checks_what_the_user_asked():
    assert mq("kem chống nắng Anessa 60ml", "Sữa chống nắng Anessa Perfect UV Skincare Milk 60ml").match_type == MatchType.EXACT
    assert mq("kem chống nắng Anessa 60ml", "Anessa Perfect UV Skincare Milk 20ml").match_type == MatchType.SIMILAR
    assert mq("kem chống nắng Anessa 60ml", "Anessa Perfect UV Skincare Gel 90g").match_type == MatchType.SIMILAR
    assert mq("kem chống nắng Anessa 60ml", "Kem chống nắng Anessa Perfect UV").match_type == MatchType.LIKELY
    assert mq("kem chống nắng Anessa 60ml", "Kem chống nắng Bioré 60ml").match_type == MatchType.DIFFERENT


def test_query_match_model_and_accessories():
    assert mq("nồi chiên không dầu Philips HD9200", "Philips Air Fryer HD9200 4.1 lít").match_type == MatchType.EXACT
    assert mq("nồi chiên không dầu Philips HD9200", "Nồi chiên Philips HD9252/90 4.1L").match_type == MatchType.SIMILAR
    assert mq("Dyson HD15 màu đen", "Giá đỡ máy sấy tóc Dyson Supersonic HD15").match_type == MatchType.DIFFERENT
    assert mq("iPhone 16 Pro 256GB", "iPhone 16 Pro 256GB Titan Tự Nhiên cũ 99%").match_type == MatchType.SIMILAR


def test_same_variant_clustering_predicate():
    assert same_variant(n("Tã quần Merries size L 44 miếng"), n("Bỉm quần Merries L44"))
    assert not same_variant(n("Bỉm quần Merries L44"), n("Bỉm quần Merries L44 nội địa Nhật"))
    assert not same_variant(n("Kem chống nắng Anessa 60ml"), n("Anessa Perfect UV Skincare Milk 60ml"))


def test_vague_listing_detection():
    vague, specific = n("Kem chống nắng Anessa 60ml giá sốc"), n("Anessa Perfect UV Skincare Milk 60ml")
    assert is_vaguer_description(vague, specific)
    assert not is_vaguer_description(specific, vague)
    assert not is_vaguer_description(n("Anessa Perfect UV Skincare Milk 60ml"), n("Anessa Perfect UV Skincare Milk 20ml"))
