package org.mavai.punit.decl.internal.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed service definition: its type name, the complete baseline
 * {@code configuration:} record, and the optional exploration grid and
 * optimization entries. The type name resolves against the service-type
 * registry — and the configuration validates against the resolved
 * type's schema — at instantiation, not here.
 *
 * @param name the service name (the {@code services:} key)
 * @param type the service-type name
 * @param configuration the baseline factor record, in declaration order
 * @param explorations the exploration delta entries, each declaring
 *     only the covariates that deviate from the baseline
 * @param optimizations the declared optimize experiments
 */
public record ServiceEntry(
        String name,
        String type,
        Map<String, Object> configuration,
        List<Map<String, Object>> explorations,
        List<OptimizationDeclaration> optimizations) {

    public ServiceEntry {
        configuration = new LinkedHashMap<>(configuration);
        explorations = explorations.stream()
                .map(entry -> (Map<String, Object>) new LinkedHashMap<>(entry))
                .toList();
        optimizations = List.copyOf(optimizations);
    }

    @Override
    public Map<String, Object> configuration() {
        return Map.copyOf(configuration);
    }
}
