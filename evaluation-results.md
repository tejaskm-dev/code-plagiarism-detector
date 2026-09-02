# Evaluation against a hand-built adversarial corpus

A labeled corpus was constructed by hand, the shipped CLI was run against it unmodified,
and the output scored against ground truth recorded at construction time. No source file
under `src/main` was touched at any point in this evaluation.

- Corpora: `evaluation-corpus/` (51 files) and `evaluation-corpus/multi-assignment/` (48 files)
- Scorers: `score.py`, `attribute_fps.py`, `score_multi.py`
- Raw output: `evaluation-results/`

## 0. Methodology, including what went wrong in it

Two errors in this evaluation were caught and corrected before publication. Both are
recorded here rather than quietly fixed, because in each case the broken version produced
a plausible-looking number rather than an obvious failure.

**Self-correction 1 — the scorer invented nine false negatives.** The first run reported
recall 0.710 with nine misses. One was `oc_evan ↔ oc_farah`: two files copied from
*different* originals, and therefore unrelated to each other. The pair-labelling logic
fell through to "both files share a category" and marked the pair expected-positive.
Corrected, recall went to 1.000 and the false negatives vanished entirely. **The tool had
been right the whole time; the measuring instrument was wrong.** Had the individual
mistakes not been printed by name, 0.710 would have shipped as a finding about the
detector.

**Self-correction 2 — an overstated claim caught before it shipped.** A draft of this
report stated that "all 19 true positives scored above 0.87, while the false-positive tail
sits below 0.82", implying clean separation. Verifying it against the data showed the true
positives bottom out at 0.8056 and the false positives top out at 0.8118 — the two ranges
**overlap**. The claim was flattering, load-bearing, and false. Correcting it is what
prompted the fixed-threshold comparison in §7, which produced the most significant result
in the evaluation.

These are the fourth and fifth harness self-corrections recorded in this project, and the
pattern has been identical every time: a broken measurement reads as a believable result,
not as an error. Every number in this report that could be checked against the raw pair
list has been.

## 1. Corpus composition

51 files across three languages, 17 per language, in five categories. Each language
solves one non-trivial problem (Java: integer → Roman numeral; Python: run-length
encoding; C: palindrome detection) plus a separate trivial problem and a separate
boilerplate exercise.

| Category | Java | Python | C | Total |
|---|---:|---:|---:|---:|
| INDEPENDENT-HONEST | 4 | 4 | 4 | 12 |
| OBVIOUS-COPY | 2 | 2 | 2 | 6 |
| DISGUISED-COPY | 3 | 3 | 3 | 9 |
| SHARED-BOILERPLATE | 4 | 4 | 4 | 12 |
| TRIVIAL-ASSIGNMENT | 4 | 4 | 4 | 12 |
| **Total** | **17** | **17** | **17** | **51** |

Copies were recorded with their source. Ground truth treats two files copied from the
same original as a positive pair as well, since they are near-identical to each other;
that yields 19 expected-positive pairs (6 Java, 7 Python, 6 C) out of 408 compared.

## 2. Results

| Scope | Pairs | TP | FP | FN | TN | Precision | Recall | F1 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Java | 136 | 6 | 12 | 0 | 118 | 0.333 | **1.000** | 0.500 |
| Python | 136 | 7 | 18 | 0 | 111 | 0.280 | **1.000** | 0.438 |
| C | 136 | 6 | 19 | 0 | 111 | 0.240 | **1.000** | 0.387 |
| **All** | **408** | **19** | **49** | **0** | **340** | **0.279** | **1.000** | **0.437** |

By pair category, pooled across languages:

