package com.integrityengine.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeCommandTest {

    @TempDir
    Path workspace;

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;
    private AnalyzeCommand command;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        command = new AnalyzeCommand(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private Path submissions() throws Exception {
        Path root = workspace.resolve("submissions");
        write(root.resolve("student1/Solution.java"), GRADEBOOK);
        write(root.resolve("student2/Solution.java"), GRADEBOOK_RENAMED);
        write(root.resolve("student3/Solution.java"),
                "public class Tree { int k; Tree l; Tree r; void add(int x){ if(x<k){ if(l==null) l=new Tree(); l.add(x);} } }");
        write(root.resolve("student4/Solution.py"),
                "def total(values):\n    result = 0\n    for value in values:\n        result += value\n    return result\n");
        return root;
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ argument parsing

    @Test
    void parsesTheFullOptionSet() {
        Map<String, String> options = AnalyzeCommand.parse(new String[] {
                "analyze", "--dir", "/tmp/in", "--out", "/tmp/report.json",
                "--db", "/tmp/x.db", "--assignment", "hw3", "--llm-api-key", "sk-ant-x"});

        assertEquals("/tmp/in", options.get("dir"));
        assertEquals("/tmp/report.json", options.get("out"));
        assertEquals("hw3", options.get("assignment"));
        assertEquals("sk-ant-x", options.get("llm-api-key"));
    }

    @Test
    void rejectsUnknownOptionsAndMissingValues() {
        assertThrows(IllegalArgumentException.class,
                () -> AnalyzeCommand.parse(new String[] {"--nope", "x"}));
        assertThrows(IllegalArgumentException.class,
                () -> AnalyzeCommand.parse(new String[] {"--dir"}));
        assertThrows(IllegalArgumentException.class,
                () -> AnalyzeCommand.parse(new String[] {"stray"}));
    }

    @Test
    @DisplayName("A bare invocation prints help and succeeds")
    void noArgumentsPrintsHelp() {
        assertEquals(AnalyzeCommand.EXIT_OK, command.run(new String[] {}));
        assertTrue(stdout().contains("usage:"), stdout());
        assertEquals("", stderr(), "help is not an error, so nothing belongs on stderr");
    }

    @Test
    void missingRequiredOptionsExitWithAUsageCode() {
        assertEquals(AnalyzeCommand.EXIT_USAGE, command.run(new String[] {"analyze"}));
        assertTrue(stderr().contains("--dir and --out"), stderr());
        assertTrue(stderr().contains("usage:"), stderr());
    }

    @Test
    void helpIsPrintedOnRequest() {
        assertEquals(AnalyzeCommand.EXIT_OK, command.run(new String[] {"--help"}));
        assertTrue(stdout().contains("usage:"));
        assertTrue(stdout().contains("--llm-api-key"));
    }

    @Test
    void aMissingDirectoryIsAUsageError() {
        assertEquals(AnalyzeCommand.EXIT_USAGE, command.run(new String[] {
                "analyze", "--dir", workspace.resolve("nope").toString(),
                "--out", workspace.resolve("r.json").toString()}));
        assertTrue(stderr().contains("not a directory"), stderr());
    }

    @Test
    void aDirectoryWithNoSourceFilesIsAnError() throws Exception {
        Path empty = workspace.resolve("empty");
        write(empty.resolve("notes.txt"), "nothing to see");

        assertEquals(AnalyzeCommand.EXIT_RUNTIME_ERROR, command.run(new String[] {
                "analyze", "--dir", empty.toString(), "--out", workspace.resolve("r.json").toString()}));
        assertTrue(stderr().contains("recognised source extension"), stderr());
    }

    // --------------------------------------------------------------- the real run

    @Test
    @DisplayName("A full run writes a report and summarises what happened")
    void endToEndRunProducesAReport() throws Exception {
        Path report = workspace.resolve("out/report.json");

        int status = command.run(new String[] {
                "analyze", "--dir", submissions().toString(),
                "--out", report.toString(), "--assignment", "hw3"});

        assertEquals(AnalyzeCommand.EXIT_OK, status, stderr());
        assertTrue(Files.exists(report), "the report file should have been created");

        String json = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"assignmentId\": \"hw3\""), json);
        assertTrue(json.contains("\"cohortMedian\""), json);
        assertTrue(json.contains("\"flagged\""), json);
        assertTrue(json.contains("\"allPairs\""), json);

        assertTrue(stdout().contains("Analysed 4 submissions for assignment hw3"), stdout());
        assertTrue(stdout().contains("6 pairs compared"), stdout());
    }

    @Test
    @DisplayName("Repeated runs report new-versus-total, so append-only accumulation is visible")
    void repeatedRunsMakeAccumulationVisible() throws Exception {
        Path root = submissions();
        Path report = workspace.resolve("report.json");
        String[] args = {"analyze", "--dir", root.toString(), "--out", report.toString(),
                "--assignment", "hw3", "--db", workspace.resolve("runs.db").toString()};

        command.run(args);
        assertTrue(stdout().contains("6 new results recorded for assignment hw3 (6 total"), stdout());

        setUp();
        command.run(args);
        // Results are append-only by design; without this line a second run would look
        // identical to the first while quietly doubling the table.
        assertTrue(stdout().contains("6 new results recorded for assignment hw3 (12 total"), stdout());
    }

    @Test
    void mixedLanguageDirectoriesAreHandled() throws Exception {
        int status = command.run(new String[] {
                "analyze", "--dir", submissions().toString(),
                "--out", workspace.resolve("r.json").toString()});

        assertEquals(AnalyzeCommand.EXIT_OK, status, stderr());
        // Three Java files and one Python file all count as submissions.
        assertTrue(stdout().contains("Analysed 4 submissions"), stdout());
    }

    @Test
    @DisplayName("The assignment id defaults to the directory name")
    void assignmentDefaultsToTheDirectoryName() throws Exception {
        command.run(new String[] {"analyze", "--dir", submissions().toString(),
                "--out", workspace.resolve("r.json").toString()});

        assertTrue(stdout().contains("assignment submissions"), stdout());
    }

    @Test
    @DisplayName("Two assignments sharing one database do not clobber each other")
    void differentAssignmentsInOneDatabaseStaySeparate() throws Exception {
        // Both directories contain a "student1/Solution.java". With submission ids taken
        // from the relative path alone, the second run's upsert reassigned the first
        // run's submissions to its own assignment and pulled their results across the
        // join with them.
        Path first = workspace.resolve("hw1");
        write(first.resolve("student1/Solution.java"), GRADEBOOK);
        write(first.resolve("student2/Solution.java"), GRADEBOOK_RENAMED);

        Path second = workspace.resolve("hw2");
        write(second.resolve("student1/Solution.java"), GRADEBOOK);
        write(second.resolve("student2/Solution.java"), GRADEBOOK_RENAMED);

        Path shared = workspace.resolve("shared.db");
        command.run(new String[] {"analyze", "--dir", first.toString(),
                "--out", workspace.resolve("a.json").toString(),
                "--assignment", "hw1", "--db", shared.toString()});
        setUp();
        command.run(new String[] {"analyze", "--dir", second.toString(),
                "--out", workspace.resolve("b.json").toString(),
                "--assignment", "hw2", "--db", shared.toString()});

        assertTrue(stdout().contains("1 new results recorded for assignment hw2 (1 total"),
                "hw2 must see only its own result, got: " + stdout());

        var repositories = new com.integrityengine.persistence.RepositoryFactory(shared);
        assertEquals(2, repositories.submissions().findByAssignment("hw1").size(),
                "hw1's submissions must survive the second run");
        assertEquals(2, repositories.submissions().findByAssignment("hw2").size());
        assertEquals(1, repositories.results().findByAssignment("hw1").size(),
                "hw1's result must not migrate to hw2");
    }

    // -------------------------------------------------------------------- BYOK

    @Test
    @DisplayName("An API key passed on the command line is never echoed to stdout or stderr")
    void theApiKeyIsNeverPrinted() throws Exception {
        String key = "sk-ant-super-secret-value-12345";

        command.run(new String[] {
                "analyze", "--dir", submissions().toString(),
                "--out", workspace.resolve("r.json").toString(),
                "--assignment", "hw3", "--llm-api-key", key});

        assertFalse(stdout().contains(key), "the key leaked to stdout");
        assertFalse(stderr().contains(key), "the key leaked to stderr");
        assertFalse(Files.readString(workspace.resolve("r.json"), StandardCharsets.UTF_8).contains(key),
                "the key leaked into the report file");
    }

    @Test
    @DisplayName("The usage text warns that a key on the command line is visible to other processes")
    void usageWarnsAboutCommandLineKeys() {
        command.run(new String[] {"--help"});

        assertTrue(stdout().contains("ANTHROPIC_API_KEY"), stdout());
        assertTrue(stdout().toLowerCase().contains("process list"), stdout());
    }

    // ------------------------------------------------------------ output format

    @Test
    @DisplayName("The full analysis is printed to the console by default")
    void theAnalysisIsPrintedByDefault() throws Exception {
        command.run(new String[] {"analyze", "--dir", submissions().toString(),
                "--out", workspace.resolve("r.json").toString(), "--assignment", "hw3"});

        assertTrue(stdout().contains("ACADEMIC INTEGRITY REPORT"), stdout());
        assertTrue(stdout().contains("COHORT OVERVIEW"), stdout());
        assertTrue(stdout().contains("HOW TO READ THIS"), stdout());
    }

    @Test
    @DisplayName("--quiet keeps the operational summary but drops the analysis")
    void quietSuppressesTheAnalysisOnly() throws Exception {
        command.run(new String[] {"analyze", "--dir", submissions().toString(),
                "--out", workspace.resolve("r.json").toString(), "--assignment", "hw3", "--quiet"});

        assertFalse(stdout().contains("ACADEMIC INTEGRITY REPORT"), stdout());
        assertTrue(stdout().contains("new results recorded"), stdout());
        assertTrue(stdout().contains("Report written to"), stdout());
    }

    @Test
    @DisplayName("The output extension chooses the format: .json for machines, .txt for people")
    void theExtensionSelectsTheReportFormat() throws Exception {
        Path root = submissions();
        Path asJson = workspace.resolve("out.json");
        Path asText = workspace.resolve("out.txt");

        command.run(new String[] {"analyze", "--dir", root.toString(),
                "--out", asJson.toString(), "--assignment", "hw3", "--quiet"});
        setUp();
        command.run(new String[] {"analyze", "--dir", root.toString(),
                "--out", asText.toString(), "--assignment", "hw3", "--quiet"});

        String json = Files.readString(asJson, StandardCharsets.UTF_8);
        String text = Files.readString(asText, StandardCharsets.UTF_8);

        assertTrue(json.startsWith("{"), "a .json report should be JSON");
        assertTrue(json.contains("\"allPairs\""), json);

        assertTrue(text.contains("ACADEMIC INTEGRITY REPORT"), "a .txt report should be prose");
        assertFalse(text.startsWith("{"), "a .txt report must not be JSON");
    }

    @Test
    void markdownExtensionAlsoProducesProse() throws Exception {
        Path asMarkdown = workspace.resolve("out.md");
        command.run(new String[] {"analyze", "--dir", submissions().toString(),
                "--out", asMarkdown.toString(), "--assignment", "hw3", "--quiet"});

        assertTrue(Files.readString(asMarkdown, StandardCharsets.UTF_8)
                .contains("ACADEMIC INTEGRITY REPORT"));
    }

    @Test
    @DisplayName("The printed analysis names students, not just file paths")
    void theAnalysisNamesStudents() throws Exception {
        command.run(new String[] {"analyze", "--dir", submissions().toString(),
                "--out", workspace.resolve("r.json").toString(), "--assignment", "hw3"});

        assertTrue(stdout().contains("student1"), stdout());
    }

    // -------------------------------------------------------------- JSON writing

    @Test
    void jsonEscapingHandlesTheAwkwardCharacters() {
        assertEquals("a\\\"b", Json.escape("a\"b"));
        assertEquals("a\\\\b", Json.escape("a\\b"));
        assertEquals("a\\nb", Json.escape("a\nb"));
        assertEquals("a\\tb", Json.escape("a\tb"));
        assertEquals("\\u0001", Json.escape(String.valueOf((char) 1)));
        assertEquals("café ☃", Json.escape("café ☃"));
    }

    @Test
    @DisplayName("Non-finite numbers become null, since JSON has no NaN")
    void nonFiniteNumbersAreWrittenAsNull() {
        assertEquals("null", Json.number(Double.NaN));
        assertEquals("null", Json.number(Double.POSITIVE_INFINITY));
        assertEquals("0.5", Json.number(0.5));
    }

    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @DisplayName("A filename containing quotes and newlines still produces parseable JSON")
    void hostileFilenamesDoNotBreakTheReport() throws Exception {
        Path root = workspace.resolve("hostile");
        write(root.resolve("student1/We\"ird.java"), GRADEBOOK);
        write(root.resolve("student2/Solution.java"), GRADEBOOK_RENAMED);
        Path report = workspace.resolve("hostile.json");

        assertEquals(AnalyzeCommand.EXIT_OK, command.run(new String[] {
                "analyze", "--dir", root.toString(), "--out", report.toString()}), stderr());

        String json = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(json.contains("We\\\"ird.java"), "the quote should be escaped, not raw: " + json);
        assertEquals(countUnescapedQuotes(json) % 2, 0, "unbalanced quotes mean invalid JSON");
    }

    private static int countUnescapedQuotes(String json) {
        int count = 0;
        for (int i = 0; i < json.length(); i++) {
            if (json.charAt(i) == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                count++;
            }
        }
        return count;
    }

    private static final String GRADEBOOK = """
            public class GradeBook {
                private final int[] scores;
                public GradeBook(int[] scores) { this.scores = scores; }
                public double average() {
                    int total = 0;
                    for (int score : scores) { total += score; }
                    return (double) total / scores.length;
                }
                public int highest() {
                    int best = scores[0];
                    for (int i = 1; i < scores.length; i++) {
                        if (scores[i] > best) { best = scores[i]; }
                    }
                    return best;
                }
            }
            """;

    private static final String GRADEBOOK_RENAMED = """
            public class MarkRegister {
                private final int[] values;
                public MarkRegister(int[] values) { this.values = values; }
                public int peak() {
                    int top = values[0];
                    for (int k = 1; k < values.length; k++) {
                        if (values[k] > top) { top = values[k]; }
                    }
                    return top;
                }
                public double mean() {
                    int sum = 0;
                    for (int value : values) { sum += value; }
                    return (double) sum / values.length;
                }
            }
            """;
}
