package org.mavai.punit.decl.spi;

import java.util.Map;
import org.mavai.outcome.Outcome;

/**
 * A service definition resolved to a runnable configuration: the
 * per-sample invocation plus the resolved configuration's provenance
 * projection. One invocation, one request — the family's
 * sampling-independence rule binds implementations: no retries, no
 * caching, no request sophistication.
 */
public interface ConfiguredService {

    /**
     * Invokes the service once. An anticipated bad response travels
     * back as the response (or an {@link Outcome} failure) for the
     * criteria to judge; only genuine defects throw.
     */
    Outcome<String> invoke(Object input);

    /**
     * The resolved configuration as covariate values — every parameter
     * as actually used, joining run and baseline provenance.
     */
    default Map<String, String> configurationCovariates() {
        return Map.of();
    }
}
