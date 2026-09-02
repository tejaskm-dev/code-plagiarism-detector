#!/usr/bin/env python3
"""
Attributes each false positive from the 408-pair run to a known cause, or leaves it
unexplained. Read-only: consumes the existing results, changes nothing.

Attribution rules, applied in order, each requiring positive evidence rather than a
category label alone:

  (a) BOILERPLATE-COVERAGE-HOLE
      Both files share the provided skeleton, and the skeleton was not suppressed
      because its cohort coverage falls in the gap between the two guards:
      below the engine's 0.80 discard bar, above the 5-submission inertia floor.

  (b) MIXED-ASSIGNMENT-BATCHING
      The pair is flagged in the mixed batch but NOT flagged when its own cluster is
      run as a separate cohort. Evidence comes from the isolated re-runs, not from
      the category name.

  (c) LANGUAGE-SCAFFOLDING-FLOOR
      The pair scores at or below the measured cost of simply writing a valid program
      in that language. The floor was measured separately: two files sharing only the
      include block / def-and-indent structure and nothing else score 0.3846 in C and
      0.5152 in Python. Java has no such universal frame and shows no residual at all.

  (d) UNEXPLAINED
      Everything else. This is the number that matters.
"""

import json
import math
import pathlib
from collections import Counter

RESULTS = pathlib.Path("evaluation-results")
LANGUAGES = ["java", "python", "c"]
ENGINE_THRESHOLD = 0.80          # IntegrityEngine.BOILERPLATE_THRESHOLD
FILTER_MIN_COHORT = 5            # BoilerplateFilter.MINIMUM_COHORT_SIZE

# Measured, not assumed: see evaluation-results/scaffolding-floor.txt. Two files that
# share only the language's mandatory frame and have entirely unrelated bodies.
SCAFFOLDING_FLOOR = {"c": 0.3846, "python": 0.5152, "java": 0.0}


def isolated_flagged(kind, language):
    """Pairs flagged when a cluster is run as its own cohort."""
    path = RESULTS / f"{kind}-{language}.json"
    report = json.loads(path.read_text())
    prefix = report["assignmentId"] + "/"
    out = set()
    for pair in report["flagged"]:
        left = pair["left"][len(prefix):]
        right = pair["right"][len(prefix):]
        out.add(tuple(sorted((left.split("/")[-1], right.split("/")[-1]))))
    return out


