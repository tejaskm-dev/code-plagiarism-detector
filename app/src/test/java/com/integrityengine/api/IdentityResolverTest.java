package com.integrityengine.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import java.util.ArrayList;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityResolverTest {

    // ------------------------------------------------------------ folder strategy

    @Test
    @DisplayName("A folder always wins: it is an assertion by the uploader, not a guess")
    void theFolderTakesPrecedence() {
        StudentIdentity identity = IdentityResolver.resolve("22CS101/Gradebook.java").orElseThrow();

        assertEquals("22CS101", identity.studentId());
        assertEquals(StudentIdentity.Source.FOLDER, identity.how());
    }

    @Test
    void onlyTheFirstFolderSegmentIsUsed() {
        assertEquals("alice",
                IdentityResolver.resolve("alice/src/main/Gradebook.java").orElseThrow().studentId());
    }

    @Test
    @DisplayName("A folder wins even when the filename also carries an identifier")
    void folderBeatsFilename() {
        StudentIdentity identity =
                IdentityResolver.resolve("22CS101/Gradebook_99XY888.java").orElseThrow();

        assertEquals("22CS101", identity.studentId());
        assertEquals(StudentIdentity.Source.FOLDER, identity.how());
    }

    @Test
    void aFolderNeedNotLookLikeARollNumber() {
        assertEquals("Jane Doe",
                IdentityResolver.resolve("Jane Doe/Gradebook.java").orElseThrow().studentId());
    }

    // ---------------------------------------------------------- filename strategy

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Gradebook_22CS101.java, 22CS101",
            "22CS101_Gradebook.java, 22CS101",
            "22CS101.java, 22CS101",
            "Gradebook-CS22B1001.java, CS22B1001",
            "solution_20BCE1234.py, 20BCE1234",
            "hw3.2022CSE045.c, 2022CSE045",
            "Gradebook_20231234.java, 20231234"})
    @DisplayName("Roll-number-shaped tokens are recognised in the filename")
    void rollNumbersAreExtracted(String filename, String expected) {
        StudentIdentity identity = IdentityResolver.resolve(filename).orElseThrow();

        assertEquals(expected, identity.studentId());
        assertEquals(StudentIdentity.Source.FILENAME, identity.how());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Gradebook.java",
            "Gradebook_v2.java",
            "Assignment2.java",
            "Solution_final.java",
            "Gradebook_JohnSmith.java",
            "main.c",
            "Gradebook_2024.java",
            "test_v1_2.py"})
    @DisplayName("Anything that is not clearly an identifier is left unresolved rather than guessed")
    void ambiguousFilenamesYieldNothing(String filename) {
        // Guessing here would attribute one student's work to another. The documented
        // non-matches: personal names (indistinguishable from a class name), version and
        // sequence suffixes, and bare four-digit runs, which are far more often a year.
        assertEquals(Optional.empty(), IdentityResolver.resolve(filename));
    }

    @Test
    @DisplayName("A long word followed by a digit is not a roll number")
    void aLeadingWordDisqualifiesAToken() {
        // The leading-letters limit of four is what separates 22CS101 from Assignment2.
        assertEquals(Optional.empty(), IdentityResolver.fromFilename("Assignment2.java"));
        assertEquals(Optional.empty(), IdentityResolver.fromFilename("Gradebook2.java"));
        assertTrue(IdentityResolver.fromFilename("CS22B1001.java").isPresent());
    }

    // --------------------------------------------------------------- degenerate

    @Test
    void blankAndNullPathsResolveToNothing() {
        assertEquals(Optional.empty(), IdentityResolver.resolve(null));
        assertEquals(Optional.empty(), IdentityResolver.resolve(""));
        assertEquals(Optional.empty(), IdentityResolver.resolve("   "));
    }

    @Test
    void windowsSeparatorsAreHandled() {
        assertEquals("22CS101",
                IdentityResolver.resolve("22CS101\\Gradebook.java").orElseThrow().studentId());
    }

    // ------------------------------------------------------- archive root stripping

    @Test
    @DisplayName("A single wrapping folder is stripped, or every student would be 'submissions'")
    void aCommonArchiveRootIsRemoved() {
        List<SubmissionIntake.Extracted> files = List.of(
                new SubmissionIntake.Extracted("submissions/alice/A.java", "A.java", "x"),
                new SubmissionIntake.Extracted("submissions/bob/A.java", "A.java", "y"));

        List<SubmissionIntake.Extracted> stripped = SubmissionIntake.stripCommonRoot(files);

        assertEquals("alice/A.java", stripped.get(0).path());
        assertEquals("bob/A.java", stripped.get(1).path());
        assertEquals("alice", IdentityResolver.resolve(stripped.get(0).path()).orElseThrow().studentId());
    }

    @Test
    @DisplayName("Distinct top-level folders are the students themselves and must be kept")
    void differingFirstSegmentsAreNotStripped() {
        List<SubmissionIntake.Extracted> files = List.of(
                new SubmissionIntake.Extracted("alice/A.java", "A.java", "x"),
                new SubmissionIntake.Extracted("bob/A.java", "A.java", "y"));

        assertEquals(files, SubmissionIntake.stripCommonRoot(files));
    }

    @Test
    void aFileAtTheArchiveRootPreventsStripping() {
        List<SubmissionIntake.Extracted> files = List.of(
                new SubmissionIntake.Extracted("submissions/alice/A.java", "A.java", "x"),
                new SubmissionIntake.Extracted("README.java", "README.java", "y"));

        assertEquals(files, SubmissionIntake.stripCommonRoot(files));
    }

    // ------------------------------------------------------- resolving a whole batch

    @Nested
    @DisplayName("Resolving the batch together")
    class Batch {

        private List<String> cohort() {
            return List.of(
                    "student01_alice__Gradebook.java",
                    "student02_ben__Gradebook.java",
                    "student03_chen__Gradebook.java",
                    "student04_dana__Gradebook.java",
                    "student05_evan__Gradebook.java");
        }

        @Test
        @DisplayName("A flat upload of well-named files is identified, not left unidentified")
        void namesAreLiftedOutOfFilenames() {
            List<Optional<StudentIdentity>> resolved = IdentityResolver.resolveBatch(cohort());

            assertEquals(5, resolved.stream().filter(Optional::isPresent).count(),
                    "every file in this batch carries its student's name");
            assertEquals("student01_alice", resolved.get(0).orElseThrow().studentId());
            assertEquals("student05_evan", resolved.get(4).orElseThrow().studentId());
        }

        @Test
        @DisplayName("The word every file shares is the assignment's, and is dropped")
        void theSharedTokenIsNotTreatedAsAName() {
            IdentityResolver.resolveBatch(cohort()).forEach((identity) ->
                    assertFalse(identity.orElseThrow().studentId().toLowerCase().contains("gradebook"),
                            "the assignment name must not end up in a student id"));
        }

        @Test
        @DisplayName("Resolution is by filename, so it is marked as such")
        void theSourceIsRecorded() {
            assertEquals(StudentIdentity.Source.FILENAME,
                    IdentityResolver.resolveBatch(cohort()).get(0).orElseThrow().how());
        }

        @Test
        @DisplayName("A folder still wins over the filename")
        void foldersTakePrecedence() {
            List<Optional<StudentIdentity>> resolved = IdentityResolver.resolveBatch(List.of(
                    "alice/Gradebook.java", "ben/Gradebook.java", "chen/Gradebook.java"));

            assertEquals("alice", resolved.get(0).orElseThrow().studentId());
            assertEquals(StudentIdentity.Source.FOLDER, resolved.get(0).orElseThrow().how());
        }

        @Test
        @DisplayName("A roll number still wins over the batch rule")
        void rollNumbersTakePrecedence() {
            List<Optional<StudentIdentity>> resolved = IdentityResolver.resolveBatch(List.of(
                    "22CS101_Gradebook.java", "22CS102_Gradebook.java", "22CS103_Gradebook.java"));

            assertEquals("22CS101", resolved.get(0).orElseThrow().studentId());
        }

        @Test
        @DisplayName("When every file has the same name, nobody is identified")
        void identicalFilenamesStayUnidentified() {
            List<Optional<StudentIdentity>> resolved = IdentityResolver.resolveBatch(
                    List.of("Gradebook.java", "Gradebook.java", "Gradebook.java", "Gradebook.java"));

            assertTrue(resolved.stream().allMatch(Optional::isEmpty),
                    "there is genuinely nothing to go on here; asking the user is correct");
        }

        @Test
        @DisplayName("Too few files to judge frequency: no guessing")
        void aTinyBatchIsNotGuessedAt() {
            // With two files, "in every file" and "in one file" are nearly the same
            // statement. The old single-file rules still apply; the batch rule does not.
            List<Optional<StudentIdentity>> resolved =
                    IdentityResolver.resolveBatch(List.of("alice_Gradebook.java", "ben_Gradebook.java"));

            assertTrue(resolved.stream().allMatch(Optional::isEmpty));
        }

        @Test
        @DisplayName("Order is preserved, one answer per input")
        void outputLinesUpWithInput() {
            List<String> paths = new ArrayList<>(cohort());
            paths.add("Gradebook.java");

            List<Optional<StudentIdentity>> resolved = IdentityResolver.resolveBatch(paths);

            assertEquals(paths.size(), resolved.size());
            assertTrue(resolved.get(5).isEmpty(), "the file with no distinctive token");
        }

        @Test
        @DisplayName("A shared suffix like _final is dropped, the distinctive part kept")
        void sharedSuffixesAreStripped() {
            List<Optional<StudentIdentity>> resolved = IdentityResolver.resolveBatch(List.of(
                    "alice_hw3_final.java", "ben_hw3_final.java",
                    "chen_hw3_final.java", "dana_hw3_final.java"));

            assertEquals("alice", resolved.get(0).orElseThrow().studentId());
        }
    }
}
