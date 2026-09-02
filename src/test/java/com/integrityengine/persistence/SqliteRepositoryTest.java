package com.integrityengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.integrityengine.domain.CodeSubmission;
import com.integrityengine.domain.SimilarityResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Persistence is tested against a real SQLite file in a temporary directory, not a mock.
 *
 * <p>An in-memory URL would be misleading here: {@code jdbc:sqlite::memory:} gives every
 * connection its own private database, and these repositories open a connection per
 * operation, so nothing would ever be found again. Testing on a real file exercises the
 * same code path production uses.
 */
class SqliteRepositoryTest {

    @TempDir
    Path directory;

    private RepositoryFactory factory;
    private SubmissionRepository submissions;
    private ResultRepository results;

    @BeforeEach
    void setUp() {
        factory = new RepositoryFactory(directory.resolve("integrity.db"));
        submissions = factory.submissions();
        results = factory.results();
    }

    private static CodeSubmission submission(String id, String assignment) {
        return new CodeSubmission(id, "student-" + id, assignment, id + ".java", "int x = 1;");
    }

    // ------------------------------------------------------------------ submissions

    @Nested
    class Submissions {

        @Test
        void savedSubmissionsComeBackIntact() {
            CodeSubmission original = new CodeSubmission(
                    "s1", "student-1", "a1", "Main.java", "public class Main {}\n");

            submissions.save(original);
            List<CodeSubmission> found = submissions.findAll();

            assertEquals(1, found.size());
            CodeSubmission stored = found.get(0);
            assertEquals(original.getSubmissionId(), stored.getSubmissionId());
            assertEquals(original.getStudentId(), stored.getStudentId());
            assertEquals(original.getAssignmentId(), stored.getAssignmentId());
            assertEquals(original.getFilename(), stored.getFilename());
            assertEquals(original.getSourceCode(), stored.getSourceCode());
        }

        @Test
        void anEmptyDatabaseReturnsEmptyListsNotNull() {
            assertEquals(List.of(), submissions.findAll());
            assertEquals(List.of(), submissions.findByAssignment("nothing"));
        }

        @Test
        void findByAssignmentFiltersAndDoesNotLeakOtherAssignments() {
            submissions.save(submission("s1", "a1"));
            submissions.save(submission("s2", "a1"));
            submissions.save(submission("s3", "a2"));

            assertEquals(List.of("s1", "s2"), ids(submissions.findByAssignment("a1")));
            assertEquals(List.of("s3"), ids(submissions.findByAssignment("a2")));
            assertEquals(3, submissions.findAll().size());
        }

        @Test
        @DisplayName("Re-saving the same id replaces it, so a re-run does not fail or duplicate")
        void savingTheSameSubmissionTwiceIsIdempotent() {
            submissions.save(new CodeSubmission("s1", "student-1", "a1", "Main.java", "version one"));
            submissions.save(new CodeSubmission("s1", "student-1", "a1", "Main.java", "version two"));

            List<CodeSubmission> found = submissions.findAll();
            assertEquals(1, found.size(), "an upsert must not create a second row");
            assertEquals("version two", found.get(0).getSourceCode());
        }

        @Test
        void resultsAreOrderedDeterministically() {
            submissions.save(submission("s3", "a1"));
            submissions.save(submission("s1", "a1"));
            submissions.save(submission("s2", "a1"));

            assertEquals(List.of("s1", "s2", "s3"), ids(submissions.findAll()));
            assertEquals(ids(submissions.findAll()), ids(submissions.findAll()));
        }

        @Test
        @DisplayName("Query results are immutable: a caller cannot edit the repository's answer")
        void returnedSubmissionListsAreImmutable() {
            submissions.save(submission("s1", "a1"));

            List<CodeSubmission> all = submissions.findAll();
            List<CodeSubmission> filtered = submissions.findByAssignment("a1");

            assertThrows(UnsupportedOperationException.class, () -> all.add(submission("s9", "a1")));
            assertThrows(UnsupportedOperationException.class, () -> filtered.clear());
        }

