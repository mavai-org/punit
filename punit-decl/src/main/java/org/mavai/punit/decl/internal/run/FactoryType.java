package org.mavai.punit.decl.internal.run;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * A user-registered configurable service type: the
 * {@code @BindingFactory} method whose parameter list <em>is</em> the
 * configuration schema. Kebab-case file keys map to camelCase
 * parameters deterministically; scalar types are checked; a misfit is a
 * load-time refusal carrying the rendered signature. The factory runs
 * at contract-load time and yields the per-sample callable.
 */
final class FactoryType implements ServiceType {

    private final String name;
    private final Method factory;
    private final BindingsRegistry registry;

    FactoryType(String name, Method factory, BindingsRegistry registry) {
        this.name = name;
        this.factory = factory;
        this.registry = registry;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ConfiguredService configure(String serviceName, Map<String, Object> configuration) {
        Parameter[] parameters = factory.getParameters();
        Map<String, Parameter> byKey = new LinkedHashMap<>();
        for (Parameter parameter : parameters) {
            byKey.put(kebabCase(parameter.getName()), parameter);
        }
        for (String key : configuration.keySet()) {
            if (!byKey.containsKey(key)) {
                throw misfit(serviceName, "unknown configuration key `" + key + ":`");
            }
        }
        Object[] arguments = new Object[parameters.length];
        int index = 0;
        for (Map.Entry<String, Parameter> slot : byKey.entrySet()) {
            if (!configuration.containsKey(slot.getKey())) {
                throw misfit(serviceName, "missing configuration key `" + slot.getKey() + ":`");
            }
            Object value = configuration.get(slot.getKey());
            Object converted = convert(value, slot.getValue().getType());
            if (converted == null) {
                throw misfit(serviceName, "`" + slot.getKey() + ":` must be a "
                        + slot.getValue().getType().getSimpleName() + ", got "
                        + (value == null ? "null" : value.getClass().getSimpleName()));
            }
            arguments[index++] = converted;
        }
        Object callable = registry.invoke(factory, arguments);
        BindingsRegistry.Invoker invoker = registry.adaptCallable(serviceName, callable);
        Map<String, String> covariates = new LinkedHashMap<>();
        covariates.put("serviceType", name);
        for (Map.Entry<String, Object> entry : configuration.entrySet()) {
            covariates.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new ConfiguredService() {
            @Override
            public org.mavai.outcome.Outcome<String> invoke(Object input) {
                return invoker.invoke(input);
            }

            @Override
            public Map<String, String> configurationCovariates() {
                return covariates;
            }
        };
    }

    private ContractConfigurationException misfit(String serviceName, String problem) {
        String signature = name + "(" + java.util.Arrays.stream(factory.getParameters())
                .map(parameter -> kebabCase(parameter.getName()) + ": "
                        + parameter.getType().getSimpleName())
                .collect(Collectors.joining(", ")) + ")";
        return new ContractConfigurationException(
                "service '" + serviceName + "': " + problem + " — the type's configuration "
                        + "schema is its factory's signature: " + signature);
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
    private static Object convert(Object value, Class<?> type) {
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
