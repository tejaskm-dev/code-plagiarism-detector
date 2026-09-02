# Sample cohort

`cs101-hw3/` is a mock assignment: ten students implementing the same `Gradebook`
class (mean, max, median, count-above) on top of a provided starter. It exists so the
tool can be exercised against data with **known ground truth**.

```sh
./bin/integrity analyze --dir examples/cs101-hw3 --out /tmp/report.json --assignment cs101-hw3
```

## Ground truth

| Student | What it is |
|---|---|
| `student01_alice` | honest — plain loops |
| `student02_ben` | honest — streams |
| `student03_chen` | honest — sorts a copy first |
| **`student04_dana`** | **copied from alice** — every name changed, reformatted, methods reordered, comments rewritten |
| `student05_evan` | honest — single pass, manual bookkeeping (plus a `notes.txt`, which is ignored) |
| `student06_farah` | honest — helper-method heavy |
| **`student07_gus`** | **copied from chen** — whitespace and comments only, the lazy disguise |
| `student08_hana` | honest — defensive, validates first |
| `student09_ivan` | honest — uniform naming and exhaustive javadoc, the "looks machine-written" profile |
| `student10_jia` | honest — terse |

All ten share the same provided starter (package, imports, fields, constructor,
`addScore`, `getCourseName`, `count`), so boilerplate suppression has real work to do.

## What the output should show

Both planted copies come out on top, far clear of everything else:

```
FLAGGED  1.0000  chen  vs gus      <- whitespace-only copy
FLAGGED  0.9815  alice vs dana     <- renamed + reformatted + reordered
FLAGGED  0.3590  alice vs farah
FLAGGED  0.3544  dana  vs farah
FLAGGED  0.3425  alice vs jia
FLAGGED  0.3378  dana  vs jia
         0.2727  farah vs jia
```

Two things are worth noticing, and both are the point of the exercise.

**The disguises did not work.** Renaming every identifier, reflowing the braces and
reordering the methods moved `alice`/`dana` from 1.00 to 0.98. That is what the
tokenizer normalisation is for.

**There is a tail of moderate flags, and they are not plagiarism.** `alice`, `farah`
and `jia` all wrote the obvious loop over a standard algorithm, so they converge more
than the cohort average — and against a median of 0.096 with a MAD of 0.043, a score of
0.36 really is several MADs out. The tool is answering *"unusual for this cohort"*,
which is not the same question as *"copied"*. The gap between the real copies (0.98+)
and the tail (0.36) is the signal a human should act on.

That tail is left in deliberately rather than tuned away. On a short, standard
assignment honest solutions genuinely converge, and a demo that hid that would be
misrepresenting how the tool behaves.
