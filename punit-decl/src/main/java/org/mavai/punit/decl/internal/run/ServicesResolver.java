package org.mavai.punit.decl.internal.run;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.ServiceEntry;
import org.mavai.punit.decl.internal.model.ServicesDeclaration;
import org.mavai.punit.decl.internal.parser.ServicesParser;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * Service definitions at run time: discovery of the conventional
 * {@code mavai-services.yaml} (beside the contract files in the test
 * class's resource package, then the project root; an explicit path
 * overrides), the service-type registry (built-ins via
 * {@link ServiceLoader}, user types from the bindings class — separate
 * namespaces, built-in names unshadowable), configuration validation
 * through each type, and the exploration grid's duplicate-point
 * refusal over resolved covariate values.
 */
final class ServicesResolver {

    private final Map<String, ServiceType> types = new LinkedHashMap<>();
    private final Map<String, ConfiguredService> configured = new LinkedHashMap<>();

    private ServicesResolver() {}

    static ServicesResolver resolve(Class<?> caller, Path explicitFile, BindingsRegistry registry) {
        ServicesResolver resolver = new ServicesResolver();
        for (ServiceType builtIn : ServiceLoader.load(ServiceType.class)) {
            resolver.types.put(builtIn.name(), builtIn);
        }
        for (Map.Entry<String, java.lang.reflect.Method> factory : registry.factories().entrySet()) {
            if (resolver.types.containsKey(factory.getKey())) {
                throw new ContractConfigurationException(
                        "@BindingFactory(\"" + factory.getKey() + "\") in "
                                + registry.bindingsClass().getSimpleName() + " shadows the "
                                + "built-in type of that name — built-in type names cannot be "
                                + "re-registered");
            }
            resolver.types.put(factory.getKey(),
                    new FactoryType(factory.getKey(), factory.getValue(), registry));
        }
        ServicesDeclaration declaration = discover(caller, explicitFile);
        if (declaration != null) {
            for (ServiceEntry entry : declaration.services().values()) {
                resolver.configure(entry);
            }
        }
        return resolver;
    }

    /** The configured service a {@code service:} key resolves to, or {@code null}. */
    ConfiguredService lookup(String serviceName) {
        return configured.get(serviceName);
    }

    String registeredTypeNames() {
        return types.isEmpty() ? "none registered" : String.join(", ", new TreeMap<>(types).keySet());
    }

    private void configure(ServiceEntry entry) {
        ServiceType type = types.get(entry.type());
        if (type == null) {
            throw new ContractConfigurationException(
                    "service '" + entry.name() + "': unknown `type: " + entry.type() + "` — "
                            + "registered types: " + registeredTypeNames() + " (built-in types "
                            + "ship via their module; user types are registered in the bindings "
                            + "class with @BindingFactory(\"" + entry.type() + "\"))");
        }
        configured.put(entry.name(), type.configure(entry.name(), entry.configuration()));
        validateExplorations(entry, type);
    }

    /**
     * The grid's duplicate-point refusal: each exploration entry is the
     * baseline with its keys replaced, and two entries resolving to the
     * same covariate point (or restating the baseline) are refused —
     * one population, one grid point.
     */
    private void validateExplorations(ServiceEntry entry, ServiceType type) {
        if (entry.explorations().isEmpty()) {
            return;
        }
        Map<Map<String, String>, String> seen = new LinkedHashMap<>();
        seen.put(type.configure(entry.name(), entry.configuration()).configurationCovariates(),
                "the baseline `configuration:`");
        int index = 0;
        for (Map<String, Object> deltas : entry.explorations()) {
            index++;
            Map<String, Object> merged = new LinkedHashMap<>(entry.configuration());
            merged.putAll(deltas);
            Map<String, String> point =
                    type.configure(entry.name(), merged).configurationCovariates();
            String previous = seen.putIfAbsent(point, "exploration entry " + index);
            if (previous != null) {
                throw new ContractConfigurationException(
                        "service '" + entry.name() + "': exploration entry " + index
                                + " resolves to the same configuration as " + previous
                                + " — two grid entries for one population; each entry must "
                                + "resolve to a distinct covariate point");
            }
        }
    }

    // ── Discovery ─────────────────────────────────────────────────

    private static ServicesDeclaration discover(Class<?> caller, Path explicitFile) {
        if (explicitFile != null) {
            if (!Files.isRegularFile(explicitFile)) {
                throw new ContractConfigurationException(
                        "the services file named via .services(...) does not exist: " + explicitFile);
            }
            return ServicesParser.load(explicitFile);
        }
        String resource = caller.getPackageName().replace('.', '/') + "/"
                + ServicesDeclaration.CONVENTIONAL_FILENAME;
        URL packaged = caller.getClassLoader().getResource(resource);
        if (packaged != null) {
            try (InputStream in = packaged.openStream()) {
                return ServicesParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw new ContractConfigurationException(
                        "cannot read services file " + packaged + ": " + error.getMessage(), error);
            }
        }
        Path projectRoot = Path.of(ServicesDeclaration.CONVENTIONAL_FILENAME);
        if (Files.isRegularFile(projectRoot)) {
            return ServicesParser.load(projectRoot);
        }
        return null;
    }
}
