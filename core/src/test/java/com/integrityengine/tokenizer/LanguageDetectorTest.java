package com.integrityengine.tokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LanguageDetectorTest {

    private final LanguageDetector detector = new LanguageDetector();

    @ParameterizedTest
    @CsvSource({
            "Main.java, JAVA", "solution.c, C", "solution.cpp, CPP", "a.cc, CPP",
            "a.cxx, CPP", "a.hpp, CPP", "script.py, PYTHON", "app.js, JAVASCRIPT",
            "mod.mjs, JAVASCRIPT", "comp.jsx, JAVASCRIPT"})
    void detectsByExtension(String filename, Language expected) {
        assertEquals(Optional.of(expected), detector.detectLanguage(filename));
    }

    @Test
    void extensionMatchingIsCaseInsensitive() {
        assertEquals(Optional.of(Language.JAVA), detector.detectLanguage("Main.JAVA"));
        assertEquals(Optional.of(Language.PYTHON), detector.detectLanguage("Script.Py"));
    }

    @Test
    void pathsAreStrippedBeforeMatching() {
        assertEquals(Optional.of(Language.JAVA), detector.detectLanguage("/submissions/s1/Main.java"));
        assertEquals(Optional.of(Language.JAVA), detector.detectLanguage("C:\\subs\\Main.java"));
    }

    @Test
    @DisplayName("A dotted directory must not be mistaken for an extension")
    void dottedDirectoriesDoNotConfuseDetection() {
        assertEquals(Optional.of(Language.PYTHON), detector.detectLanguage("/home/my.stuff/run.py"));
        assertTrue(detector.detectLanguage("/home/my.stuff/README").isEmpty());
    }

    @Test
    @DisplayName(".h resolves to C, which is a documented guess rather than a detection")
    void headerFilesResolveToC() {
        assertEquals(Optional.of(Language.C), detector.detectLanguage("stack.h"));
    }

    @Test
    void unknownAndMalformedNamesReturnEmpty() {
        assertTrue(detector.detectLanguage("notes.txt").isEmpty());
        assertTrue(detector.detectLanguage("Makefile").isEmpty());
        assertTrue(detector.detectLanguage("trailing.").isEmpty());
        assertThrows(NullPointerException.class, () -> detector.detectLanguage(null));
    }
}
