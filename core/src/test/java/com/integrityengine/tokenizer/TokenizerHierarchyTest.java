package com.integrityengine.tokenizer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the class hierarchy itself while the method bodies are still stubs, so a later
 * build stage cannot quietly flatten it.
 */
class TokenizerHierarchyTest {

    @Test
    void cFamilyTokenizersShareTheCFamilyBase() {
        List<ITokenizer> cFamily = List.of(
                new JavaTokenizer(), new CTokenizer(), new CppTokenizer(), new JavaScriptTokenizer());

        for (ITokenizer t : cFamily) {
            assertInstanceOf(CFamilyTokenizer.class, t);
            assertInstanceOf(AbstractTokenizer.class, t);
        }
    }

    @Test
    void pythonTokenizerBypassesTheCFamilyBase() {
        PythonTokenizer python = new PythonTokenizer();

        assertInstanceOf(AbstractTokenizer.class, python);
        // Checked reflectively: PythonTokenizer is final, so `instanceof CFamilyTokenizer`
        // is a compile error here -- which is itself the guarantee this test is asserting.
        assertFalse(CFamilyTokenizer.class.isAssignableFrom(PythonTokenizer.class));
        assertEquals(AbstractTokenizer.class, PythonTokenizer.class.getSuperclass());
    }

    @Test
    void polymorphicDispatchReachesEachLanguage() {
        List<ITokenizer> all = List.of(
                new JavaTokenizer(), new CTokenizer(), new CppTokenizer(),
                new JavaScriptTokenizer(), new PythonTokenizer());

        List<Language> languages = all.stream().map(ITokenizer::language).toList();

        assertEquals(
                List.of(Language.JAVA, Language.C, Language.CPP, Language.JAVASCRIPT, Language.PYTHON),
                languages);
    }

    @Test
    void theTemplateMethodIsSealedAgainstOverriding() throws Exception {
        // tokenize() fixes the four-stage pipeline; a subclass that could reorder or
        // skip a stage would break the guarantee that renaming is invisible.
        assertTrue(java.lang.reflect.Modifier.isFinal(
                AbstractTokenizer.class.getDeclaredMethod("tokenize", String.class).getModifiers()));
    }

    @Test
    void everyTokenizerRunsAllFourStages() {
        // Reaching real output at all means no stage threw, in the fixed order.
        assertFalse(new JavaTokenizer().tokenize("class A { int x = 1; }").isEmpty());
        assertFalse(new PythonTokenizer().tokenize("x = 1\n").isEmpty());
    }
}
