package org.mavai.punit.decl.internal.parser;

import java.util.LinkedHashMap;
import java.util.Map;
import org.mavai.punit.decl.ContractConfigurationException;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/**
 * YAML parsing and shape helpers shared by the two format parsers.
 * snakeyaml-engine implements YAML 1.2 with safe construction — scalars,
 * mappings, and sequences only, never tag-driven object instantiation.
 */
final class Yaml {

    private Yaml() {}

    /** Parses YAML text; a malformed document is a load-time refusal. */
    static Object parse(String text, String what) {
        try {
            LoadSettings settings = LoadSettings.builder().build();
            return new Load(settings).loadFromString(text);
        } catch (YamlEngineException error) {
            throw fail("the " + what + " is not well-formed YAML: " + error.getMessage(), error);
        }
    }

    /** The value as a string-keyed mapping, or a refusal naming its place. */
    static Map<String, Object> requireMapping(Object value, String where) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw fail(where + " must be a mapping");
        }
        Map<String, Object> mapping = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            mapping.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return mapping;
    }

    /** The key's value as a non-empty string, or a refusal naming the key. */
    static String requireString(Map<String, Object> data, String key) {
        if (!(data.get(key) instanceof String value) || value.isEmpty()) {
            throw fail("`" + key + ":` must be a non-empty string");
        }
        return value;
    }

    /** Whether the value is a rate strictly inside the unit interval. */
    static boolean isUnitIntervalRate(Object value) {
        return value instanceof Number number
                && !(value instanceof Boolean)
                && number.doubleValue() > 0
                && number.doubleValue() < 1;
    }

    /** Whether the value is a whole number of at least {@code minimum}. */
    static boolean isIntegerAtLeast(Object value, int minimum) {
        return value instanceof Integer integer && integer >= minimum;
    }

    /** A bounded rendering of an author-supplied value for a refusal message. */
    static String display(Object value) {
        String text = value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
        return text.length() <= 60 ? text : text.substring(0, 57) + "...";
    }

    static ContractConfigurationException fail(String message) {
        return new ContractConfigurationException(message);
    }

    static ContractConfigurationException fail(String message, Throwable cause) {
        return new ContractConfigurationException(message, cause);
    }
}