| Category | Pairs | Flagged | Should flag? | Precision | Recall | F1 |
|---|---:|---:|---|---:|---:|---:|
| OBVIOUS-COPY | 6 | 6 | yes | 1.000 | 1.000 | **1.000** |
| DISGUISED-COPY | 13 | 13 | yes | 1.000 | 1.000 | **1.000** |
| INDEPENDENT-HONEST | 18 | 3 | no | — | — | — |
| SHARED-BOILERPLATE | 18 | 18 | no | — | — | — |
| TRIVIAL-ASSIGNMENT | 18 | 15 | no | — | — | — |
| UNRELATED-COPIES | 26 | 5 | no | — | — | — |
| CROSS-CATEGORY | 309 | 8 | no | — | — | — |

**Recall is perfect and precision is poor.** Every planted copy was caught; roughly
seven in ten flags are wrong.

## 3. The three specific questions

### Disguised copies vs a naive text diff — comfortably passed

All 13 disguised-copy pairs were caught. Their **raw text similarity averages 0.365 and
falls as low as 0.134** (`difflib.SequenceMatcher` on untouched source). A naive
text-diff detector at a 0.80 threshold catches **0 of 13**; the pipeline catches
**13 of 13**.

This is the clearest validation in the evaluation: renaming every identifier, reordering
functions, rewriting comments and reformatting reduced surface text similarity to under a
fifth in the worst case, and the tokenizer normalisation absorbed all of it. Mean tool
score on those same pairs was 0.929.

### Trivial assignments — the claim holds, but only under its actual condition

| Setting | Flagged |
|---|---|
| Trivial cluster run as its own cohort (Java) | **0 of 6** |
| Trivial cluster run as its own cohort (Python) | **0 of 6** |
| Trivial cluster run as its own cohort (C) | **0 of 6** |
| Trivial pairs inside the mixed 17-file batch | **15 of 18** |

Run as its own cohort — which is what "a trivial assignment" actually means — the claim
holds exactly, in all three languages, on hand-written files rather than synthetic data.
Median similarity was 0.42–0.58 and nothing was flagged, because every pair was equally
similar so no pair was unusual.

Mixed into a batch alongside a harder problem, those same pairs are flagged. That is not
a contradiction: they genuinely are outliers *relative to that batch*. It is a statement
about what the statistic is defined against. **The guarantee is conditional on the cohort
being one assignment**, and the CLI does nothing to enforce that.

### Shared boilerplate — failed outright

**18 of 18 shared-boilerplate pairs were flagged.** The mechanism is worth stating
precisely, because it is not what it looks like:

- In the full batch the skeleton appears in 4 of 17 files. The engine discards a
  fingerprint as boilerplate only when it appears in ≥80% of the cohort — 14 of 17 here.
  4 is nowhere near it, so **nothing was suppressed**.
- Run in isolation the cluster is 4 files, below `BoilerplateFilter.MINIMUM_COHORT_SIZE`
  of 5, so the filter is inert and again **nothing was suppressed**.

The similarity scores confirm it: Java scored 0.6975 mean in both runs, identical to four
decimal places. What differs is the surrounding distribution. In isolation those pairs are
the entire cohort, so none stands out and none is flagged. In the mixed batch they sit far
above a low median, so all six are flagged.

The statistics behaved correctly in both cases. The boilerplate filter simply never got
the chance to do its job, because its threshold is calibrated for starter code issued to
*everyone*, not for a sub-group sharing a skeleton.

## 4. Surprises worth recording

**A scorer bug produced nine phantom false negatives.** The first run reported recall
0.710 with 9 misses, including `oc_evan ↔ oc_farah` — two files copied from *different*
originals, therefore unrelated to each other. My `pair_label` fell through to "both files
share a category" and labeled the pair positive. Fixed, recall went to 1.000 and the
false negatives vanished entirely. The tool had been right all along; the measuring
instrument was wrong. This is the fifth harness self-correction across this project, and
the pattern holds: **the broken harness reported a plausible-looking number, not an
obvious error.** Anything less than reading the individual mistakes by name would have
shipped 0.710 as a finding.

