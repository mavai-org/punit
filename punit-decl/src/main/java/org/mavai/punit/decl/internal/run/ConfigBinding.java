package org.mavai.punit.decl.internal.run;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.mavai.punit.decl.ContractConfigurationException;

/**
 * The factory-signature-is-the-schema rule, shared by every registry
 * that binds a kebab-case configuration mapping onto a method's
 * camelCase parameter list — service-type factories and stepper
 * factories alike. A misfit is a load-time refusal carrying the
 * rendered signature.
 */
final class ConfigBinding {

    private ConfigBinding() {}

    /** Binds the configuration onto the method's parameters, in slot order. */
    static Object[] bind(String context, String name, Method method, Map<String, Object> configuration) {
        Parameter[] parameters = method.getParameters();
        Map<String, Parameter> byKey = new LinkedHashMap<>();
        for (Parameter parameter : parameters) {
            byKey.put(kebabCase(parameter.getName()), parameter);
        }
        for (String key : configuration.keySet()) {
            if (!byKey.containsKey(key)) {
                throw misfit(context, name, method, "unknown configuration key `" + key + ":`");
            }
        }
        Object[] arguments = new Object[parameters.length];
        int index = 0;
        for (Map.Entry<String, Parameter> slot : byKey.entrySet()) {
            if (!configuration.containsKey(slot.getKey())) {
                throw misfit(context, name, method, "missing configuration key `" + slot.getKey() + ":`");
            }
            Object value = configuration.get(slot.getKey());
            Object converted = convert(value, slot.getValue().getType());
            if (converted == null) {
                throw misfit(context, name, method, "`" + slot.getKey() + ":` must be a "
                        + slot.getValue().getType().getSimpleName() + ", got "
                        + (value == null ? "null" : value.getClass().getSimpleName()));
            }
            arguments[index++] = converted;
        }
        return arguments;
    }

    private static ContractConfigurationException misfit(
            String context, String name, Method method, String problem) {
        return new ContractConfigurationException(
                context + ": " + problem + " — the configuration schema is the factory's "
                        + "signature: " + renderedSignature(name, method));
    }

    /** The signature as authors read it: {@code name(kebab-key: Type, ...)}. */
    static String renderedSignature(String name, Method method) {
        return name + "(" + Arrays.stream(method.getParameters())
                .map(parameter -> kebabCase(parameter.getName()) + ": "
                        + parameter.getType().getSimpleName())
                .collect(Collectors.joining(", ")) + ")";
    }

    /** camelCase parameter names as kebab-case file keys: {@code topP} → {@code top-p}. */
    static String kebabCase(String parameterName) {
        StringBuilder kebab = new StringBuilder(parameterName.length() + 4);
        for (int i = 0; i < parameterName.length(); i++) {
            char c = parameterName.charAt(i);
            if (Character.isUpperCase(c)) {
                kebab.append('-').append(Character.toLowerCase(c));
            } else {
                kebab.append(c);
            }
        }
        return kebab.toString();
    }

    /** The value as the parameter's scalar type, or {@code null} on a misfit. */
    static Object convert(Object value, Class<?> type) {
        if (type == String.class) {
            return value instanceof String ? value : null;
        }
        if (type == int.class || type == Integer.class) {
            return value instanceof Integer ? value : null;
        }
        if (type == long.class || type == Long.class) {
            if (value instanceof Long) {
                return value;
            }
            return value instanceof Integer whole ? whole.longValue() : null;
        }
        if (type == double.class || type == Double.class) {
            if (value instanceof Double) {
                return value;
            }
            return value instanceof Number number && !(value instanceof Boolean)
                    ? number.doubleValue() : null;
        }
        if (type == boolean.class || type == Boolean.class) {
            return value instanceof Boolean ? value : null;
        }
        return null;
    }
}
