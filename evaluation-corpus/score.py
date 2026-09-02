#!/usr/bin/env python3
"""
Scores the integrity engine's output against hand-built ground truth.

Standalone evaluation tooling: not part of the shipped Java system, so it is free to
use the Python standard library (difflib is used for the naive-baseline comparison).

Usage:  python3 evaluation-corpus/score.py
"""

import difflib
import json
import pathlib
import sys
from itertools import combinations

CORPUS = pathlib.Path("evaluation-corpus")
RESULTS = pathlib.Path("evaluation-results")
LANGUAGES = ["java", "python", "c"]

# A pair is expected-positive only when both files belong to the same copy cluster.
# Only these two labels mean "should be flagged"; everything else is a negative pair.
POSITIVE_LABELS = {"OBVIOUS-COPY", "DISGUISED-COPY"}

# Categories that describe a whole file. A pair only inherits one of these when both
# halves share it AND they are not in the same copy cluster.
NEGATIVE_FILE_CATEGORIES = {
    "INDEPENDENT-HONEST", "SHARED-BOILERPLATE", "TRIVIAL-ASSIGNMENT",
}


def copy_clusters(files):
    """Group files transitively by the copiedFrom relation.

    Two files copied from the same original are near-identical to each other, so that
    pair is a true positive too even though neither was copied from the other.
    """
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
    clusters = {}
    for path in parent:
        clusters.setdefault(find(path), set()).add(path)
    return {p: find(p) for p in parent}, clusters


def pair_label(left, right, category, cluster_of):
    """The population a pair belongs to, for per-category scoring."""
    if cluster_of[left] == cluster_of[right]:
        # Label a positive pair by its hardest half: a disguised copy is the real test.
        if "DISGUISED-COPY" in (category[left], category[right]):
            return "DISGUISED-COPY"
        return "OBVIOUS-COPY"
    # Not in the same cluster, so this is a negative pair whatever the file
    # categories say. Two OBVIOUS-COPY files copied from *different* originals are
    # unrelated to each other; labelling that pair by its shared file category would
    # silently turn it into an expected positive.
    if category[left] == category[right] and category[left] in NEGATIVE_FILE_CATEGORIES:
        return category[left]
    if category[left] in POSITIVE_LABELS and category[right] in POSITIVE_LABELS:
        return "UNRELATED-COPIES"
    return "CROSS-CATEGORY"


def score_language(language):
    truth = json.loads((CORPUS / "ground-truth.json").read_text())[language]
    report = json.loads((RESULTS / f"results-{language}.json").read_text())

    files = truth["files"]
    category = {f["path"]: f["category"] for f in files}
    cluster_of, _ = copy_clusters(files)

    prefix = f"{report['assignmentId']}/"
    def strip(submission_id):
        return submission_id[len(prefix):] if submission_id.startswith(prefix) else submission_id

    flagged = {tuple(sorted((strip(p["left"]), strip(p["right"])))) for p in report["flagged"]}
    scores = {tuple(sorted((strip(p["left"]), strip(p["right"])))): p["score"]
              for p in report["allPairs"]}

    rows = []
    for left, right in combinations(sorted(category), 2):
        key = tuple(sorted((left, right)))
        if key not in scores:
            continue  # not compared (e.g. unreadable file)
        label = pair_label(left, right, category, cluster_of)
        rows.append({
            "language": language,
            "left": left,
            "right": right,
            "label": label,
            "expected_flag": label in POSITIVE_LABELS,
            "flagged": key in flagged,
            "score": scores[key],
        })
    return rows, report


def raw_text_similarity(language, left, right):
    """Naive baseline: character-level similarity of the untouched source text."""
    root = CORPUS / language
    a = (root / left).read_text()
    b = (root / right).read_text()
    return difflib.SequenceMatcher(None, a, b).ratio()


def prf(tp, fp, fn):
    precision = tp / (tp + fp) if tp + fp else float("nan")
    recall = tp / (tp + fn) if tp + fn else float("nan")
    f1 = (2 * precision * recall / (precision + recall)
          if tp and precision + recall else 0.0 if tp + fp + fn else float("nan"))
    return precision, recall, f1


def fmt(value):
    return "  n/a " if value != value else f"{value:5.3f}"