**Two categories produce two-thirds of all false positives.** Of 49, eighteen are shared
boilerplate and fifteen are trivial-assignment — 67% from 36 of 408 pairs. The remaining
16 are spread thinly across 372 pairs. Precision is not uniformly poor; it collapses in
two specific, identifiable situations, both of which are detectable in advance from the
corpus rather than the results.

**C is the weakest language, and mechanically so.** C had 19 false positives against
Java's 12. Its files share `#include <stdio.h>`, `#include <string.h>`, `printf`,
`main(void)` and `return 0` — a fixed frame present in almost every file. Three of the
five INDEPENDENT-HONEST false positives are C, and the lowest-scoring C false positive
(0.3034) has raw text similarity of just 0.26, meaning the shared structure the tool found
is real. Language-level idiom floor is a genuine confound, not noise.

**Cross-category pairs are almost never flagged** — 8 of 309, 2.6%. Files solving
different problems reliably score near zero. The failure mode is entirely within
same-problem populations, which is the harder and more realistic case.

## 5. What this evaluation does not establish

The corpus is small (51 files, 19 positive pairs), constructed by one author, and the
disguises are the ones I thought to apply. Absolute precision and recall from a corpus
this size should be read as indicative, not measured — a single additional false negative
would move recall from 1.000 to 0.947.

The two failing categories were also deliberately constructed to be hard. A real cohort
where the starter code is issued to every student would suppress correctly; this corpus
models a sub-group sharing a skeleton, which is the adversarial case.

## 6. Practical conclusions

1. **Recall is the strength.** Nothing planted escaped, including disguises that defeat
   text comparison entirely. As a triage tool that must not miss, it works.
2. **Batch composition is a correctness input, not a convenience.** Analysing one
   assignment per batch is not a usage recommendation — mixing populations changes what
   every flag means. This deserves enforcement or a loud warning in the CLI.
3. **The boilerplate threshold has a hole between the two guards.** Structure shared by
   a substantial minority (roughly 5%–80% of a cohort) is neither suppressed nor
   statistically ordinary, and lands as a false positive. This is the same hazard
   documented in Stage 4, arriving from the opposite direction: there the threshold was
   too low and deleted evidence; here it is too high and admits noise.
4. **Precision needs the human step the report already describes.** At 0.279, roughly two
   in seven flags are real. Ranking helps but does not separate cleanly — see §7.


## 7. Does the adaptive threshold actually earn its cost?

This section exists because a claim I first wrote here was wrong, and checking it produced
the most interesting result in the evaluation.

I initially wrote that "all 19 true positives scored above 0.87, while the false-positive
tail sits below 0.82". The second half is true; the first is not. The real ranges:

| | min | max | n |
|---|---:|---:|---:|
| True positives | **0.8056** | 1.0000 | 19 |
| False positives | 0.1818 | **0.8118** | 49 |

They overlap. One false positive (a C boilerplate pair at 0.8118) outscores the two
weakest true positives (0.8056). No score cut-off separates them perfectly.

### The uncomfortable comparison

Sweeping every possible fixed score threshold over this corpus:

| Method | Precision | Recall | F1 |
|---|---:|---:|---:|
| Adaptive modified z-score (what ships) | 0.279 | **1.000** | 0.437 |
| Best fixed score threshold (0.8118) | **1.000** | 0.895 | **0.944** |

**On this corpus a dumb fixed threshold more than doubles the F1 score.** That is a direct
challenge to the project's central design claim, so it should not be waved away.

### The case the adaptive method exists for

The obvious rebuttal is that a fixed threshold fails when an assignment is so constrained
that honest solutions are forced to be near-identical — the case the median/MAD design was
built for. This corpus's TRIVIAL-ASSIGNMENT cluster does not actually test that: its
honest pairs top out around 0.74, comfortably under any sensible fixed bar.

So I added one: a `degenerate/` cluster (12 files, 4 per language, all honest by
construction) implementing a task with essentially no design freedom — a two-field value
type with getters and a string conversion.

