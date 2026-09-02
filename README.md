# Code Plagiarism Detector (Academic Integrity Engine)

> **Source-code plagiarism detection and AI-authorship analysis pipeline**: Tokenize → Winnowing Fingerprint → Statistical MAD Outlier Detection → Interactive Web Dashboard & Terminal Reports.

---

## ⚡ Quickstart for Lab Showcase (Paste & Run)

Follow these exact commands to set up, build, and demonstrate the project on any lab computer.

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

---

### Step 2: Clone and Build

```bash
# 1. Clone the repository
git clone https://github.com/tejaskm-dev/code-plagiarism-detector.git
cd code-plagiarism-detector

# 2. Build the executable JAR (skipping tests for speed)
mvn clean package -DskipTests

# 3. Ensure launcher scripts have execute permissions
chmod +x bin/integrity bin/integrity-server
```

---

### Step 3: Run the Showcase

You can showcase the project in two ways: via the **Interactive Web Dashboard** (recommended for visual presentations) or the **Terminal CLI**.

#### Option A: Interactive Web UI (Recommended for Live Demo)

Starts the embedded Javalin server serving the modern web UI and REST API on port `7070`:

```bash
./bin/integrity-server
```

1. Open your browser and navigate to: **[http://localhost:7070](http://localhost:7070)**
2. **Demo Flow in the Browser:**
   - Click **Create Assignment** (e.g., enter `CS101-HW3`).
   - Click **Upload Submissions** and select the provided sample archive: `examples/cs101-hw3.zip` (contains 10 student Java submissions with disguised copies, boilerplate, and independent honest code).
   - Click **Run Analysis**.
   - **Showcase Features to Display:**
     - **Similarity Matrix & Heatmap**: Visual clustering of high-similarity pairs.
     - **Statistical Distribution**: Cohort median vs. Median Absolute Deviation (MAD) threshold.
     - **Side-by-Side Code Diff**: Click any flagged pair (e.g., `student01_alice` vs. `student04_dana`) to view matched token fingerprints with synchronized scrolling and syntax highlighting.
     - **AI Authorship Radar**: Stylometric feature extraction radar scores for AI-generated code detection.

*Press `Ctrl + C` in the terminal when finished to stop the server.*

---

#### Option B: Terminal CLI Analysis

Run the batch analyzer directly from the command line on sample student submissions:

```bash
./bin/integrity analyze --dir examples/cs101-hw3 --out report.txt --assignment cs101-hw3
```

This immediately analyzes the 10 student submissions, compares all 45 submission pairs, and outputs a formatted terminal report:

```text
==========================================================================
ACADEMIC INTEGRITY ENGINE - PLAGIARISM & SIMILARITY REPORT
==========================================================================
Assignment : cs101-hw3
Submissions: 10
Pairs      : 45 compared
Cohort     : median 0.0962, MAD 0.0384 (flag threshold: z >= 3.50)

FLAGGED PAIRS (STATISTICAL OUTLIERS)
--------------------------------------------------------------------------
  #1   [####################]  student03_chen  <->  student07_gus
       similarity   1.0000   (cohort median 0.0962)
       deviation    14.2 deviations from the median - past the 3.5 review threshold
       files        cs101-hw3/student03_chen/Gradebook.java
                    cs101-hw3/student07_gus/Gradebook.java
       reading      Effectively the same program once naming and layout are
                    stripped out. Very little of this can happen by accident.

  #2   [####################]  student01_alice  <->  student04_dana
       similarity   0.9815   (cohort median 0.0962)
       deviation    13.9 deviations from the median - past the 3.5 review threshold
       files        cs101-hw3/student01_alice/Gradebook.java
                    cs101-hw3/student04_dana/Gradebook.java
       reading      Effectively the same program once naming and layout are
                    stripped out. Very little of this can happen by accident.
...
AI-AUTHORSHIP SIGNALS
--------------------------------------------------------------------------
  [####################]  0.977  cs101-hw3/student06_farah/Gradebook.java
       stylometry 0.977  via stylometric-forest-v1
```

> **Tip**: Change `--out report.txt` to `--out report.json` to generate machine-readable JSON for integration into grading pipelines.

---

### Step 4: Run the Benchmark Test Suite (Optional)

To prove reliability and test coverage during the demonstration:

```bash
# Run unit & integration test suite (200+ tests across tokenizers, winnowing, and API)
mvn test
```

To run the automated evaluation scoring benchmark across multi-language datasets (Python, Java, C):

```bash
python3 evaluation-corpus/score.py
```

---

## 🛠️ CLI Options Reference

```bash
./bin/integrity analyze --dir <submissions_dir> --out <output_path> [options]
```

| Flag | Argument | Description | Default |
| :--- | :--- | :--- | :--- |
| `--dir` | `<path>` | Directory containing student submissions (**required**; expected 1 folder per student). | — |
| `--out` | `<path>` | Output file path (`.txt`/`.md` for terminal display, `.json` for raw output). | — |
| `--assignment` | `<id>` | Unique assignment identifier for tracking in the database. | Directory name |
| `--db` | `<file>` | SQLite database file used to record historical cohort statistics. | `integrity.db` |
| `--llm-api-key` | `<key>` | Anthropic API key to enable LLM-assisted forensic review. | Optional (BYOK) |
| `--quiet` | — | Suppress pair-by-pair breakdown and print summary metrics only. | Off |

---

## 🧠 Key Technical Highlights

1. **Robust Multi-Language Tokenization**:
   - Supports **Java**, **Python**, **C**, and **C++**.
   - Normalizes variable identifiers, literals, and comments to defeat trivial obfuscation (e.g. variable renaming, reformatting, comment modifications).
2. **Winnowing Fingerprinting Algorithm**:
   - Generates position-independent $k$-gram token hashes.
   - Guaranteed minimum match guarantee with noise threshold $t$ and window size $w$.
3. **Adaptive Boilerplate Suppression**:
   - Fingerprints appearing across $\ge 80\%$ of the student cohort are automatically classified as instructor scaffolding/starter code and removed from comparisons.
4. **Statistical Outlier Detection (MAD & Modified Z-Score)**:
   - Evaluates similarity against the cohort's **Median Absolute Deviation (MAD)** instead of an arbitrary static threshold (e.g., static 70%).
   - Accurately distinguishes natural convergence on short assignments from genuine collusion.
5. **AI Authorship & Stylometry (BYOK)**:
   - Includes an offline **stylometric random forest** model detecting synthetic patterns (naming uniformity, cyclomatic complexity distributions).
   - Supports optional BYOK LLM evaluation via `export ANTHROPIC_API_KEY=sk-ant-...`.

---

## 👥 Contributors

* **Tejas KM** ([@tejaskm-dev](https://github.com/tejaskm-dev)) — Architecture, Tokenizers, Winnowing Fingerprinting & Persistence
* **Sufiyan Shiraj** ([@Sufiyan-Shiraj](https://github.com/Sufiyan-Shiraj)) — AI Authorship Detection, REST API & CLI Tooling
* **Sumedha K S** ([@sumedhaks](https://github.com/sumedhaks8075)) — Interactive Web Dashboard & Evaluation Test Cases
* **Sreya K Nair** ([@sreyaknair](https://github.com/sreyaknair08)) — Evaluation Corpus Datasets & UI Report Mockups
