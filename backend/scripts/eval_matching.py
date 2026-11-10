"""Đánh giá bộ so khớp tất định trên bộ dữ liệu gán nhãn tay.

Usage:  python -m scripts.eval_matching [--verbose]
Prints the confusion matrix and exact-match precision/recall; exits non-zero when
precision of ``exact`` drops below the guard (a wrong exact merge is the costly error).
"""
import json
import sys
from collections import Counter
from pathlib import Path

from app.services.matching import match_products
from app.services.normalization import normalize_title

DATA = Path(__file__).resolve().parents[1] / "tests" / "data" / "matching_pairs.json"
LABELS = ["exact", "likely", "similar", "different"]


def evaluate() -> dict:
    pairs = json.loads(DATA.read_text(encoding="utf-8"))["pairs"]
    confusion: Counter[tuple[str, str]] = Counter()
    errors = []
    for a, b, label in pairs:
        result = match_products(normalize_title(a), normalize_title(b))
        predicted = result.match_type.value
        confusion[(label, predicted)] += 1
        if predicted != label:
            errors.append({"a": a, "b": b, "label": label, "predicted": predicted, "reason": result.reason})
    tp = confusion[("exact", "exact")]
    predicted_exact = sum(confusion[(lbl, "exact")] for lbl in LABELS)
    actual_exact = sum(confusion[("exact", p)] for p in LABELS)
    return {
        "pairs": len(pairs),
        "accuracy": sum(confusion[(lbl, lbl)] for lbl in LABELS) / len(pairs),
        "exact_precision": tp / predicted_exact if predicted_exact else 1.0,
        "exact_recall": tp / actual_exact if actual_exact else 1.0,
        "confusion": confusion,
        "errors": errors,
    }


def main() -> int:
    report = evaluate()
    print(f"pairs={report['pairs']}  accuracy={report['accuracy']:.3f}  "
          f"exact_precision={report['exact_precision']:.3f}  exact_recall={report['exact_recall']:.3f}")
    print("\nconfusion (rows = label, cols = predicted)")
    print(f"{'':>10}" + "".join(f"{p:>10}" for p in LABELS))
    for lbl in LABELS:
        print(f"{lbl:>10}" + "".join(f"{report['confusion'][(lbl, p)]:>10}" for p in LABELS))
    if "--verbose" in sys.argv:
        for e in report["errors"]:
            print(f"\n[{e['label']} -> {e['predicted']}] {e['reason']}\n  A: {e['a']}\n  B: {e['b']}")
    return 0 if report["exact_precision"] >= 0.98 else 1


if __name__ == "__main__":
    sys.exit(main())
