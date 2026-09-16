package com.integrityengine.api;

import com.integrityengine.tokenizer.TokenizerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns whatever a browser uploaded into a list of identified source files.
 *
 * <p>Handles two shapes: individual files, which carry no folder structure at all, and a
 * ZIP archive, whose entry paths do. Extraction is in memory and bounded — an archive is
 * untrusted input, so entry count, per-entry size and total expanded size are all capped,
 * and any entry whose path tries to escape the root is dropped rather than sanitised.
 */
final class SubmissionIntake {

    /** Per-file cap, matching the limit applied to a direct upload. */
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    /** Total expanded size of one archive. Guards against a zip bomb. */
    static final long MAX_ARCHIVE_BYTES = 64L * 1024 * 1024;

    static final int MAX_ARCHIVE_ENTRIES = 2000;

    private final TokenizerFactory tokenizers = new TokenizerFactory();

    /** One source file, with the path it arrived under. */
    record Extracted(String path, String filename, String content) {
    }

    /** A file that could not be taken, and why. */
    record Skipped(String path, String reason) {
    }

    record Result(List<Extracted> files, List<Skipped> skipped) {
    }

    static boolean isArchive(String filename) {
        return filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".zip");
    }

    /**
     * Expand one uploaded archive.
     *
     * @param name the archive's own filename, used only in skip messages
     */
    Result readArchive(String name, InputStream stream) {
        List<Extracted> files = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        long totalBytes = 0;
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(stream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    skipped.add(new Skipped(name, "archive has more than "
                            + MAX_ARCHIVE_ENTRIES + " entries"));
                    break;
                }
                if (entry.isDirectory()) {
                    continue;
                }

                String path = normalise(entry.getName());
                if (path == null) {
                    skipped.add(new Skipped(entry.getName(), "unsafe path in archive"));
                    continue;
                }
                if (isNoise(path)) {
                    continue;
                }

                String filename = path.substring(path.lastIndexOf('/') + 1);
                if (tokenizers.forFilename(filename).isEmpty()) {
                    skipped.add(new Skipped(path, "unrecognised source extension"));
                    continue;
                }

                byte[] content = readBounded(zip, MAX_FILE_BYTES);
                if (content == null) {
                    skipped.add(new Skipped(path, "larger than " + MAX_FILE_BYTES + " bytes"));
                    continue;
                }
                totalBytes += content.length;
                if (totalBytes > MAX_ARCHIVE_BYTES) {
                    skipped.add(new Skipped(name, "archive expands beyond "
                            + MAX_ARCHIVE_BYTES + " bytes"));
                    break;
                }
                files.add(new Extracted(path, filename, new String(content, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            skipped.add(new Skipped(name, "could not be read as a zip archive"));
        }

        return new Result(stripCommonRoot(files), skipped);
    }

    /**
     * Drops a single wrapping directory shared by every entry.
     *
     * <p>Archives made by right-clicking a folder contain {@code submissions/alice/X.java}
     * rather than {@code alice/X.java}. Without this, every file would resolve to the
     * student "submissions" and the collision this whole change exists to fix would
     * simply move one level up.
     */
    static List<Extracted> stripCommonRoot(List<Extracted> files) {
        if (files.size() < 2) {
            return files;
        }
        Set<String> firstSegments = new LinkedHashSet<>();
        for (Extracted file : files) {
            int slash = file.path().indexOf('/');
            if (slash <= 0) {
                return files;   // at least one file sits at the top level; nothing to strip
            }
            firstSegments.add(file.path().substring(0, slash));
        }
        if (firstSegments.size() != 1) {
            return files;
        }

        String root = firstSegments.iterator().next() + "/";
        List<Extracted> stripped = new ArrayList<>(files.size());
        for (Extracted file : files) {
            stripped.add(new Extracted(
                    file.path().substring(root.length()), file.filename(), file.content()));
        }
        return stripped;
    }

    /** @return a path safe to use, or null if it escapes the archive root */
    private static String normalise(String rawName) {
        String path = rawName.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                return null;
            }
        }
        return path.isBlank() ? null : path;
    }

    /** Archive cruft that is never a submission. */
    private static boolean isNoise(String path) {
        if (path.startsWith("__MACOSX/") || path.contains("/__MACOSX/")) {
            return true;
        }
        for (String segment : path.split("/")) {
            if (segment.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /** @return the bytes, or null if the entry exceeds the limit */
    private static byte[] readBounded(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > limit) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Assigns a unique submission id to each extracted file.
     *
     * <p>The id is never derived from the filename alone. It combines the assignment, the
     * resolved student and the filename, and a final guard appends a discriminator if two
     * files still collide — so one student's work can never be silently replaced by
     * another's file of the same name.
     *
     * <p>That guard is scoped to a single request, deliberately. Two colliding files
     * <em>within</em> one upload are two different submissions and both must survive.
     * The same student re-uploading the same filename <em>across</em> requests is a
     * resubmission and should replace the earlier copy, which is exactly what the
     * repository's upsert does. Extending the guard across requests turns every
     * re-upload into a duplicate submission and inflates the pair count.
     */
    static List<Identified> identify(String assignmentId, List<Extracted> files,
                                     java.util.function.Supplier<String> placeholderIds) {
        List<Identified> identified = new ArrayList<>(files.size());
        Set<String> used = new HashSet<>();

        // Resolved as a batch rather than file by file: a name that appears in every
        // filename is the assignment's, and one that appears once is a student's. That
        // distinction cannot be made from a single filename, which is why uploading a
        // flat set of well-named files used to leave every one of them unidentified.
        List<String> paths = new ArrayList<>(files.size());
        for (Extracted file : files) {
            paths.add(file.path());
        }
        List<Optional<StudentIdentity>> resolved = IdentityResolver.resolveBatch(paths);

        for (int index = 0; index < files.size(); index++) {
            Extracted file = files.get(index);
            StudentIdentity identity = resolved.get(index)
                    .orElseGet(() -> new StudentIdentity(
                            placeholderIds.get(), StudentIdentity.Source.UNIDENTIFIED));

            String base = assignmentId + "/" + identity.studentId() + "/" + file.filename();
            String id = base;
            int discriminator = 2;
            while (!used.add(id)) {
                id = base + "~" + discriminator++;
            }
            identified.add(new Identified(id, identity, file));
        }
        return identified;
    }

    record Identified(String submissionId, StudentIdentity identity, Extracted file) {
    }

    Optional<Extracted> readPlainFile(String filename, InputStream stream) {
        try {
            byte[] content = readBounded(stream, MAX_FILE_BYTES);
            if (content == null) {
                return Optional.empty();
            }
            return Optional.of(new Extracted(
                    filename, filename, new String(content, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    boolean isSupportedSource(String filename) {
        return tokenizers.forFilename(filename).isPresent();
    }
}
