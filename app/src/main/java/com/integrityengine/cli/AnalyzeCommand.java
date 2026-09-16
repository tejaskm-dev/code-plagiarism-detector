package com.integrityengine.cli;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.Report;
import com.integrityengine.engine.IntegrityEngine;
import com.integrityengine.persistence.RepositoryFactory;
import com.integrityengine.persistence.ResultRepository;
import com.integrityengine.tokenizer.TokenizerFactory;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code analyze --dir <path> --out <file> [--llm-api-key <key>] [--db <file>] [--assignment <id>]}
 *
 * <p>Deliberately small: parse arguments, read the directory, call the facade, write
 * JSON. All of the actual work lives behind {@link IntegrityEngine}.
 */
final class AnalyzeCommand {

    static final int EXIT_OK = 0;
    static final int EXIT_RUNTIME_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final String USAGE = """
            usage: analyze --dir <path> --out <file> [options]

              --dir <path>          directory of submissions to analyse (searched recursively)
              --out <file>          where to write the JSON report
              --db <file>           SQLite database (default: integrity.db beside --out)
              --assignment <id>     assignment identifier (default: the directory name)
              --llm-api-key <key>   enable AI-authorship analysis (BYOK)
              --quiet               print only the summary, not the full analysis

            The report format follows the --out extension: .json for machine use,
            .txt or .md for the same analysis printed to the console.

            The API key may also be supplied via the ANTHROPIC_API_KEY environment
            variable, which is preferable: a key passed on the command line is visible
            to any other process on the machine via the process list.

            Other commands:
              serve [--port <n>] [--db <file>]   start the web UI and REST API
              --version                          print the version
            """;

    private final PrintStream out;
    private final PrintStream err;

    AnalyzeCommand(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    int run(String[] args) {
        // Bare invocation is a request for help, not a mistake. Naming the subcommand
        // and then omitting its required options *is* a mistake, and stays an error.
        if (args.length == 0) {
            out.print(USAGE);
            return EXIT_OK;
        }

        Map<String, String> options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            err.println();
            err.print(USAGE);
            return EXIT_USAGE;
        }

        if (options.containsKey("help")) {
            out.print(USAGE);
            return EXIT_OK;
        }

        String directory = options.get("dir");
        String output = options.get("out");
        if (directory == null || output == null) {
            err.println("error: --dir and --out are both required");
            err.println();
            err.print(USAGE);
            return EXIT_USAGE;
        }

        try {
            return analyze(options, Path.of(directory), Path.of(output));
        } catch (IllegalArgumentException | UncheckedIOException | IOException e) {
            err.println("error: " + e.getMessage());
            return EXIT_RUNTIME_ERROR;
        } catch (RuntimeException e) {
            // Never print a stack trace at a user; never risk echoing a key into one.
            err.println("error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return EXIT_RUNTIME_ERROR;
        }
    }

    private int analyze(Map<String, String> options, Path directory, Path output) throws IOException {
        if (!Files.isDirectory(directory)) {
            err.println("error: not a directory: " + directory);
            return EXIT_USAGE;
        }

        String assignmentId = options.getOrDefault("assignment", defaultAssignmentId(directory));
        // Deliberately NOT inside the submissions directory: writing a database into
        // the input tree pollutes the thing being analysed, and on a re-run the file
        // sits inside the directory being walked.
        Path database = options.containsKey("db")
                ? Path.of(options.get("db"))
                : siblingOf(output, "integrity.db");
        Path databaseParent = database.toAbsolutePath().getParent();
        if (databaseParent != null) {
            Files.createDirectories(databaseParent);
        }

        List<CodeSubmission> submissions = readSubmissions(directory, assignmentId);
        if (submissions.isEmpty()) {
            err.println("error: no files with a recognised source extension under " + directory);
            return EXIT_RUNTIME_ERROR;
        }

        String apiKey = resolveApiKey(options);
        RepositoryFactory repositories = new RepositoryFactory(database);
        ResultRepository results = repositories.results();

        int before = results.findByAssignment(assignmentId).size();
        Report report = new IntegrityEngine(repositories).analyzeBatch(submissions, apiKey);
        int after = results.findByAssignment(assignmentId).size();

        Map<String, String> students = new java.util.LinkedHashMap<>();
        for (CodeSubmission submission : submissions) {
            students.put(submission.getSubmissionId(), submission.getStudentId());
        }
        String analysis = new TextReportWriter().render(report, students);

        Files.createDirectories(output.toAbsolutePath().getParent());
        // The extension picks the format: nobody wants to read JSON, and nothing wants
        // to parse prose.
        boolean humanFormat = output.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .matches(".*\\.(txt|md|text)$");
        Files.writeString(output, humanFormat ? analysis : ReportWriter.toJson(report),
                StandardCharsets.UTF_8);

        if (!options.containsKey("quiet")) {
            out.println();
            out.print(analysis);
            out.println();
        }
        summarise(report, submissions.size(), after - before, after, output, database, apiKey != null);
        return EXIT_OK;
    }

    /**
     * Results are append-only, so a bare "done" would hide the fact that repeated runs
     * accumulate. Reporting new-versus-total makes that visible at a glance.
     */
    private void summarise(Report report, int submissionCount, int added, int total,
                           Path output, Path database, boolean aiEnabled) {
        out.printf("Analysed %d submissions for assignment %s: %d pairs compared, %d flagged.%n",
                submissionCount, report.getAssignmentId(),
                report.getSimilarityResults().size(), report.getFlaggedResults().size());
        if (aiEnabled) {
            out.printf("  %d AI-authorship verdicts%n", report.getAiResults().size());
        }
        out.printf("%d new results recorded for assignment %s (%d total in %s)%n",
                added, report.getAssignmentId(), total, database);
        out.printf("Report written to %s%n", output);
    }

    /**
     * Reads every file with an extension a tokenizer recognises.
     *
     * <p>The submission id is the assignment id followed by the path relative to the
     * root. Both halves are needed. The relative path alone is stable across re-runs, so
     * re-analysis upserts rather than duplicating — but it is <em>not</em> unique across
     * assignments, and two different cohorts sharing one database will both contain a
     * {@code student1/Solution.java}. Without the prefix the second run silently
     * reassigns the first run's submissions to its own assignment, dragging their
     * results with them through the join.
     *
     * <p>The student id is the first path segment, matching the usual
     * one-directory-per-student layout.
     */
    private static List<CodeSubmission> readSubmissions(Path root, String assignmentId) throws IOException {
        TokenizerFactory tokenizers = new TokenizerFactory();
        List<CodeSubmission> submissions = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            List<Path> candidates = files
                    .filter(Files::isRegularFile)
                    .filter(path -> tokenizers.forFilename(path.getFileName().toString()).isPresent())
                    .filter(path -> !isHidden(root.relativize(path)))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            for (Path file : candidates) {
                String relative = root.relativize(file).toString();
                String source;
                try {
                    source = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    // A binary or mis-encoded file should not abort the whole run.
                    continue;
                }
                submissions.add(new CodeSubmission(
                        assignmentId + "/" + relative,
                        studentIdOf(root.relativize(file)),
                        assignmentId,
                        relative,
                        source));
            }
        }
        return submissions;
    }

