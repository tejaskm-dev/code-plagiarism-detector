package com.integrityengine.api;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer.
 *
 * <p>Deliberately duplicated from the CLI's equivalent rather than shared. The project
 * carries no JSON dependency, and {@code cli} and {@code api} are independent entry
 * points — neither should have to import the other to emit a response body. The
 * duplication is 40 lines of escaping with no behaviour to drift.
 */
final class JsonOut {

    private JsonOut() {
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
        return text == null ? "null" : "\"" + escape(text) + "\"";
    }

    /** JSON has no NaN or Infinity; both become null rather than invalid output. */
    static String number(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
    }

    static String object(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(string(entry.getKey())).append(':').append(entry.getValue());
        }
        return out.append('}').toString();
    }

    static String array(List<String> rendered) {
        return "[" + String.join(",", rendered) + "]";
    }

    /** A uniform error body, so the front end never has to guess at a failure shape. */
    static String error(String code, String message) {
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("error", string(code));
        fields.put("message", string(message));
        return object(fields);
    }
}