| Language | Pair scores | Flagged by z-score | Flagged by a fixed 0.81 bar |
|---|---|---:|---:|
| Java | 1.000 – 1.000 | **6 / 6** | 6 / 6 |
| Python | 0.857 – 1.000 | **0 / 6** | 6 / 6 |
| C | 1.000 – 1.000 | **6 / 6** | 6 / 6 |

Python is the vindication: honest work scoring up to 1.000, and the adaptive method
correctly flags nobody while a fixed threshold accuses the entire class. That is exactly
the scenario the design was built for, and it works.

Java and C are the problem. There every pair scored *exactly* 1.000, so the MAD and the
MAE both collapse to zero, tier 3 fires, and the absolute guardrail (0.90) flags all six
pairs. **The one part of the statistical design that reintroduces a fixed threshold does
so precisely in the most degenerate case — the case the rest of the design exists to
handle.**

That behaviour is arguably correct: a cohort where every pair is byte-identical after
normalisation genuinely does warrant a human look, whether the cause is mass copying or an
assignment with no room to differ. But it means the headline claim needs qualifying.

### Corrected statement of the claim

> A trivially constrained assignment will not produce false accusations **provided the
> cohort retains some spread**. Where honest solutions vary at all — even between 0.857
> and 1.000 — the adaptive threshold correctly stays silent where a fixed one would flag
> everyone. Where they are perfectly uniform, the guardrail flags the entire cohort by
> design, and a human must distinguish "trivial assignment" from "everyone copied".

That is a narrower claim than the project has been making, and it is the one the evidence
supports.

### What would settle it

Neither method wins outright: fixed is better on the main corpus, adaptive is better on
degenerate Python, and both fail on degenerate Java/C. The honest reading is that this
corpus is too small and too deliberately adversarial to rank them. What would settle it is
a real multi-assignment corpus spanning genuinely different difficulty levels — precisely
the variation a fixed threshold cannot adapt to and this corpus does not contain.


## 8. False-positive attribution

Every one of the 49 false positives from the 408-pair run was traced to a cause, with
positive evidence required for each attribution rather than a category label alone.
Script: `evaluation-corpus/attribute_fps.py`.

| Cause | Count | Share |
|---|---:|---:|
| Boilerplate coverage hole | 18 | 36.7% |
| Mixed-assignment batching | 15 | 30.6% |
| Language scaffolding floor | 16 | 32.7% |
| **Genuinely unexplained** | **0** | **0.0%** |

**(a) Boilerplate coverage hole — 18.** Both files share the provided skeleton, which was
never suppressed. In the 17-file batch the skeleton appears in 4 files; discard requires
≥80%, i.e. 14 of 17. Run as its own 4-file cohort it falls below `MINIMUM_COHORT_SIZE`
of 5 and the filter is inert. Structure shared by roughly 5–80% of a cohort falls between
the two guards and is never removed.

**(b) Mixed-assignment batching — 15.** Evidence is the isolated re-run, not the label:
each of these pairs is flagged in the mixed batch and **not** flagged when the trivial
cluster is run as its own cohort (0 of 6 in all three languages).

**(c) Everything else — 16, all of it the language scaffolding floor.** This was the
category expected to contain genuine mystery. It does not. All 16 are C (10) or Python
(6); **Java has none**. Their scores repeat almost exactly — 0.3143 six times, 0.1852 four
times — which is what a fixed shared structure looks like.

Dumping the shared k-grams through the public API confirmed it directly. The C pairs share
`#include < ID . ID`, `ID ( void ) {`, `ID ( STR , ID (` and `; return NUM ; }` — the
include block, `main(void)`, the `printf` call and `return 0`. The Python pairs share
`def ID ( ID ) :` and `NEWLINE INDENT` / `DEDENT` scaffolding.

That floor was then measured rather than assumed. Two files per language sharing only the
mandatory frame, with deliberately unrelated bodies (`evaluation-corpus/scaffolding-floor/`):

