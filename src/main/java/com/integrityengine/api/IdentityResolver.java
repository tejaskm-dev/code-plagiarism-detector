package com.integrityengine.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Works out which student a file belongs to.
 *
 * <p>Three strategies, first match wins, deliberately ordered from most reliable to
 * least:
 *
 * <ol>
 *   <li><b>Folder.</b> A path with a directory component gives its first segment, the
 *       same convention the CLI already uses for a directory of submissions.</li>
 *   <li><b>Filename, roll number.</b> A roll-number-shaped token embedded in the
 *       filename.</li>
 *   <li><b>Filename, relative to the batch.</b> Tokens that are unique within the upload
 *       name the student; tokens shared across the batch name the assignment. See
 *       {@link #resolveBatch}.</li>
 *   <li><b>Nothing.</b> No guessing. The caller supplies a unique placeholder so the
 *       file is still stored and compared, and the UI marks it as needing manual
 *       identification.</li>
 * </ol>
 *
 * <p><b>What the filename pattern matches.</b> A token of letters and digits where a run
 * of at least two digits is preceded by at most four letters — the shape of essentially
 * every institutional roll number: {@code 22CS101}, {@code CS22B1001}, {@code 20BCE1234},
 * {@code 2022CSE045}. It also accepts a bare run of five or more digits
 * ({@code 20231234}), which is a student number in many systems.
 *
 * <p><b>What the roll-number rule deliberately does not match.</b> Plain names —
 * {@code Gradebook_JohnSmith} yields nothing on its own, because in a single filename a
 * personal name is indistinguishable from a class or module name, and guessing wrong
 * attributes work to the wrong person. Version and sequence suffixes are rejected too:
 * {@code Gradebook_v2}, {@code Assignment2} and {@code Solution_final}. The
 * leading-letters limit of four is what rejects {@code Assignment2}. Four-digit runs are
 * rejected as bare numbers because they are far more often a year than an identifier.
 *
 * <p><b>Why the batch rule exists.</b> That caution is right for one filename in
 * isolation and needlessly strict for a whole cohort at once. Given ten files named
 * {@code student01_alice__Gradebook.java} … {@code student10_jia__Gradebook.java}, the
 * word {@code Gradebook} is in every one of them and {@code alice} is in exactly one.
 * Frequency across the batch separates the assignment's name from the student's without
 * anyone having to guess what a name looks like — the same reasoning the similarity
 * pipeline already uses to strip boilerplate.
 */
final class IdentityResolver {

    /** Letters (at most 4) then 2+ digits, optionally more letters and digits. */
    private static final Pattern ROLL_NUMBER =
            Pattern.compile("^[A-Za-z]{0,4}\\d{2,}[A-Za-z]{0,4}\\d{0,6}[A-Za-z]{0,2}$");

    /** A bare student number. Five digits minimum, so years do not qualify. */
    private static final Pattern STUDENT_NUMBER = Pattern.compile("^\\d{5,}$");

    private static final Pattern SEPARATORS = Pattern.compile("[_\\-. ]+");

    /** Below this many files, token frequency says nothing useful. */
    static final int MINIMUM_BATCH = 3;

    /** A token in at least this share of the batch belongs to the assignment. */
    static final double SHARED_TOKEN_FRACTION = 0.5;

    private IdentityResolver() {
    }

    /**
     * @param path file path relative to the upload root; may or may not contain folders
     * @return the resolved identity, or empty when nothing identifiable was found
     */
    static java.util.Optional<StudentIdentity> resolve(String path) {
        if (path == null || path.isBlank()) {
            return java.util.Optional.empty();
        }

        String normalised = path.replace('\\', '/');
        int slash = normalised.indexOf('/');
        if (slash > 0) {
            String folder = normalised.substring(0, slash).trim();
            if (!folder.isEmpty()) {
                return java.util.Optional.of(
                        new StudentIdentity(folder, StudentIdentity.Source.FOLDER));
            }
        }

        String filename = normalised.substring(normalised.lastIndexOf('/') + 1);
        return fromFilename(filename)
                .map(id -> new StudentIdentity(id, StudentIdentity.Source.FILENAME));
    }

    /**
     * Resolves a whole upload at once, using the batch to separate student tokens from
     * assignment tokens.
     *
     * <p>Falls back to {@link #resolve} for any file the batch cannot settle, so folders
     * and roll numbers still win where they are present. Returns one entry per input, in
     * the same order.
     *
     * @param paths file paths relative to the upload root
     * @return the resolved identity for each path, empty where nothing was identifiable
     */
    static List<java.util.Optional<StudentIdentity>> resolveBatch(List<String> paths) {
        List<java.util.Optional<StudentIdentity>> resolved = new ArrayList<>(paths.size());
        for (String path : paths) {
            resolved.add(resolve(path));
        }

        // Only worth doing with a real cohort. Below this, "appears in every file" and
        // "appears once" are the same statement and the frequency signal is noise.
        if (paths.size() < MINIMUM_BATCH) {
            return resolved;
        }

        Map<String, Integer> frequency = new HashMap<>();
        List<List<String>> perFile = new ArrayList<>(paths.size());
        for (String path : paths) {
            List<String> tokens = tokensOf(path);
            perFile.add(tokens);
            for (String token : new LinkedHashSet<>(tokens)) {
                frequency.merge(token.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }

        // A token carried by most of the batch describes the assignment, not a person.
        int shared = Math.max(2, (int) Math.ceil(paths.size() * SHARED_TOKEN_FRACTION));

        for (int i = 0; i < paths.size(); i++) {
            if (resolved.get(i).isPresent()) {
                continue;
            }
            List<String> distinctive = new ArrayList<>();
            for (String token : perFile.get(i)) {
                if (frequency.getOrDefault(token.toLowerCase(Locale.ROOT), 0) < shared) {
                    distinctive.add(token);
                }
            }
            if (!distinctive.isEmpty()) {
                resolved.set(i, java.util.Optional.of(new StudentIdentity(
                        String.join("_", distinctive), StudentIdentity.Source.FILENAME)));
            }
        }
        return resolved;
    }

    /** Filename stem split into its separator-delimited parts, extension dropped. */
    private static List<String> tokensOf(String path) {
        String normalised = path.replace('\\', '/');
        String filename = normalised.substring(normalised.lastIndexOf('/') + 1);
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;

        List<String> tokens = new ArrayList<>();
        for (String token : SEPARATORS.split(stem)) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** @return a roll-number-shaped token from the filename stem, if there is one */
    static java.util.Optional<String> fromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return java.util.Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;

        for (String token : SEPARATORS.split(stem)) {
            if (token.length() < 4) {
                continue;
            }
            boolean hasLetter = token.chars().anyMatch(Character::isLetter);
            boolean hasDigit = token.chars().anyMatch(Character::isDigit);

            if (hasLetter && hasDigit && ROLL_NUMBER.matcher(token).matches()) {
                return java.util.Optional.of(token);
            }
            if (!hasLetter && STUDENT_NUMBER.matcher(token).matches()) {
                return java.util.Optional.of(token);
            }
        }
        return java.util.Optional.empty();
    }
}