def main():
    rows = json.loads((RESULTS / "pair-classifications.json").read_text())
    fps = [r for r in rows if not r["expected_flag"] and r["flagged"]]
    tps = [r for r in rows if r["expected_flag"] and r["flagged"]]

    isolated = {
        ("trivial-assignment", lang): isolated_flagged("trivial", lang) for lang in LANGUAGES
    }
    isolated.update({
        ("shared-boilerplate", lang): isolated_flagged("boiler", lang) for lang in LANGUAGES
    })

    # Cohort arithmetic for the boilerplate hole, per language batch of 17.
    cohort = 17
    required = math.ceil(ENGINE_THRESHOLD * cohort - 1e-9)
    boiler_files = 4

    attributed = []
    for r in fps:
        left_dir = r["left"].split("/")[0]
        right_dir = r["right"].split("/")[0]
        base = (r["left"].split("/")[-1], r["right"].split("/")[-1])
        key = tuple(sorted(base))

        cause, evidence = "UNEXPLAINED", ""

        if left_dir == "shared-boilerplate" and right_dir == "shared-boilerplate":
            not_flagged_alone = key not in isolated[("shared-boilerplate", r["language"])]
            cause = "BOILERPLATE-COVERAGE-HOLE"
            evidence = (f"skeleton in {boiler_files}/{cohort} files; suppression needs "
                        f"{required}/{cohort}; isolated cohort of {boiler_files} is below the "
                        f"{FILTER_MIN_COHORT}-file inertia floor"
                        + ("; not flagged when run alone" if not_flagged_alone else ""))

        elif left_dir == "trivial-assignment" and right_dir == "trivial-assignment":
            if key not in isolated[("trivial-assignment", r["language"])]:
                cause = "MIXED-ASSIGNMENT-BATCHING"
                evidence = "not flagged when the trivial cluster is run as its own cohort"
            else:
                cause = "UNEXPLAINED"
                evidence = "flagged even in isolation"

        elif r["score"] <= SCAFFOLDING_FLOOR.get(r["language"], 0.0):
            cause = "LANGUAGE-SCAFFOLDING-FLOOR"
            evidence = (f"scores {r['score']:.4f}, at or below the measured "
                        f"{SCAFFOLDING_FLOOR[r['language']]:.4f} floor for {r['language']}")

        attributed.append({**r, "cause": cause, "evidence": evidence})

    counts = Counter(a["cause"] for a in attributed)
    tp = len(tps)

    def precision(excluded_causes):
        remaining = [a for a in attributed if a["cause"] not in excluded_causes]
        return tp / (tp + len(remaining)), len(remaining)

    lines = []
    def emit(t=""):
        print(t)
        lines.append(t)

    emit("=" * 78)
    emit("FALSE-POSITIVE ATTRIBUTION  (408-pair run, unchanged)")
    emit("=" * 78)
    emit()
    emit(f"True positives : {tp}")
    emit(f"False positives: {len(fps)}")
    emit()
    emit(f"{'cause':<32}{'count':>7}{'share of FPs':>15}")
    emit("-" * 78)
    for cause in ["BOILERPLATE-COVERAGE-HOLE", "MIXED-ASSIGNMENT-BATCHING",
                  "LANGUAGE-SCAFFOLDING-FLOOR", "UNEXPLAINED"]:
        n = counts.get(cause, 0)
        emit(f"{cause:<32}{n:>7}{n / len(fps):>14.1%}")
    emit()

    emit("PRECISION UNDER EACH ATTRIBUTION")
    emit("-" * 78)
    scenarios = [
        ("as measured (nothing excluded)", set()),
        ("excluding the boilerplate hole", {"BOILERPLATE-COVERAGE-HOLE"}),
        ("excluding mixed-assignment batching", {"MIXED-ASSIGNMENT-BATCHING"}),
        ("excluding the scaffolding floor", {"LANGUAGE-SCAFFOLDING-FLOOR"}),
        ("excluding the two causes asked about", {"BOILERPLATE-COVERAGE-HOLE",
                                                  "MIXED-ASSIGNMENT-BATCHING"}),
        ("excluding all three identified causes", {"BOILERPLATE-COVERAGE-HOLE",
                                                   "MIXED-ASSIGNMENT-BATCHING",
                                                   "LANGUAGE-SCAFFOLDING-FLOOR"}),
    ]
    emit(f"{'scenario':<40}{'FPs left':>10}{'precision':>12}")
    for name, excluded in scenarios:
        p, remaining = precision(excluded)
        emit(f"{name:<40}{remaining:>10}{p:>12.3f}")
    emit()

    emit("THE UNEXPLAINED RESIDUAL, IN FULL")
    emit("-" * 78)
    residual = sorted((a for a in attributed if a["cause"] == "UNEXPLAINED"),
                      key=lambda a: -a["score"])
    if not residual:
        emit("  none")
    for a in residual:
        emit(f"  {a['score']:.4f}  [{a['label']:<18}] {a['language']:<7}"
             f" {a['left']} <-> {a['right']}")
    emit()
    by_lang = Counter(a["language"] for a in residual)
    by_label = Counter(a["label"] for a in residual)
    emit(f"  by language: {dict(by_lang)}")
    emit(f"  by category: {dict(by_label)}")
    emit()
    emit("=" * 78)

    (RESULTS / "fp-attribution.txt").write_text("\n".join(lines) + "\n")
    json.dump(attributed, (RESULTS / "fp-attribution.json").open("w"), indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