| Language | Scaffolding-only pair scores | Residual FP range |
|---|---:|---|
| C | **0.3846** | 0.3000 – 0.3143 |
| Python | **0.5152** | 0.1818 – 0.1852 |
| Java | no universal frame | none |

**Every residual false positive scores at or below the cost of merely writing valid code
in that language.** They contain no shared authored logic at all. Java shows none because
Java files have no universal frame — no include block, no `main` in every file, and the
class declaration carries the author's own chosen name.

### Precision under each attribution

| Scenario | FPs remaining | Precision |
|---|---:|---:|
| As measured | 49 | 0.279 |
| Excluding the boilerplate hole | 31 | 0.380 |
| Excluding mixed-assignment batching | 34 | 0.358 |
| **Excluding both causes asked about** | **16** | **0.543** |
| Excluding all three identified causes | 0 | 1.000 |

The answer to the question posed: **removing the two known, understood causes lifts
precision from 0.279 to 0.543.** The 1.000 in the last row is a bookkeeping statement, not
a performance claim — it says only that no unexplained failure mode remains, and it is
reached by excluding an irreducible floor that cannot be removed by tuning.

## 9. Does adaptive generalise where fixed does not?

The §7 comparison was unfair to the adaptive method: the fixed threshold was fitted to the
very corpus it was then evaluated on. The proper test is a threshold fixed once and
applied unchanged across assignments of different difficulty.

A second corpus was built: four assignments in Java (language held constant so difficulty
is the only variable), 12 files each, same internal spread (6 honest, 1 obvious copy,
2 disguised copies, 3 sharing a skeleton), **each run as its own batch** — respecting the
precondition the CLI does not enforce. 48 files, 264 pairs, 16 expected-positive.

`a1-constrained` was intended to be the case that breaks a fixed threshold. It was not
constrained enough — its honest pairs top out at 0.72, below any sensible bar. `a0-degenerate`
was added afterwards as the genuine extreme: an immutable two-field holder with getters,
equals and toString, where there is essentially no design freedom. Adding it after seeing
a1's result is a deliberate choice worth flagging; it maps where the methods diverge rather
than establishing that they do.

| Assignment | Design freedom | Honest median | Honest max | MAD | Tier used |
|---|---|---:|---:|---:|---|
| a0-degenerate | none | 1.0000 | 1.0000 | 0.0000 | 2 (MAE) |
| a1-constrained | little | 0.2051 | 0.7200 | 0.0718 | 1 (MAD) |
| a2-medium | real choices | 0.1222 | 0.5957 | 0.0635 | 1 (MAD) |
| a3-open-ended | unconstrained | 0.1000 | 0.1951 | 0.0414 | 1 (MAD) |

### Results, fixed threshold 0.8118 applied unchanged throughout

| Assignment | Method | TP | FP | FN | Precision | Recall | F1 |
|---|---|---:|---:|---:|---:|---:|---:|
| a0-degenerate | adaptive | 0 | 0 | 4 | n/a | 0.000 | 0.000 |
| | fixed | 4 | **32** | 0 | 0.111 | 1.000 | 0.200 |
| a1-constrained | adaptive | 4 | 6 | 0 | 0.400 | 1.000 | 0.571 |
| | fixed | 2 | 0 | 2 | 1.000 | 0.500 | **0.667** |
| a2-medium | adaptive | 4 | 4 | 0 | 0.500 | 1.000 | 0.667 |
| | fixed | 4 | 0 | 0 | 1.000 | 1.000 | **1.000** |
| a3-open-ended | adaptive | 4 | 3 | 0 | 0.571 | 1.000 | 0.727 |
| | fixed | 4 | 0 | 0 | 1.000 | 1.000 | **1.000** |
| **Pooled** | **adaptive** | 12 | 13 | 4 | 0.480 | 0.750 | **0.585** |
| | fixed | 14 | 32 | 2 | 0.304 | 0.875 | 0.452 |

### What this actually shows

