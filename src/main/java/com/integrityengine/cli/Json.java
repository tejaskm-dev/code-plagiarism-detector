package com.integrityengine.cli;

/**
 * The smallest JSON writer that will do.
 *
 * <p>Hand-rolled because the project deliberately carries no JSON dependency. Writing
 * is the safe direction — every string goes through {@link #escape}, and the structure
 * is fixed by the code rather than by input.
 */
final class Json {

    private Json() {
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    static String string(String text) {
        return "\"" + escape(text) + "\"";
    }

    /** Finite doubles only; NaN and infinity are not valid JSON numbers. */
    static String number(double value) {
        if (!Double.isFinite(value)) {
            return "null";
        }
        return Double.toString(value);
    }
}
