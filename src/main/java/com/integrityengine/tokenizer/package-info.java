/**
 * Language tokenizers.
 *
 * <p>Hierarchy: {@code ITokenizer} (contract) &rarr; {@code AbstractTokenizer}
 * (template method) &rarr; {@code CFamilyTokenizer} (shared C-family lexing) &rarr;
 * concrete per-language tokenizers. {@code PythonTokenizer} branches off
 * {@code AbstractTokenizer} directly because none of the C-family shared logic applies
 * to a whitespace-significant language.
 */
package com.integrityengine.tokenizer;
