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

    /**
     * The two tiers of the family's capability rule: a test, measure,
     * or optimize run refuses a configuration its resolved adapter
     * cannot honour ({@code STRICT}); an explore run degrades it per
     * point with an announced note ({@code EXPLORE}), so mixed-provider
     * grids run.
     */
    enum Posture { STRICT, EXPLORE }

    private final Map<String, ServiceType> types = new LinkedHashMap<>();
    private final Map<String, ConfiguredService> configured = new LinkedHashMap<>();
    private final Map<String, ServiceEntry> entries = new LinkedHashMap<>();
    private final Posture posture;

    private ServicesResolver(Posture posture) {
        this.posture = posture;
    }

    static ServicesResolver resolve(
            Class<?> caller, Path explicitFile, BindingsRegistry registry, Posture posture) {
        ServicesResolver resolver = new ServicesResolver(posture);
        // The defining classloader, not the thread-context one: under a
        // classloader-isolated Gradle task (mavaiCheck) the context
        // loader is the build's and would discover nothing.
        for (ServiceType builtIn
                : ServiceLoader.load(ServiceType.class, ServiceType.class.getClassLoader())) {
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
            Path baseDir = declaration.sourcePath() != null
                    ? declaration.sourcePath().getParent()
                    : null;
            for (ServiceEntry entry : declaration.services().values()) {
                resolver.configure(entry, declaration.roots(), baseDir);
            }
            // Usage coherence is a whole-file property: a declared root
            // referenced by nothing is a dead declaration.
            declaration.roots().refuseDead();
        }
        return resolver;
    }

    /** The configured service a {@code service:} key resolves to, or {@code null}. */
    ConfiguredService lookup(String serviceName) {
        return configured.get(serviceName);
    }

    /** Whether a definition exists for the service name. */
    boolean isDefined(String serviceName) {
        return entries.containsKey(serviceName);
    }

    /**
     * The definition's exploration grid as resolved configuration
     * records: the baseline first, then each delta entry merged over
     * it — {baseline} ∪ explorations, one point per population.
     */
    java.util.List<Map<String, Object>> explorationGrid(String serviceName) {
        ServiceEntry entry = entries.get(serviceName);
        java.util.List<Map<String, Object>> grid = new java.util.ArrayList<>();
        grid.add(new LinkedHashMap<>(entry.configuration()));
        for (Map<String, Object> deltas : entry.explorations()) {
            Map<String, Object> merged = new LinkedHashMap<>(entry.configuration());
            merged.putAll(deltas);
            grid.add(merged);
        }
        return grid;
    }

    /** The definition's baseline configuration record. */
    Map<String, Object> baselineConfiguration(String serviceName) {
        return new LinkedHashMap<>(entries.get(serviceName).configuration());
    }

    /** The definition's declared optimizations. */
    java.util.List<org.mavai.punit.decl.internal.model.OptimizationDeclaration> optimizations(
            String serviceName) {
        return entries.get(serviceName).optimizations();
    }

    /** Configures the named service at one resolved grid point, strictly. */
    ConfiguredService configurePoint(String serviceName, Map<String, Object> configuration) {
        ServiceEntry entry = entries.get(serviceName);
        return types.get(entry.type()).configure(serviceName, configuration);
    }

    /**
     * Configures one exploration grid point on the lenient tier: the
     * service that actually runs plus the type's degradation note.
     */
    ServiceType.ExplorePoint explorePoint(String serviceName, Map<String, Object> configuration) {
        ServiceEntry entry = entries.get(serviceName);
        return types.get(entry.type()).explorePoint(serviceName, configuration);
    }

    String registeredTypeNames() {
        return types.isEmpty() ? "none registered" : String.join(", ", new TreeMap<>(types).keySet());
    }

    private void configure(ServiceEntry entry,
            org.mavai.punit.decl.internal.parser.Roots roots, Path baseDir) {
        ServiceType type = types.get(entry.type());
        if (type == null) {
            throw new ContractConfigurationException(
                    "service '" + entry.name() + "': unknown `type: " + entry.type() + "` — "
                            + "registered types: " + registeredTypeNames() + " (built-in types "
                            + "ship via their module; user types are registered in the bindings "
                            + "class with @BindingFactory(\"" + entry.type() + "\"))");
        }
        entry = withResolvedFileValues(entry, type, roots, baseDir);
        configured.put(entry.name(), posture == Posture.STRICT
                ? type.configure(entry.name(), entry.configuration())
                : type.explorePoint(entry.name(), entry.configuration()).service());
        entries.put(entry.name(), entry);
        validateExplorations(entry, type);
        validateOptimizations(entry, type);
    }

    /**
     * The {@code {file: <path>}} values a type admits, resolved to plain
     * strings — root references included, the file read once at load,
     * decoded UTF-8 — before the type parses the configuration, so
     * covariates, fingerprints, and steppers see the string exactly as
     * if written inline (resolved-as-used). Baseline configuration and
     * exploration deltas alike.
     */
    private static ServiceEntry withResolvedFileValues(ServiceEntry entry, ServiceType type,
            org.mavai.punit.decl.internal.parser.Roots roots, Path baseDir) {
        java.util.List<String> keys = type.fileValueKeys();
        if (keys.isEmpty()) {
            return entry;
        }
        Map<String, Object> configuration = resolvedFileValues(
                entry.name(), entry.configuration(), keys, "configuration", roots, baseDir);
        java.util.List<Map<String, Object>> explorations = new java.util.ArrayList<>();
        int index = 0;
        for (Map<String, Object> deltas : entry.explorations()) {
            index++;
            explorations.add(resolvedFileValues(
                    entry.name(), deltas, keys, "exploration entry " + index, roots, baseDir));
        }
        return new ServiceEntry(
                entry.name(), entry.type(), configuration, explorations, entry.optimizations());
    }

    private static Map<String, Object> resolvedFileValues(String name,
            Map<String, Object> mapping, java.util.List<String> keys, String where,
            org.mavai.punit.decl.internal.parser.Roots roots, Path baseDir) {
        Map<String, Object> resolved = new LinkedHashMap<>(mapping);
        for (String key : keys) {
            Object value = resolved.get(key);
            if (!(value instanceof Map<?, ?> reference)) {
                continue;
            }
            String location = "service '" + name + "': " + where + ": `" + key + ":`";
            if (reference.size() != 1
                    || !(reference.get("file") instanceof String rawPath)
                    || rawPath.isEmpty()) {
                throw new ContractConfigurationException(location + " file form is "
                        + "`{file: <path>}` with a non-empty path and no other key");
            }
            Path rooted = roots.resolve(rawPath, location);
            Path file;
            if (rooted != null) {
                file = rooted;
            } else if (baseDir == null) {
                throw new ContractConfigurationException(location + ": a `{file:}` value "
                        + "needs a services file loaded from disk to resolve '" + rawPath
                        + "' relative to it");
            } else {
                file = baseDir.resolve(rawPath).normalize();
            }
            byte[] data;
            try {
                data = java.nio.file.Files.readAllBytes(file);
            } catch (java.io.IOException error) {
                throw new ContractConfigurationException(location + ": cannot read file "
                        + file + ": " + error.getMessage(), error);
            }
            try {
                resolved.put(key, java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                        .decode(java.nio.ByteBuffer.wrap(data)).toString());
            } catch (java.nio.charset.CharacterCodingException error) {
                throw new ContractConfigurationException(location + ": file " + file
                        + " is not valid UTF-8 text: " + error, error);
            }
        }
        return resolved;
    }

    /**
     * The inert-overlay refusal: an {@code initial:} that resolves to
     * the baseline's own covariate point changes nothing — iteration 0
     * starts from the baseline by default, so restating it is at best a
     * no-op and at worst a misread of what the overlay does.
     */
    private void validateOptimizations(ServiceEntry entry, ServiceType type) {
        if (entry.optimizations().isEmpty()) {
            return;
        }
        Map<String, String> baseline = type.explorePoint(entry.name(), entry.configuration())
                .service().configurationCovariates();
        int index = 0;
        for (org.mavai.punit.decl.internal.model.OptimizationDeclaration optimization
                : entry.optimizations()) {
            index++;
            if (optimization.initial().isEmpty()) {
                continue;
            }
            Map<String, Object> merged = new LinkedHashMap<>(entry.configuration());
            merged.putAll(optimization.initial());
            Map<String, String> point = type.explorePoint(entry.name(), merged)
                    .service().configurationCovariates();
            if (point.equals(baseline)) {
                throw new ContractConfigurationException(
                        "service '" + entry.name() + "': optimization entry " + index
                                + ": `initial:` merely restates the baseline `configuration:` — "
                                + "iteration 0 starts from the baseline by default; omit the "
                                + "overlay, or state a different starting point");
            }
        }
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
        // Grid points project their covariates through the lenient tier
        // regardless of posture: the grid belongs to explore, where the
        // configuration that actually runs — degraded to what each
        // point's provider honours — is what is fingerprinted, and a
        // capability misfit in a grid entry must not block a test or
        // measurement that never consults the grid.
        Map<Map<String, String>, String> seen = new LinkedHashMap<>();
        seen.put(type.explorePoint(entry.name(), entry.configuration())
                        .service().configurationCovariates(),
                "the baseline `configuration:`");
        int index = 0;
        for (Map<String, Object> deltas : entry.explorations()) {
            index++;
            Map<String, Object> merged = new LinkedHashMap<>(entry.configuration());
            merged.putAll(deltas);
            Map<String, String> point =
                    type.explorePoint(entry.name(), merged).service().configurationCovariates();
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
