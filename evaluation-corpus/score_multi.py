#!/usr/bin/env python3
"""
Compares the adaptive modified z-score against a SINGLE fixed similarity threshold
across four assignments of differing difficulty, each run as its own batch.

The fixed threshold is 0.8118 -- the value that was optimal on the first corpus. It is
deliberately NOT retuned per assignment: the question is whether a threshold fitted to
one test set generalises to others, which is the same failure mode the adaptive method
is accused of avoiding.
"""

import json
import pathlib
import statistics
from itertools import combinations

CORPUS = pathlib.Path("evaluation-corpus/multi-assignment")
RESULTS = pathlib.Path("evaluation-results")
FIXED_THRESHOLD = 0.8118
ASSIGNMENTS = ["a0-degenerate", "a1-constrained", "a2-medium", "a3-open-ended"]
DIFFICULTY = {
    "a0-degenerate": "no design freedom",
    "a1-constrained": "little freedom",
    "a2-medium": "real choices",
    "a3-open-ended": "unconstrained",
}


def clusters(files):
    parent = {f["path"]: f["path"] for f in files}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for f in files:
        if f["copiedFrom"]:
            a, b = find(f["path"]), find(f["copiedFrom"])
            if a != b:
                parent[a] = b
    return {p: find(p) for p in parent}


def load(assignment):
    truth = json.loads((CORPUS / "ground-truth.json").read_text())[assignment]
    report = json.loads((RESULTS / f"multi-{assignment}.json").read_text())
    prefix = report["assignmentId"] + "/"

    def strip(sid):
        return sid[len(prefix):] if sid.startswith(prefix) else sid

    cluster_of = clusters(truth["files"])
    category = {f["path"]: f["category"] for f in truth["files"]}
    flagged = {tuple(sorted((strip(p["left"]), strip(p["right"])))) for p in report["flagged"]}
    scores = {tuple(sorted((strip(p["left"]), strip(p["right"])))): p["score"]
              for p in report["allPairs"]}

    rows = []
    for left, right in combinations(sorted(category), 2):
        key = tuple(sorted((left, right)))
        if key not in scores:
            continue
        positive = cluster_of[left] == cluster_of[right]
        rows.append({
            "assignment": assignment,
            "left": left, "right": right,
            "expected": positive,
            "adaptive": key in flagged,
            "fixed": scores[key] >= FIXED_THRESHOLD,
            "score": scores[key],
            "cat_left": category[left], "cat_right": category[right],
        })
    return rows, report


def prf(rows, method):
    tp = sum(1 for r in rows if r["expected"] and r[method])
    fp = sum(1 for r in rows if not r["expected"] and r[method])
    fn = sum(1 for r in rows if r["expected"] and not r[method])
    p = tp / (tp + fp) if tp + fp else float("nan")
    rc = tp / (tp + fn) if tp + fn else float("nan")
    f1 = 2 * p * rc / (p + rc) if tp and (p + rc) else (0.0 if tp + fp + fn else float("nan"))
    return tp, fp, fn, p, rc, f1


def fmt(v):
    return "  n/a" if v != v else f"{v:5.3f}"


def main():
    all_rows = []
    reports = {}
    for a in ASSIGNMENTS:
        rows, report = load(a)
        all_rows.extend(rows)
        reports[a] = report

    lines = []
    def emit(t=""):
        print(t)
        lines.append(t)

    emit("=" * 92)
    emit("ADAPTIVE vs A SINGLE FIXED THRESHOLD, ACROSS FOUR DIFFICULTY LEVELS")
    emit(f"Fixed threshold = {FIXED_THRESHOLD} (optimal on the FIRST corpus; not retuned here)")
    emit("Each assignment run as its own batch.")
    emit("=" * 92)
    emit()

    emit("COHORT SHAPE PER ASSIGNMENT")
    emit("-" * 92)
    emit(f"{'assignment':<17}{'design freedom':<20}{'honest median':>15}{'honest max':>12}"
         f"{'MAD':>9}{'tier':>8}")
    for a in ASSIGNMENTS:
        rows = [r for r in all_rows if r["assignment"] == a]
        honest = [r["score"] for r in rows
                  if r["cat_left"] == "INDEPENDENT-HONEST" and r["cat_right"] == "INDEPENDENT-HONEST"]
        rep = reports[a]
        scores = [p["score"] for p in rep["allPairs"]]
        mad = rep["cohortMad"]
        # The JSON report does not carry the MAE, so recompute it the same way the
        # engine does: mean absolute deviation about the median.
        med = statistics.median(scores)
        mae = sum(abs(s - med) for s in scores) / len(scores)
        tier = "1 (MAD)" if mad > 0 else ("2 (MAE)" if mae > 0 else "3 (guard)")
        emit(f"{a:<17}{DIFFICULTY[a]:<20}{statistics.median(honest):>15.4f}"
             f"{max(honest):>12.4f}{mad:>9.4f}{tier:>8}")
    emit()

    emit("PER-ASSIGNMENT SCORES")
    emit("-" * 92)
    emit(f"{'assignment':<17}{'method':<10}{'TP':>4}{'FP':>4}{'FN':>4}"
         f"{'precision':>11}{'recall':>9}{'F1':>8}")
    for a in ASSIGNMENTS:
        rows = [r for r in all_rows if r["assignment"] == a]
        for method in ("adaptive", "fixed"):
            tp, fp, fn, p, rc, f1 = prf(rows, method)
            emit(f"{a if method == 'adaptive' else '':<17}{method:<10}"
                 f"{tp:>4}{fp:>4}{fn:>4}{fmt(p):>11}{fmt(rc):>9}{fmt(f1):>8}")
        emit()

    emit("POOLED ACROSS ALL FOUR ASSIGNMENTS")
    emit("-" * 92)
    emit(f"{'method':<12}{'pairs':>7}{'TP':>5}{'FP':>5}{'FN':>5}"
         f"{'precision':>11}{'recall':>9}{'F1':>8}")
    for method in ("adaptive", "fixed"):
        tp, fp, fn, p, rc, f1 = prf(all_rows, method)
        emit(f"{method:<12}{len(all_rows):>7}{tp:>5}{fp:>5}{fn:>5}"
             f"{fmt(p):>11}{fmt(rc):>9}{fmt(f1):>8}")
    emit()

    emit("WHERE THE TWO METHODS DISAGREE")
    emit("-" * 92)
    disagree = [r for r in all_rows if r["adaptive"] != r["fixed"]]
    emit(f"  {len(disagree)} of {len(all_rows)} pairs")
    for a in ASSIGNMENTS:
        rows = [r for r in disagree if r["assignment"] == a]
        if not rows:
            continue
        only_fixed = [r for r in rows if r["fixed"]]
        only_adaptive = [r for r in rows if r["adaptive"]]
        emit(f"  {a}:")
        if only_fixed:
            correct = sum(1 for r in only_fixed if r["expected"])
            emit(f"     fixed flags but adaptive does not : {len(only_fixed):>3}"
                 f"  ({correct} correct, {len(only_fixed) - correct} false)")
        if only_adaptive:
            correct = sum(1 for r in only_adaptive if r["expected"])
            emit(f"     adaptive flags but fixed does not : {len(only_adaptive):>3}"
                 f"  ({correct} correct, {len(only_adaptive) - correct} false)")
    emit()
    emit("=" * 92)

    (RESULTS / "multi-assignment-scores.txt").write_text("\n".join(lines) + "\n")
    json.dump(all_rows, (RESULTS / "multi-pair-classifications.json").open("w"), indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