        @Test
        void nullsAreRejected() {
            assertThrows(NullPointerException.class, () -> submissions.save(null));
            assertThrows(NullPointerException.class, () -> submissions.findByAssignment(null));
        }
    }

    // ---------------------------------------------------------------------- results

    @Nested
    class Results {

        @BeforeEach
        void storeSubmissions() {
            submissions.save(submission("s1", "a1"));
            submissions.save(submission("s2", "a1"));
            submissions.save(submission("s3", "a2"));
        }

        @Test
        void savedResultsComeBackIntact() {
            results.save(new SimilarityResult("s1", "s2", 0.875, "jaccard"));

            List<SimilarityResult> found = results.findByAssignment("a1");
            assertEquals(1, found.size());
            assertEquals("s1", found.get(0).getLeftSubmissionId());
            assertEquals("s2", found.get(0).getRightSubmissionId());
            assertEquals(0.875, found.get(0).getSimilarityScore(), 1e-9);
            assertEquals("jaccard", found.get(0).getComparatorName());
        }

        @Test
        @DisplayName("The assignment is reached through the submission, not duplicated on the row")
        void findByAssignmentJoinsThroughTheSubmission() {
            results.save(new SimilarityResult("s1", "s2", 0.9, "jaccard"));
            results.save(new SimilarityResult("s3", "s3", 0.1, "jaccard"));

            assertEquals(1, results.findByAssignment("a1").size());
            assertEquals(1, results.findByAssignment("a2").size());
            assertEquals(List.of(), results.findByAssignment("a3"));
        }

        @Test
        @DisplayName("A result referring to unknown submissions is rejected, not silently orphaned")
        void orphanResultsAreRejected() {
            // Without the foreign key this would insert happily and then be invisible to
            // every query -- a row that exists but can never be reached.
            PersistenceException failure = assertThrows(PersistenceException.class,
                    () -> results.save(new SimilarityResult("ghost", "s2", 0.5, "jaccard")));

            assertNotNull(failure.getCause(), "the underlying SQLException must be retained");
            assertEquals(List.of(), results.findByAssignment("a1"));
        }

        @Test
        void resultsAreOrderedByDescendingScore() {
            results.save(new SimilarityResult("s1", "s2", 0.20, "jaccard"));
            results.save(new SimilarityResult("s2", "s1", 0.90, "jaccard"));
            results.save(new SimilarityResult("s1", "s2", 0.55, "jaccard"));

            List<SimilarityResult> found = results.findByAssignment("a1");
            assertEquals(List.of(0.90, 0.55, 0.20),
                    found.stream().map(SimilarityResult::getSimilarityScore).toList());
        }

        @Test
        @DisplayName("The join follows the LEFT submission, so a cross-assignment pair lands in one place only")
        void findByAssignmentUsesTheLeftSubmissionNotTheRight() {
            // s1 is in a1 and s3 is in a2. If the join ever switched to the right-hand
            // submission this pair would silently move from one assignment's report to
            // another's -- and every other fixture here has both sides in the same
            // assignment, so nothing else can detect it.
            results.save(new SimilarityResult("s1", "s3", 0.7, "jaccard"));

            assertEquals(1, results.findByAssignment("a1").size(),
                    "the pair belongs to the left submission's assignment");
            assertEquals(List.of(), results.findByAssignment("a2"),
                    "it must not also appear under the right submission's assignment");
        }

        @Test
        void returnedResultListsAreImmutable() {
            results.save(new SimilarityResult("s1", "s2", 0.5, "jaccard"));
            List<SimilarityResult> found = results.findByAssignment("a1");

            assertThrows(UnsupportedOperationException.class,
                    () -> found.add(new SimilarityResult("s1", "s2", 0.9, "jaccard")));
        }

