package org.mavai.punit.decl.internal.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict JSON-value equality for set-form elements, as a type-tagged
 * key: numbers compare numerically (decimal), strings exactly, booleans
 * and null by identity — a number never equals its string spelling, and
 * JSON {@code true} never equals {@code 1}. The one element-identity
 * rule for every set form, shared by the parser (member-list
 * normalisation) and the compiler (selection judgement).
 */
public final class SetElements {

    private SetElements() {}

    /** The element under the strict JSON-value rule, as a comparable key. */
    public static Object key(Object value) {
        if (value instanceof Boolean) {
            return List.of("bool", value);
        }
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            return List.of("num", NumericValue.of(value).stripTrailingZeros());
        }
        return List.of("str", String.valueOf(value));
    }

    /** The values as a multiset over element keys (duplicates significant). */
    public static Map<Object, Long> multiset(List<?> values) {
        Map<Object, Long> counts = new HashMap<>();
        for (Object value : values) {
            counts.merge(key(value), 1L, Long::sum);
        }
        return counts;
    }
}
