# academic-integrity-engine

Source-code plagiarism detection: tokenize → winnow → compare → flag statistical outliers,
with optional BYOK AI-authorship analysis.

## Requirements

Java 17 or newer, and Maven.

On macOS `java` on the `PATH` is often `/usr/bin/java`, a stub that reports
"Unable to locate a Java Runtime" even when a JDK is installed. The `bin/integrity`
launcher works around this by locating and version-checking a real JDK itself, so you
usually do not need to configure anything. If you have no JDK at all:

```sh
brew install openjdk
```

## Build

```sh
mvn package -DskipTests     # produces target/integrity-engine.jar
mvn test                    # run the full suite
```

## Run

```sh
./bin/integrity analyze --dir <submissions> --out <report.json> [options]
```

| Option | Meaning |
|---|---|
| `--dir <path>` | directory of submissions, searched recursively (required) |
| `--out <file>` | where to write the JSON report (required) |
| `--db <file>` | SQLite database (default: `integrity.db` beside `--out`) |
| `--assignment <id>` | assignment identifier (default: the directory name) |
| `--llm-api-key <key>` | enable AI-authorship analysis |

Run with no arguments for help. Exit codes: `0` success, `1` runtime error, `2` usage error.

### Example

```sh
./bin/integrity analyze --dir ./submissions --out ./report.json --assignment cs101-hw3
```

```
Analysed 8 submissions for assignment cs101-hw3
  28 pairs compared, cohort median 0.1010, MAD 0.0693
  7 pairs flagged as anomalous for this cohort
28 new results recorded for assignment cs101-hw3 (28 total in ./integrity.db)
Report written to ./report.json
```

Submissions are expected one directory per student; the first path segment becomes the
student id. Files whose extension no tokenizer recognises are stored but not compared.

### AI-authorship analysis (optional)

Bring your own key. Prefer the environment variable — a key passed on the command line
is visible to any other process on the machine through the process list:

```sh
export ANTHROPIC_API_KEY=sk-ant-...
./bin/integrity analyze --dir ./submissions --out ./report.json
```

With no key the module is skipped entirely. If the key is present but the call fails,
the analysis degrades to local stylometry and the report records the model as
`heuristic-only` rather than failing the run.

## Things worth knowing

- **Results are append-only.** Re-analysing an assignment adds rows rather than
  replacing them, which is why the summary reports new-versus-total counts.
- **A flag means "unusual for this cohort", not "plagiarised."** On a tight, low-variance
  cohort a modest similarity can cross the threshold. The output is a shortlist for human
  review.
- **Small submissions are noisy.** With very short files most pairs score zero, the MAD
  collapses, and incidental overlap looks anomalous.
- **Boilerplate suppression has a floor.** Fingerprints appearing in ≥80% of the cohort
  are discarded as boilerplate. A colluding group larger than that would suppress its own
  evidence.
# code-plagiarism-detector
