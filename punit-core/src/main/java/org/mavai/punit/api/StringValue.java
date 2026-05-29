package org.mavai.punit.api;

import java.util.Objects;

/** String scalar factor value. Canonical form is JSON-quoted. */
public record StringValue(String value) implements FactorValue {

    public StringValue {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String canonical() {
        return jsonQuote(value);
    }

    @Override
    public Object yamlValue() {
        return value;
    }

    static String jsonQuote(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }
}