**Pooled, adaptive wins (0.585 vs 0.452) — and the pooled number is misleading.** It is
driven almost entirely by a0, where the fixed threshold produces 32 false positives in a
single 66-pair batch. Weighting by pair count lets one pathological assignment decide the
comparison.

**Per assignment, the fixed threshold wins three of four**, including two perfect scores.
It generalised considerably better than the §7 result implied it would. On a1 it beat
adaptive despite *missing two copies* (recall 0.500), because adaptive's six false
positives cost it more.

**Both methods fail at a0, in opposite directions.** The fixed threshold flags 32 honest
pairs — the entire cohort — because honest work legitimately scores 1.000. The adaptive
method flags nothing at all, **including all four real copies**: with every score identical
there is no outlier to find. Recall 0.000.

That second failure is arguably the correct answer to an impossible question. When honest
work is byte-identical to copied work after normalisation, no similarity measure can
separate them. But "correct" and "useful" part company here: the tool reports a clean
cohort when four files really were copied, and says nothing to indicate the finding is
vacuous.

**Adaptive's real advantage is not accuracy, it is failure mode.** It never produced a
catastrophic false-positive count; it was also never the best method on any single
assignment. Fixed is better when the cohort is well-behaved and dangerous when it is not.

### Honest limits of this comparison

Four assignments, one language, one author, 16 positive pairs. `a0` was added after seeing
a1's result. A single fixed threshold was tested, not swept. This does not establish that
adaptive generalises better — it establishes that **fixed generalises better than expected
across a1–a3 and fails catastrophically at a0, while adaptive is mediocre everywhere and
catastrophic nowhere.** Which of those is preferable is a product decision about the cost
of a false accusation versus a missed copy, not something this data settles.

## 10. Open design question: the no-spread state

**Not changed. Recorded for a deliberate decision.**

When a cohort has no dispersion, the current code has two distinct behaviours depending on
a detail that has nothing to do with the question being asked:

| Cohort state | MAD | MAE | Tier | Current behaviour |
|---|---|---|---|---|
| Every pair scores *exactly* alike | 0 | 0 | 3 | Absolute guardrail: flags every pair at or above 0.90 |
| Near-uniform, a sub-group differs slightly | 0 | >0 | 2 | MAE fallback: typically flags nothing |

Both were observed. The isolated `degenerate/` clusters in Java and C hit tier 3 and
flagged **6 of 6** pairs. The `a0-degenerate` assignment, whose three boilerplate files
supply just enough spread to keep the MAE above zero, hit tier 2 and flagged **0 of 66** —
missing four genuine copies.

So on the same underlying situation — an assignment with no design freedom — the tool
either accuses everyone or clears everyone, decided by whether some unrelated sub-group
happens to differ. Neither answer is informative, and the report gives the reader no way
to tell that the analysis was vacuous.

**The question to decide:** should the no-spread state continue to resolve to a flag/no-flag
verdict at all, or should it report a third outcome — something like *"insufficient
statistical spread to distinguish honest uniformity from collusion; manual review
required"* — and be surfaced as such in the report rather than folded into the flag list?

Arguments on both sides, so that the decision is a real one:

- **Keep the guardrail.** A cohort where every pair is identical after normalisation does
  warrant a human look, whatever the cause. Silence is the more dangerous failure.
- **Replace it with an explicit no-verdict state.** A flag currently means "unusual for
  this cohort". In the no-spread state nothing is unusual, so the flag is not that claim —
  it is an absolute-similarity claim wearing the same label. That is the exact confusion
  the statistical design was built to remove, reintroduced at the one point where the
  statistics have nothing to say.
- **Whichever is chosen, tier 2 and tier 3 should not disagree.** The current split means
  an unrelated sub-group's presence flips the verdict from "flag all" to "flag none".

No behaviour has been changed. Retuning `BoilerplateFilter` or `StatisticalAnalyzer`
against these corpora would be fitting to a single test set — the precise failure the
fixed-threshold comparison exists to expose.