        @Test
        @DisplayName("Saving is append-only: re-analysis adds rows rather than replacing them")
        void savingIsAppendOnly() {
            results.save(new SimilarityResult("s1", "s2", 0.5, "jaccard"));
            results.save(new SimilarityResult("s1", "s2", 0.5, "jaccard"));

            // Documented behaviour, not an accident -- a result has no natural key.
            assertEquals(2, results.findByAssignment("a1").size());
        }

        @Test
        void scoresSurviveTheRoundTripExactly() {
            for (double score : new double[] {0.0, 1.0, 0.1234567890123, 0.999999999}) {
                results.save(new SimilarityResult("s1", "s2", score, "jaccard"));
            }
            List<Double> stored = results.findByAssignment("a1").stream()
                    .map(SimilarityResult::getSimilarityScore).sorted().toList();

            assertEquals(List.of(0.0, 0.1234567890123, 0.999999999, 1.0), stored);
        }

        @Test
        void nullsAreRejected() {
            assertThrows(NullPointerException.class, () -> results.save(null));
            assertThrows(NullPointerException.class, () -> results.findByAssignment(null));
        }
    }

    // ------------------------------------------------------------- hostile content

    @ParameterizedTest
    @ValueSource(strings = {
            "'; DROP TABLE submission; --",
            "\" OR \"1\"=\"1",
            "Robert'); DROP TABLE submission;--",
            "100%_wildcard",
            "line1\nline2\ttabbed"})
    @DisplayName("SQL metacharacters in untrusted values are stored, not executed")
    void sqlMetacharactersAreParameterisedNotInterpolated(String hostile) {
        // Submission ids and filenames come from the filesystem. A file really can be
        // named "'; DROP TABLE submission; --.java".
        submissions.save(new CodeSubmission(hostile, hostile, hostile, hostile, hostile));

        List<CodeSubmission> found = submissions.findByAssignment(hostile);
        assertEquals(1, found.size(), "the value should round-trip verbatim");
        assertEquals(hostile, found.get(0).getSourceCode());
        // The table is still there.
        assertEquals(1, submissions.findAll().size());
    }

    @Test
    @DisplayName("Unicode, emoji and very large sources survive the round trip")
    void unusualContentIsPreserved() {
        String awkward = "// café ☃ 😀\nString s = \"你好\";\n";
        String large = "int x = 1;\n".repeat(50_000);

        submissions.save(new CodeSubmission("s1", "st", "a1", "Unicode.java", awkward));
        submissions.save(new CodeSubmission("s2", "st", "a1", "Large.java", large));

        List<CodeSubmission> found = submissions.findByAssignment("a1");
        assertEquals(awkward, found.get(0).getSourceCode());
        assertEquals(large, found.get(1).getSourceCode());
        assertTrue(large.length() > 500_000);
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    @DisplayName("The schema is created on demand and surviving data is readable by a new instance")
    void dataOutlivesTheRepositoryInstance() {
        submissions.save(submission("s1", "a1"));

        SubmissionRepository reopened = new RepositoryFactory(directory.resolve("integrity.db")).submissions();

        assertEquals(List.of("s1"), ids(reopened.findAll()));
    }

    @Test
    void twoRepositoriesCanShareOneFile() {
        SubmissionRepository other = factory.submissions();
        submissions.save(submission("s1", "a1"));
        other.save(submission("s2", "a1"));

        assertEquals(List.of("s1", "s2"), ids(submissions.findAll()));
        assertEquals(List.of("s1", "s2"), ids(other.findAll()));
    }

    @Test
    void anUnusableDatabasePathFailsWithAClearException() {
        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> new RepositoryFactory("jdbc:sqlite:/nonexistent-directory/nope/db.sqlite").submissions());

        assertTrue(failure.getMessage().contains("schema") || failure.getMessage().contains("open"),
                failure.getMessage());
    }

    @Test
    void factoryRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new RepositoryFactory((Path) null));
    }

    private static List<String> ids(List<CodeSubmission> found) {
        return found.stream().map(CodeSubmission::getSubmissionId).toList();
    }
}