    /** True if any segment of the relative path is a dot-directory or dot-file. */
    private static boolean isHidden(Path relative) {
        for (Path segment : relative) {
            if (segment.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static Path siblingOf(Path file, String name) {
        Path parent = file.toAbsolutePath().getParent();
        return parent == null ? Path.of(name) : parent.resolve(name);
    }

    private static String studentIdOf(Path relative) {
        return relative.getNameCount() > 1 ? relative.getName(0).toString() : relative.toString();
    }

    private static String defaultAssignmentId(Path directory) {
        Path name = directory.toAbsolutePath().normalize().getFileName();
        return name == null ? "assignment" : name.toString();
    }

    /** Command line wins, environment is the fallback; neither is ever echoed. */
    private static String resolveApiKey(Map<String, String> options) {
        String fromOptions = options.get("llm-api-key");
        if (fromOptions != null && !fromOptions.isBlank()) {
            return fromOptions;
        }
        String fromEnvironment = System.getenv("ANTHROPIC_API_KEY");
        return fromEnvironment != null && !fromEnvironment.isBlank() ? fromEnvironment : null;
    }

    /** @throws IllegalArgumentException on an unknown flag or a missing value */
    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new java.util.LinkedHashMap<>();
        List<String> valueless = List.of("help", "quiet");
        List<String> known = List.of("dir", "out", "db", "assignment", "llm-api-key", "help", "quiet");

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (argument.equals("analyze")) {
                continue;
            }
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + argument);
            }
            String name = argument.substring(2);
            if (!known.contains(name)) {
                throw new IllegalArgumentException("unknown option: " + argument);
            }
            if (valueless.contains(name)) {
                options.put(name, "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for " + argument);
            }
            options.put(name, args[++i]);
        }
        return options;
    }
}
