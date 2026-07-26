package org.mavai.punit.decl.internal.run;

import java.util.Map;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * A test-scope built-in service type, registered via the test
 * classpath's {@code META-INF/services} — exercises the ServiceLoader
 * discovery route punit-lm will use, and the built-in-name shadowing
 * refusal.
 */
public class EchoServiceType implements ServiceType {

    @Override
    public String name() {
        return "echo-model";
    }

    @Override
    public ConfiguredService configure(String serviceName, Map<String, Object> configuration) {
        if (!(configuration.get("prefix") instanceof String prefix)) {
            throw new ContractConfigurationException(
                    "service '" + serviceName + "': `prefix:` is required and must be a string");
        }
        return new ConfiguredService() {
            @Override
            public Outcome<String> invoke(Object input) {
                return Outcome.ok(prefix + " " + input);
            }

            @Override
            public Map<String, String> configurationCovariates() {
                return Map.of("serviceType", "echo-model", "prefix", prefix);
            }
        };
    }
}
