package org.mavai.punit.decl.internal.run;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
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
        Object[] arguments = ConfigBinding.bind(
                "service '" + serviceName + "'", name, factory, configuration);
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

}
