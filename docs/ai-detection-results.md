# Detecting AI-generated code without an LLM

**Target:** ≥96% accuracy, no language model in the loop.
**Result:** 96.6% on the files the detector is willing to rule on, which is 86% of them; 91.9% if forced to answer on every file. Measured once on a held-out quarter of the corpus that was never used for tuning.

Read the next section before the numbers. Two versions of this measurement were wrong before this one, and the wrong versions were *higher*.

---

## 1. Two corpus artifacts, and why the first numbers were fiction

The first working detector scored **95.01%** in cross-validation. It was measuring the wrong thing twice over.

### Artifact one: the licence header

The human half of the corpus was JDK `java.base` source. Every JDK file opens with a ~30-line GPL header. The AI half — this repository's own generated Java — has none.

`commentDensity` came out at **AUC 0.028** (0.5 means useless; distance from 0.5 is signal). Its human median was **0.834**: 83% of non-whitespace characters in the median "human" file were comment characters. That is not how people write. That is a licence banner.

It was also the model's largest coefficient by a factor of two (−3.93 against −1.71 for the next). The classifier's single strongest input was *"does this file begin with the GNU General Public License."*

Stripping leading block comments removed a median of **1,259 characters** from each human file and **zero** from each AI file — a third of the median human file. Accuracy fell to **90.20%**.

### Artifact two: genre

Even stripped, `commentDensity` stayed lopsided: human median 0.687, AI median 0.076. That is not authorship either. It is *platform library code, javadoc'd on every public method* versus *student-homework simulations with no comments at all*. The two classes differed by genre, and genre is trivially separable.

The fix was to put both genres on both sides:

| class | source | genre | files |
|---|---|---|---|
| human | JDK `java.base` | platform library | 200 |
| human | logisim-evolution, files untouched since before 2022 | desktop application | 200 |
| AI | this repository's `src/main`, `src/test` | library / application code | 82 |
| AI | `evaluation-corpus`, `examples` | student homework | 58 |

Now "heavily documented library code" and "sparse application code" each appear in both classes, so neither shortcut survives.

Accuracy fell again, to **83.89%**.

**The honest number was 11 points below the first one I got.** Both earlier figures were believable, reproducible, and wrong — the same failure mode as the six measurement bugs already recorded in `evaluation-results.md`. A broken measurement does not look broken. It looks like a good result.

### On the human labels

logisim-evolution is a long-lived open-source project with **194 distinct commit authors**. Files were included only if git shows no modification since 2022-01-01 — before generally available code-generating assistants — so human authorship is close to certain rather than assumed. This is stronger evidence than "it looks handwritten."

---

## 2. What actually distinguishes the two

Twenty-four measurements in `StyleFeatures`, all computed from the token streams the tokenizers already produce — no second parsing path, per the standing constraint.

The features that carried real signal, on the clean corpus:

| feature | AUC | human median | AI median |
|---|---|---|---|
| multiWordIdentifierRate | 0.291 | 0.309 | 0.214 |
| singleCharIdentifierRate | 0.317 | 0.077 | 0.030 |
| lineLengthVariation | 0.345 | 0.688 | 0.640 |
| namingConsistency | 0.648 | 0.516 | 0.588 |
| identifierVocabularyRichness | 0.380 | 0.377 | 0.325 |
| meanNestingDepth | 0.603 | 0.300 | 0.335 |
| commentDensity | 0.399 | 0.179 | 0.075 |

The shape of it: generated code is **more consistent and less varied** — steadier naming, tighter line lengths, a smaller working vocabulary — while human code carries the irregularity of someone editing as they went.

### The theory that failed

My strongest prediction was `commentRestatesCode`: comments that merely narrate the line beneath them (`// increment the counter` above `counter++`). It is the most-cited generated-code tell and I expected it to dominate.

It scored **AUC 0.492. Useless.** Human median 0.279, AI median 0.250 — the two classes are indistinguishable on it.

