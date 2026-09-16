# Code Plagiarism Detector (Academic Integrity Engine)

[![CI](https://github.com/tejaskm-dev/code-plagiarism-detector/actions/workflows/ci.yml/badge.svg)](https://github.com/tejaskm-dev/code-plagiarism-detector/actions/workflows/ci.yml)

> **A Java library for source-code plagiarism detection**: Tokenize → Winnowing Fingerprint → Statistical MAD Outlier Detection, with an offline AI-authorship model. It also ships a CLI and a web dashboard.

It finds submissions that are unusually similar *for this particular class*, rather than
applying a fixed percentage cut-off. Renaming variables, reformatting, and reordering
methods do not hide a copy.

- **Languages:** Java, Python, C, C++, JavaScript
- **Runtime dependencies:** none. The library jar is about 130 KB and runs on Java 17 or newer.
- **Everything runs offline.** A Claude second opinion on AI authorship is optional, using your own API key.

---

## 📚 Use it as a library

### Add the dependency

**Maven**
```xml
<dependency>
  <groupId>io.github.tejaskm-dev</groupId>
  <artifactId>integrity-engine-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

**Gradle**
```kotlin
implementation("io.github.tejaskm-dev:integrity-engine-core:1.0.0")
```

> These coordinates resolve once the first release is published to Maven Central. Until then, use
> JitPack as described in [docs/RELEASING.md](docs/RELEASING.md#jitpack), or build from source
> with `mvn install`.

### Analyse a class

```java
import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Report;
import com.integrityengine.domain.SimilarityResult;
import com.integrityengine.engine.IntegrityEngine;

IntegrityEngine engine = new IntegrityEngine();   // stores nothing, needs no database

Report report = engine.analyzeBatch(List.of(
        //                 submission id  student  assignment  filename          source
        new CodeSubmission("hw3/alice",   "alice", "hw3",      "Gradebook.java", aliceSource),
        new CodeSubmission("hw3/ben",     "ben",   "hw3",      "Gradebook.java", benSource),
        new CodeSubmission("hw3/chen",    "chen",  "hw3",      "Gradebook.java", chenSource)));

for (SimilarityResult pair : report.getFlaggedResults()) {
    System.out.printf("%s <-> %s  %.2f%n",
            pair.getLeftSubmissionId(), pair.getRightSubmissionId(), pair.getSimilarityScore());
}
```

The `Report` returned by `analyzeBatch` contains:

| Method | Contents |
| :--- | :--- |
| `getSimilarityResults()` | Every pair's score, from 0 to 1 |
| `getFlaggedResults()` | The pairs that are statistical outliers for this class |
| `getCohortMedian()`, `getCohortMad()` | The class-wide numbers each pair is judged against |
| `getAiResults()` | An AI-authorship estimate per file: `getAiLikelihood()`, `getRationale()` |

**Rules for a batch**
- All submissions must belong to the same assignment.
- Every submission id must be unique.
- The filename's extension selects the language. Files in unsupported languages are accepted but not compared.

Breaking the first two rules throws `IllegalArgumentException`.

### Options

**Ignore the starter code you gave students.** Code that matches these files is removed
from every submission before comparison:

```java
Report report = engine.analyzeBatch(batch, null, Map.of("Gradebook.java", starterSource));
```

**Add an LLM second opinion on AI authorship.** Pass your Anthropic key. It is used for that
call only and never stored:

```java
Report report = engine.analyzeBatch(batch, System.getenv("ANTHROPIC_API_KEY"));
```

**Keep submissions and results in SQLite.** Add the driver, which the library leaves
optional, and pass a `RepositoryFactory`:

```xml
<dependency>
  <groupId>org.xerial</groupId>
  <artifactId>sqlite-jdbc</artifactId>
  <version>3.46.1.3</version>
</dependency>
```

```java
IntegrityEngine engine = new IntegrityEngine(new RepositoryFactory(Path.of("integrity.db")));
```

**Replace a stage.** The full constructor accepts your own comparator, tokenizers,
repositories or LLM judge. The javadoc on `IntegrityEngine` describes each stage.

---

## 🛠️ Command-line tool

The same engine is available without writing any Java. Download `integrity-engine-<version>.jar` from
[Releases](https://github.com/tejaskm-dev/code-plagiarism-detector/releases):

```bash
java -jar integrity-engine-<version>.jar analyze --dir submissions/ --out report.json
```

`--dir` expects one folder per student. Writing `--out` to a `.txt` or `.md` file gives a
readable report instead of JSON. In a clone of this repository, `./bin/integrity` runs the
same commands.

| Flag | Argument | Description | Default |
| :--- | :--- | :--- | :--- |
| `--dir` | `<path>` | Directory containing student submissions (**required**; searched recursively, one folder per student). | — |
| `--out` | `<path>` | Output file path (`.txt`/`.md` for the readable report, anything else for JSON). **Required.** | — |
| `--assignment` | `<id>` | Assignment identifier recorded in the database. | Directory name |
| `--db` | `<file>` | SQLite database the results are recorded in. | `integrity.db` next to `--out` |
| `--llm-api-key` | `<key>` | Anthropic API key for an LLM second opinion on authorship. Prefer the `ANTHROPIC_API_KEY` environment variable: a key typed on the command line is visible to other processes. | Optional (BYOK) |
| `--quiet` | — | Print only the summary line, not the full report. | Off |

Other commands: `serve [--port <n>] [--db <file>]` starts the web dashboard, and `--version` prints the version.

---

## 🧠 How It Works

1. **Robust Multi-Language Tokenization**:
   - Supports **Java**, **Python**, **C**, **C++** and **JavaScript**.
   - Normalizes variable identifiers, literals, and comments to defeat trivial obfuscation (e.g. variable renaming, reformatting, comment modifications).
2. **Winnowing Fingerprinting Algorithm**:
   - Generates position-independent $k$-gram token hashes.
   - Guarantees that any shared run of tokens above the noise threshold $t$ is detected, using window size $w$.
3. **Boilerplate Suppression**:
   - In classes of five or more, fingerprints that appear in at least $80\%$ of submissions are treated as instructor scaffolding and removed from comparisons.
   - Starter files supplied as reference files are removed from comparisons at any class size.
4. **Statistical Outlier Detection (MAD & Modified Z-Score)**:
   - Evaluates similarity against the cohort's **Median Absolute Deviation (MAD)** instead of an arbitrary static threshold (e.g., static 70%).
   - Accurately distinguishes natural convergence on short assignments from genuine collusion.
5. **AI Authorship & Stylometry (BYOK)**:
   - Includes an offline **stylometric random forest** model detecting synthetic patterns (naming uniformity, cyclomatic complexity distributions).
   - Supports optional BYOK LLM evaluation via `export ANTHROPIC_API_KEY=sk-ant-...`.

For measured accuracy on a hand-labelled corpus, see [docs/evaluation-results.md](docs/evaluation-results.md) and
[docs/ai-detection-results.md](docs/ai-detection-results.md).

---

## ⚡ Project Demo (Web Dashboard)

The web dashboard presents the library's results visually for the project showcase. It is not
the main way the library is meant to be used.

### Step 1: Install Prerequisites (if not already installed)

The project requires **Java 17+** and **Maven 3.8+**. Run the command matching your lab machine:

* **Ubuntu / Debian / Linux Mint:**
  ```bash
  sudo apt update && sudo apt install -y openjdk-17-jdk maven
  ```
* **macOS:**
  ```bash
  brew install openjdk@17 maven
  ```
* **Fedora / RHEL:**
  ```bash
  sudo dnf install -y java-17-openjdk-devel maven
  ```

Verify the installation:
```bash
java -version    # Must report Java 17 or higher
mvn -version
```

> **macOS note:** `java -version` may still report Java 8, or no Java at all, after installing
> with Homebrew. Homebrew does not put its JDK on the `PATH`. The `bin/` launchers find
> it anyway. For `mvn`, set `JAVA_HOME`, for example
> `export JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home`. If Maven
> picks up an older Java, the build fails straight away with a message saying so.

### Step 2: Clone and Build

```bash
git clone https://github.com/tejaskm-dev/code-plagiarism-detector.git
cd code-plagiarism-detector
mvn clean package -DskipTests
```

### Step 3: Run the Showcase

#### Option A: Interactive Web UI (Recommended for Live Demo)

```bash
./bin/integrity-server --db demo.db
```

Using a fresh `--db` keeps old test runs off the start screen. The dashboard loads nothing
from the internet, so it also works without Wi-Fi.

1. Open your browser and navigate to: **[http://localhost:7070](http://localhost:7070)**
2. **Demo Flow in the Browser:**
   - Enter an assignment name (e.g., `CS101-HW3`) and click **Create**.
   - Click **Browse Files** and select the provided sample archive, `examples/cs101-hw3.zip`.
     It holds 10 Java submissions: two planted copies, shared starter code, and honest solutions.
   - Click **Analyse batch**.
   - **Showcase Features to Display:**
     - **Overview**: a histogram of every pair's similarity score, with lines marking the
       class median and the score a pair must reach to be flagged.
     - **Pairs**: every comparison, ranked, with its modified z-score and how much of one file
       appears in the other.
     - **Compare**: open a flagged pair (e.g., `student01_alice` vs. `student04_dana`) to see
       both files side by side, with the matching lines highlighted. It also shows which methods
       match and which identifiers were renamed.
     - **AI authorship**: the offline stylometric model's score for each file, with an
       "unsure" band so a borderline score is not presented as a verdict.
     - **Boilerplate reference** (optional): upload the starter files you gave students.
       Matches on that code then stop counting towards similarity.

*Press `Ctrl + C` in the terminal when finished to stop the server.*

The dashboard runs on a REST API, documented in [docs/API.md](docs/API.md). The server has no
authentication and is meant to run on your own machine.

#### Option B: Terminal CLI Analysis

```bash
./bin/integrity analyze --dir examples/cs101-hw3 --out report.txt --assignment cs101-hw3
```

This analyses the 10 submissions, compares all 45 pairs, and prints the report (abridged here):

```text
==========================================================================
  ACADEMIC INTEGRITY REPORT
  Assignment : cs101-hw3
==========================================================================

COHORT OVERVIEW
--------------------------------------------------------------------------
  Submissions compared   10
  Pairs examined         45
  Metric                 jaccard
  Median similarity      0.0962
  Spread (MAD)           0.0430
  Spread (MAE)           0.0982

  Method   modified z-score against the median and MAD
  In this cohort a typical pair shares about 10% of its structure,
  so a pair has to reach roughly 0.32 before it counts as unusual.

FLAGGED PAIRS (6 of 45)
--------------------------------------------------------------------------
  #1   [####################]  student03_chen  <->  student07_gus
       similarity   1.0000   (cohort median 0.0962)
       deviation    14.2 deviations from the median - past the 3.5 review threshold
       reading      Effectively the same program once naming and layout are
                    stripped out. Very little of this can happen by accident.

  #2   [####################]  student01_alice  <->  student04_dana
       similarity   0.9815   (cohort median 0.0962)
       deviation    13.9 deviations from the median - past the 3.5 review threshold
...
AI-AUTHORSHIP SIGNALS
--------------------------------------------------------------------------
  [####################]  0.977  cs101-hw3/student06_farah/Gradebook.java
       stylometry 0.977  via stylometric-forest-v1
...
```

[`examples/README.md`](examples/README.md) explains the expected result for each student,
including why a few honest pairs are also flagged.

---

## 🧪 Tests and Benchmark

```bash
mvn verify    # unit and integration tests, plus the library's javadoc and sources jars
```

The benchmark scores the engine against a hand-labelled corpus in Python, Java and C. It
reads the engine's output from `evaluation-results/`, so generate that first:

```bash
mkdir -p evaluation-results
for lang in java python c; do
  ./bin/integrity analyze --quiet --dir evaluation-corpus/$lang \
    --out evaluation-results/results-$lang.json --assignment eval-$lang --db evaluation-results/eval.db
  ./bin/integrity analyze --quiet --dir evaluation-corpus/$lang/trivial-assignment \
    --out evaluation-results/trivial-$lang.json --assignment trivial-$lang --db evaluation-results/trivial.db
done
python3 evaluation-corpus/score.py
```

---

## 🗂️ Repository Layout

| Path | Contents |
| :--- | :--- |
| `core/` | **The library** (`integrity-engine-core`): `tokenizer`, `fingerprint`, `similarity`, `statistics`, `ai`, `persistence`, all orchestrated by `engine.IntegrityEngine` |
| `app/` | The CLI (`cli`), REST API (`api`) and web dashboard, packaged as one runnable jar. Not published as a library |
| `bin/` | Launcher scripts |
| `examples/` | Sample CS101 class with known ground truth |
| `evaluation-corpus/` | Labelled benchmark corpus and Python scorers |
| `docs/` | REST API reference, release guide, evaluation reports, UI mockups |

To release, set the version in `pom.xml`, then push a matching tag (`v1.0.0`). For details, see
[docs/RELEASING.md](docs/RELEASING.md).

---

## 📄 License

[MIT](LICENSE) © 2026 Tejas KM, Sufiyan Shiraj, Sumedha K S, Sreya K Nair.

The web dashboard bundles Chart.js (MIT) and the Hanken Grotesk, Manrope and JetBrains Mono
fonts. The fonts are under the SIL Open Font License 1.1, and their license files are kept
next to them in `app/src/main/resources/public/fonts/`.

---

## 👥 Contributors

* **Tejas KM** ([@tejaskm-dev](https://github.com/tejaskm-dev)) — Architecture, Tokenizers, Winnowing Fingerprinting & Persistence
* **Sufiyan Shiraj** ([@Sufiyan-Shiraj](https://github.com/Sufiyan-Shiraj)) — AI Authorship Detection, REST API & CLI Tooling
* **Sumedha K S** ([@sumedhaks](https://github.com/sumedhaks8075)) — Interactive Web Dashboard & Evaluation Test Cases
* **Sreya K Nair** ([@sreyaknair](https://github.com/sreyaknair08)) — Evaluation Corpus Datasets & UI Report Mockups
