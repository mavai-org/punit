package org.mavai.punit.decl.internal.run;

import java.util.LinkedHashMap;
import java.util.Map;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * A test-scope built-in exercising the two-tier capability rule: the
 * strict tier refuses a configuration declaring {@code boost: true}
 * (this "provider" cannot honour it), the lenient tier drops the key
 * and states it in the note — the seam punit-lm's language-model type
 * implements for real capabilities.
 */
public class DegradingServiceType implements ServiceType {

    @Override
    public String name() {
        return "degrading-model";
    }

    @Override
    public ConfiguredService configure(String serviceName, Map<String, Object> configuration) {
        if (Boolean.TRUE.equals(configuration.get("boost"))) {
            throw new ContractConfigurationException(
                    "service '" + serviceName + "': this reader cannot honour `boost: true`, "
                            + "and silently dropping it would change what is being measured");
        }
        return service(configuration);
    }

    @Override
    public ExplorePoint explorePoint(String serviceName, Map<String, Object> configuration) {
        if (Boolean.TRUE.equals(configuration.get("boost"))) {
            Map<String, Object> degraded = new LinkedHashMap<>(configuration);
            degraded.remove("boost");
            return new ExplorePoint(service(degraded),
                    "`boost:` is not honoured — it is not sent for this configuration");
        }
        return new ExplorePoint(service(configuration), null);
    }

    private ConfiguredService service(Map<String, Object> configuration) {
        return new ConfiguredService() {
            @Override
            public Outcome<String> invoke(Object input) {
                return Outcome.ok("routed: " + input);
            }

            @Override
            public Map<String, String> configurationCovariates() {
                Map<String, String> covariates = new LinkedHashMap<>();
                covariates.put("serviceType", "degrading-model");
                configuration.forEach((key, value) ->
                        covariates.put(key, String.valueOf(value)));
                return covariates;
            }
        };
    }
}