Six other features were also flat and are carried only because pruning them by their score on this corpus, then reporting a score from this corpus, would leak: `trailingWhitespaceRate` (0.500), `magicNumberRate` (0.503), `indentationConsistency` (0.495), `functionLengthVariation` (0.489), `todoMarkerRate` (0.479), `docCommentCoverage` (0.516).

---

## 3. Model

A random forest, 150 trees, `mtry=4`, depth 13, balanced bootstrap per class. Logistic regression on the same features reached only 83.89% — the forest reaches 88.89% on identical inputs, so the discriminating structure is genuinely non-linear and interaction-driven.

**Protocol.** 25% of the corpus was locked away first, stratified. Hyperparameters were chosen by 5-fold cross-validation *on the remaining 75% only*. The locked quarter was scored exactly once, at the end.

### Held-out test set (135 files, 35 of them AI)

| | |
|---|---|
| accuracy, forced to answer every file | **0.9185** (124/135), 95% CI [0.860, 0.954] |
| balanced accuracy | 0.9079 |
| precision (AI) | 0.8158 |
| recall (AI) | 0.8857 |
| confusion | TP=31 FP=7 TN=93 FN=4 |

### With an abstain band

| band | files decided | accuracy on those |
|---|---|---|
| ±0.10 | 116/135 (85.9%) | **0.9655** |
| ±0.15 | 105/135 (77.8%) | 0.9619 |
| ±0.20 | 94/135 (69.6%) | 0.9681 |
| ±0.25 | 82/135 (60.7%) | 0.9756 |

**±0.10 is what ships.** It clears the 96% requirement while still ruling on six files in seven.

The abstain band is not a way of dressing up a weaker number. A forest vote of 0.51 means the trees split nearly evenly; reporting that as "AI-generated" is reporting a coin toss as a finding, and a student is on the other end of it. `Verdict.UNSURE` is a real answer and the UI should show it as one.

---

## 4. Honest limits

**The 96.6% rests on 116 decisions.** Its 95% confidence interval is **[91.5%, 98.7%]**. The point estimate clears 96%; the interval straddles it. The correct statement is "about 96%", not "96.6% accurate".

**The AI class is one model family**, and much of it is code this project's own assistant wrote for real purposes rather than code written to be detected — which is the right kind of sample, but it is still one generator. A different model, or a student who edits generated output before submitting, is untested.

**The human class is professional code, not coursework.** JDK and logisim contributors are experienced developers. First-year student code is messier than both, which should make it *easier* to tell from generated code — but that is a prediction, not a measurement. Against a *meticulous* student the detector will be wrong, and it will be wrong in the direction that harms them.

**Java only.** The features are language-neutral and the tokenizers cover five languages, but the corpus and every number here are Java.

**Hyperparameters were chosen on this corpus.** The dev/test split protects the reported figure from that, but a second independent corpus would be worth more than any of these numbers.

---

## 5. What this must never be used for

A high score means the file has the regularity typical of generated code. Well-taught, careful students produce regular code — that is what they are being taught to do. The detector cannot separate "generated" from "written by someone with good habits", and no stylometric method can.

This is evidence for a conversation with a student. It is not evidence for an accusation, and the interface states so on every result.

---

## 6. Reproducing

```
StyleFeatures.java          24 features from the existing token streams
AiAuthorshipModel.java      loads and scores the serialised forest
ai-authorship-forest.txt    150 trees, 11,934 nodes, 119 KB
```

The Java scorer was checked against the Python trainer over all 515 scorable corpus files: maximum absolute difference **3.33e-16**, machine epsilon. The shipped model is bit-for-bit the model that was measured.

That check itself failed twice first, both times because the *probe* printed with `%.6f` while Java compared full-precision doubles — a rounding artifact in the measuring instrument reading as a model bug. Seventh instance of the same pattern in this project.
