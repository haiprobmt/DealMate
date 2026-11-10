import pytest

from app.services.normalization import display_title, normalize_title
from app.utils.text import fold, format_vnd


def n(title):
    return normalize_title(title)


def test_fold_and_money():
    assert fold("Kem Chống Nắng, 3,6kg [HOT]") == "kem chong nang 3.6kg hot"
    assert format_vnd(315000) == "315.000đ"
    assert format_vnd(None) == "chưa rõ"


def test_sunscreen_attributes():
    a = n("[HOT] Sữa chống nắng Anessa Perfect UV Sunscreen Skincare Milk 60ml SPF50+ PA++++")
    assert (a.brand, a.category, a.volume_ml) == ("anessa", "sunscreen", 60)
    assert a.traits == {"formula": "milk"}
    assert a.model is None and a.weight_g is None


@pytest.mark.parametrize("title,expected", [
    ("Nước giặt Ariel 3.6kg", 3600), ("Ariel 3,6kg", 3600), ("Ariel 3600g", 3600), ("Gel Anessa 90g", 90),
])
def test_weight_units(title, expected):
    assert n(title).weight_g == expected


@pytest.mark.parametrize("title,expected", [
    ("Philips HD9200 4.1L", 4100), ("Philips 4.1 lít", 4100), ("Anessa 60ML", 60),
])
def test_volume_units(title, expected):
    assert n(title).volume_ml == expected


def test_diaper_size_count_and_type():
    a = n("Bỉm quần Merries L44")
    b = n("Tã quần Merries size L 44 miếng cho bé 9-14kg")
    for x in (a, b):
        assert (x.brand, x.category, x.size, x.count) == ("merries", "diaper", "L", 44)
        assert x.traits == {"diaper_type": "pants"}
        assert x.weight_g is None, "the baby-weight range is not a product weight"
    assert n("Tã dán Merries S82").traits == {"diaper_type": "tape"}


def test_phone_model_storage_color_origin():
    a = n("Apple iPhone 16 Pro Max 512GB Titan Đen chính hãng VN/A")
    assert (a.brand, a.model, a.storage_gb, a.color, a.origin) == (
        "apple", "iphone 16 pro max", 512, "black titanium", "official")
    assert n("iPhone 16 Pro 256GB bản Mỹ LL/A").origin == "imported"
    assert n("iPhone 16 Pro 256GB cũ 99%").condition == "used"
    assert n("iPhone 16 Pro 5G 256GB").weight_g is None, "5G is not five grams"


def test_plain_chinh_hang_is_not_evidence_of_origin():
    assert n("Kem chống nắng Anessa 60ml chính hãng").origin is None


def test_model_code_and_region_suffix():
    assert n("Nồi chiên không dầu Philips HD9200/90 4.1L").model == "hd9200"
    assert n("Máy sấy tóc Dyson Supersonic HD15").model == "hd15"
    assert n("Kem chống nắng SPF50 PA++++ Anessa").model is None


def test_pack_quantity():
    assert n("Combo 2 sữa chống nắng Anessa 60ml").pack_quantity == 2
    assert n("Anessa 60ml x3").pack_quantity == 3
    assert n("Anessa 60ml").pack_quantity is None


def test_accessory_detection():
    assert n("Ốp lưng iPhone 16 Pro trong suốt").category == "accessory"
    assert n("Giá đỡ máy sấy tóc Dyson Supersonic HD15").category == "accessory"


def test_unknown_stays_unknown():
    a = n("Kem chống nắng 60ml")
    assert a.brand is None and a.model is None and a.color is None and a.traits == {}


def test_cobrand_scent_is_not_the_brand():
    assert n("Nước giặt Ariel hương Downy 3.6kg").brand == "ariel"


def test_color_only_where_relevant():
    assert n("Kem dưỡng trắng da Anessa 60ml").color is None
    assert n("Máy sấy tóc Dyson HD15 màu hồng").color == "pink"


def test_display_title_uses_only_known_attributes():
    assert display_title(n("Sữa chống nắng Anessa Perfect UV Skincare Milk 60ml"), "x") == \
        "Anessa Perfect UV Skincare Milk 60ml"
    assert display_title(n("Bỉm quần Merries L44 nội địa Nhật"), "x") == \
        "Merries tã quần size L 44 miếng (nhập khẩu/xách tay)"
    assert display_title(n("Sản phẩm lạ không rõ hãng"), "Tiêu đề gốc") == "Tiêu đề gốc"