def main():
    all_rows = []
    reports = {}
    for language in LANGUAGES:
        rows, report = score_language(language)
        all_rows.extend(rows)
        reports[language] = report

    lines = []
    def emit(text=""):
        print(text)
        lines.append(text)

    emit("=" * 78)
    emit("EVALUATION AGAINST HAND-BUILT GROUND TRUTH")
    emit("=" * 78)
    emit()

    # ---- overall and per-language -------------------------------------------------
    emit("OVERALL")
    emit("-" * 78)
    emit(f"{'scope':<12}{'pairs':>7}{'TP':>5}{'FP':>5}{'FN':>5}{'TN':>6}"
         f"{'precision':>11}{'recall':>9}{'F1':>8}")
    for scope in LANGUAGES + ["ALL"]:
        rows = all_rows if scope == "ALL" else [r for r in all_rows if r["language"] == scope]
        tp = sum(1 for r in rows if r["expected_flag"] and r["flagged"])
        fp = sum(1 for r in rows if not r["expected_flag"] and r["flagged"])
        fn = sum(1 for r in rows if r["expected_flag"] and not r["flagged"])
        tn = sum(1 for r in rows if not r["expected_flag"] and not r["flagged"])
        p, rc, f1 = prf(tp, fp, fn)
        emit(f"{scope:<12}{len(rows):>7}{tp:>5}{fp:>5}{fn:>5}{tn:>6}"
             f"{fmt(p):>11}{fmt(rc):>9}{fmt(f1):>8}")
    emit()

    # ---- per pair-category --------------------------------------------------------
    emit("BY PAIR CATEGORY (all languages pooled)")
    emit("-" * 78)
    emit(f"{'category':<22}{'pairs':>7}{'flagged':>9}{'expected':>10}"
         f"{'precision':>11}{'recall':>9}{'F1':>8}")
    categories = ["OBVIOUS-COPY", "DISGUISED-COPY", "INDEPENDENT-HONEST",
                  "SHARED-BOILERPLATE", "TRIVIAL-ASSIGNMENT", "UNRELATED-COPIES",
                  "CROSS-CATEGORY"]
    for label in categories:
        rows = [r for r in all_rows if r["label"] == label]
        if not rows:
            continue
        expected = rows[0]["expected_flag"]
        flagged = sum(1 for r in rows if r["flagged"])
        if expected:
            tp, fn, fp = flagged, len(rows) - flagged, 0
        else:
            tp, fn, fp = 0, 0, flagged
        p, rc, f1 = prf(tp, fp, fn)
        emit(f"{label:<22}{len(rows):>7}{flagged:>9}{'yes' if expected else 'no':>10}"
             f"{fmt(p):>11}{fmt(rc):>9}{fmt(f1):>8}")
    emit()

    # ---- the three specific questions ---------------------------------------------
    emit("THE HEADLINE CLAIMS")
    emit("-" * 78)

    trivial = [r for r in all_rows if r["label"] == "TRIVIAL-ASSIGNMENT"]
    tflag = sum(1 for r in trivial if r["flagged"])
    emit(f"TRIVIAL-ASSIGNMENT, mixed into the full batch:")
    emit(f"  {len(trivial) - tflag}/{len(trivial)} correctly NOT flagged"
         f"   (mean similarity {sum(r['score'] for r in trivial) / len(trivial):.4f})")

    for language in LANGUAGES:
        isolated = json.loads((RESULTS / f"trivial-{language}.json").read_text())
        emit(f"  isolated {language:<7} cohort: {len(isolated['flagged'])}/"
             f"{isolated['pairCount']} flagged, median {isolated['cohortMedian']:.4f},"
             f" MAD {isolated['cohortMad']:.4f}")
    emit()

    boiler = [r for r in all_rows if r["label"] == "SHARED-BOILERPLATE"]
    bflag = sum(1 for r in boiler if r["flagged"])
    emit(f"SHARED-BOILERPLATE:")
    emit(f"  {len(boiler) - bflag}/{len(boiler)} correctly NOT flagged"
         f"   (mean similarity {sum(r['score'] for r in boiler) / len(boiler):.4f})")
    emit()

    emit("DISGUISED-COPY vs a naive text diff:")
    disguised = [r for r in all_rows if r["label"] == "DISGUISED-COPY"]
    obvious = [r for r in all_rows if r["label"] == "OBVIOUS-COPY"]
    for label, rows in (("obvious", obvious), ("disguised", disguised)):
        raws = [raw_text_similarity(r["language"], r["left"], r["right"]) for r in rows]
        caught = sum(1 for r in rows if r["flagged"])
        emit(f"  {label:<10} caught {caught}/{len(rows)}"
             f"   tool mean {sum(r['score'] for r in rows) / len(rows):.4f}"
             f"   raw-text mean {sum(raws) / len(raws):.4f}"
             f"   raw-text min {min(raws):.4f}")
    emit()
    naive_would_catch = sum(
        1 for r in disguised
        if raw_text_similarity(r["language"], r["left"], r["right"]) >= 0.80)
    emit(f"  A naive text-diff detector at a 0.80 threshold would catch"
         f" {naive_would_catch}/{len(disguised)} disguised copies.")
    emit(f"  The pipeline caught {sum(1 for r in disguised if r['flagged'])}/{len(disguised)}.")
    emit()

    # ---- every mistake, named -----------------------------------------------------
    emit("EVERY FALSE POSITIVE")
    emit("-" * 78)
    fps = sorted((r for r in all_rows if not r["expected_flag"] and r["flagged"]),
                 key=lambda r: -r["score"])
    if not fps:
        emit("  none")
    for r in fps:
        raw = raw_text_similarity(r["language"], r["left"], r["right"])
        emit(f"  {r['score']:.4f} (raw text {raw:.2f})  [{r['label']}] "
             f"{r['language']}: {r['left']} <-> {r['right']}")
    emit()

    emit("EVERY FALSE NEGATIVE")
    emit("-" * 78)
    fns = sorted((r for r in all_rows if r["expected_flag"] and not r["flagged"]),
                 key=lambda r: -r["score"])
    if not fns:
        emit("  none")
    for r in fns:
        raw = raw_text_similarity(r["language"], r["left"], r["right"])
        emit(f"  {r['score']:.4f} (raw text {raw:.2f})  [{r['label']}] "
             f"{r['language']}: {r['left']} <-> {r['right']}")
    emit()
    emit("=" * 78)

    (RESULTS / "score-output.txt").write_text("\n".join(lines) + "\n")
    json.dump(all_rows, (RESULTS / "pair-classifications.json").open("w"), indent=2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
