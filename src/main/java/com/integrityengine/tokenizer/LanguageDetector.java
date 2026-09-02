package com.integrityengine.tokenizer;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a filename to the language whose tokenizer should handle it.
 */
final class LanguageDetector {

    private static final Map<String, Language> BY_EXTENSION = Map.ofEntries(
            Map.entry("java", Language.JAVA),
            Map.entry("c", Language.C),
            Map.entry("cpp", Language.CPP),
            Map.entry("cc", Language.CPP),
            Map.entry("cxx", Language.CPP),
            Map.entry("c++", Language.CPP),
            Map.entry("hpp", Language.CPP),
            Map.entry("hh", Language.CPP),
            Map.entry("hxx", Language.CPP),
            Map.entry("py", Language.PYTHON),
            Map.entry("pyw", Language.PYTHON),
            Map.entry("js", Language.JAVASCRIPT),
            Map.entry("mjs", Language.JAVASCRIPT),
            Map.entry("cjs", Language.JAVASCRIPT),
            Map.entry("jsx", Language.JAVASCRIPT));

    /**
     * @param filename submission filename, extension included
     * @return the detected language, or empty if the extension is unrecognised
     * @throws NullPointerException if {@code filename} is null
     */
    Optional<Language> detectLanguage(String filename) {
        java.util.Objects.requireNonNull(filename, "filename");

        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return Optional.empty();
        }

        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        // .h is genuinely ambiguous between C and C++ and cannot be settled by name
        // alone; resolving it needs the file contents, which this method does not see.
        if (extension.equals("h")) {
            return Optional.of(Language.C);
        }
        return Optional.ofNullable(BY_EXTENSION.get(extension));
    }
}
