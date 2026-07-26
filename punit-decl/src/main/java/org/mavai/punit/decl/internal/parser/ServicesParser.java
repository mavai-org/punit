package org.mavai.punit.decl.internal.parser;

import static org.mavai.punit.decl.internal.parser.Yaml.fail;
import static org.mavai.punit.decl.internal.parser.Yaml.requireMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.OptimizationDeclaration;
import org.mavai.punit.decl.internal.model.OptimizationDeclaration.Objective;
import org.mavai.punit.decl.internal.model.ServiceEntry;
import org.mavai.punit.decl.internal.model.ServicesDeclaration;

/**
 * The {@code mavai-services/1} parser: reads a service-definition
 * file's text into a structurally validated {@link ServicesDeclaration}.
 * Type-registry resolution — and the configuration's validation against
 * the resolved type's schema — happens at instantiation, where the
 * registries exist; everything checkable from the document alone is
 * checked here.
 */
public final class ServicesParser {

    private static final Set<String> DEFINITION_KEYS =
            Set.of("type", "configuration", "explorations", "optimizations");
    private static final Set<String> OPTIMIZATION_KEYS = Set.of(
            "id", "stepper", "stepper-config", "scorer", "objective",
            "max-iterations", "no-improvement-window", "initial");
    private static final Pattern ID_SHAPE = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final String DEFAULT_SCORER = "pass-rate";

    private ServicesParser() {}

    /** Parses a services file's text with no source location. */
    public static ServicesDeclaration parse(String text) {
        return parse(text, null);
    }

    /**
     * Parses and structurally validates a services file's text.
     *
     * @throws ContractConfigurationException on any malformation —
     *     always before any invocation
     */
    public static ServicesDeclaration parse(String text, Path sourcePath) {
        Map<String, Object> data = requireMapping(Yaml.parse(text, "services file"), "the services file");
        if (!ServicesDeclaration.FORMAT_IDENTIFIER.equals(data.get("format"))) {
            throw fail("`format:` must be '" + ServicesDeclaration.FORMAT_IDENTIFIER + "', got '"
                    + data.get("format") + "'");
        }
        for (String key : data.keySet()) {
            if (!key.equals("format") && !key.equals("services")) {
                throw fail("the services file has unknown key `" + key + ":` — it holds "
                        + "`format:` and the `services:` block, nothing else");
            }
        }
        Object servicesValue = data.get("services");
        if (!(servicesValue instanceof Map<?, ?> raw) || raw.isEmpty()) {
            throw fail("`services:` must be a non-empty mapping");
        }
        Map<String, ServiceEntry> services = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?>)) {
                throw fail("service '" + name + "' must be a mapping");
            }
            services.put(name, definition(name, requireMapping(entry.getValue(), "service '" + name + "'")));
        }
        return new ServicesDeclaration(services, sourcePath);
    }

    /** Reads and parses a services file from disk. */
    public static ServicesDeclaration load(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw fail("cannot read services file " + path + ": " + error.getMessage(), error);
        }
        return parse(text, path);
    }

    private static ServiceEntry definition(String name, Map<String, Object> data) {
        for (String key : data.keySet()) {
            if (!DEFINITION_KEYS.contains(key)) {
                throw fail("service '" + name + "': unknown key `" + key + ":` — a definition "
                        + "holds `type:` and its `configuration:` block (plus `explorations:` "
                        + "and `optimizations:`); every covariate value lives inside "
                        + "`configuration:`, uniformly");
            }
        }
        if (!(data.get("type") instanceof String type) || type.isEmpty()) {
            throw fail("service '" + name + "': `type:` names the service implementation to "
                    + "instantiate — a built-in type or one registered in the bindings class");
        }
        if (!(data.get("configuration") instanceof Map<?, ?>)) {
            throw fail("service '" + name + "': a `configuration:` block is required — the "
                    + "complete set of parameter values the service runs under");
        }
        Map<String, Object> configuration =
                requireMapping(data.get("configuration"), "service '" + name + "': `configuration:`");

        List<Map<String, Object>> explorations = new ArrayList<>();
        if (data.containsKey("explorations")) {
            Object entries = data.get("explorations");
            if (!(entries instanceof List<?> list) || list.isEmpty()) {
                throw fail("service '" + name + "': `explorations:` must be a non-empty list of "
                        + "entries, each declaring the configuration values it replaces");
            }
            int index = 0;
            for (Object entry : list) {
                index++;
                String where = "exploration entry " + index;
                if (!(entry instanceof Map<?, ?>)) {
                    throw fail("service '" + name + "': " + where + " must be a mapping of "
                            + "replacement values");
                }
                Map<String, Object> deltas = requireMapping(entry, "service '" + name + "': " + where);
                for (Map.Entry<String, Object> delta : deltas.entrySet()) {
                    if (delta.getValue() == null) {
                        throw fail("service '" + name + "': " + where + ": `" + delta.getKey()
                                + ":` declares no value — an entry states replacements; omit a "
                                + "key to keep its baseline value");
                    }
                }
                explorations.add(deltas);
            }
        }

        List<OptimizationDeclaration> optimizations = new ArrayList<>();
        if (data.containsKey("optimizations")) {
            optimizations = optimizations(name, data.get("optimizations"));
        }

        return new ServiceEntry(name, type, configuration, explorations, optimizations);
    }

    private static List<OptimizationDeclaration> optimizations(String name, Object entriesValue) {
        if (!(entriesValue instanceof List<?> entries) || entries.isEmpty()) {
            throw fail("service '" + name + "': `optimizations:` must be a non-empty list of "
                    + "entries, each declaring one optimize run");
        }
        List<OptimizationDeclaration> declarations = new ArrayList<>();
        Map<String, String> seenIds = new LinkedHashMap<>();
        int index = 0;
        for (Object entryValue : entries) {
            index++;
            String where = "optimization entry " + index;
            if (!(entryValue instanceof Map<?, ?>)) {
                throw fail("service '" + name + "': " + where + " must be a mapping");
            }
            Map<String, Object> entry = requireMapping(entryValue, "service '" + name + "': " + where);
            for (String key : entry.keySet()) {
                if (!OPTIMIZATION_KEYS.contains(key)) {
                    throw fail("service '" + name + "': " + where + " has unknown key `" + key
                            + ":` — an optimization entry accepts: "
                            + String.join(", ", OPTIMIZATION_KEYS.stream().sorted().toList()));
                }
            }

            String id = entryId(name, where, entry, entries.size() > 1);
            String previous = seenIds.get(id);
            if (previous != null) {
                throw fail("service '" + name + "': " + where + ": `id: " + id + "` is already "
                        + "used by " + previous + " — each optimization names its own run and "
                        + "artefact");
            }
            seenIds.put(id, where);

            if (!(entry.get("stepper") instanceof String stepper) || stepper.isEmpty()) {
                throw fail("service '" + name + "': " + where + ": `stepper:` is required — the "
                        + "registered name of the algorithm proposing each next configuration");
            }
            Map<String, Object> stepperConfig = new LinkedHashMap<>();
            if (entry.containsKey("stepper-config")) {
                if (!(entry.get("stepper-config") instanceof Map<?, ?>)) {
                    throw fail("service '" + name + "': " + where + ": `stepper-config:` must be "
                            + "a mapping of the stepper's factory parameters");
                }
                stepperConfig = requireMapping(entry.get("stepper-config"),
                        "service '" + name + "': " + where + ": `stepper-config:`");
            }

            String scorer = DEFAULT_SCORER;
            if (entry.containsKey("scorer")) {
                if (!(entry.get("scorer") instanceof String scorerName) || scorerName.isEmpty()) {
                    throw fail("service '" + name + "': " + where + ": `scorer:` must be a "
                            + "registered name");
                }
                scorer = scorerName;
            }

            Objective objective = Objective.MAXIMIZE;
            if (entry.containsKey("objective")) {
                objective = Objective.forKey(String.valueOf(entry.get("objective")));
                if (objective == null) {
                    throw fail("service '" + name + "': " + where + ": `objective:` must be one "
                            + "of maximize, minimize, got '" + entry.get("objective") + "'");
                }
            }

            Object maxIterationsValue = entry.get("max-iterations");
            if (maxIterationsValue == null) {
                throw fail("service '" + name + "': " + where + ": `max-iterations:` is required "
                        + "— the cap always bounds the spend");
            }
            if (!Yaml.isIntegerAtLeast(maxIterationsValue, 1)) {
                throw fail("service '" + name + "': " + where + ": `max-iterations:` must be a "
                        + "positive integer, got " + maxIterationsValue);
            }
            int maxIterations = (Integer) maxIterationsValue;

            Integer window = null;
            Object windowValue = entry.get("no-improvement-window");
            if (windowValue != null) {
                if (!Yaml.isIntegerAtLeast(windowValue, 1)) {
                    throw fail("service '" + name + "': " + where + ": `no-improvement-window:` "
                            + "must be a positive integer, got " + windowValue);
                }
                window = (Integer) windowValue;
            }

            Map<String, Object> initial = new LinkedHashMap<>();
            if (entry.containsKey("initial")) {
                if (!(entry.get("initial") instanceof Map<?, ?> overlay) || overlay.isEmpty()) {
                    throw fail("service '" + name + "': " + where + ": `initial:` must be a "
                            + "non-empty mapping of configuration values to replace for "
                            + "iteration 0 — omit it to start from the baseline");
                }
                initial = requireMapping(entry.get("initial"),
                        "service '" + name + "': " + where + ": `initial:`");
                for (Map.Entry<String, Object> value : initial.entrySet()) {
                    if (value.getValue() == null) {
                        throw fail("service '" + name + "': " + where + ": `initial:` key `"
                                + value.getKey() + ":` declares no value — the overlay states "
                                + "replacements; omit a key to keep its baseline value");
                    }
                }
            }

            declarations.add(new OptimizationDeclaration(
                    id, stepper, stepperConfig, scorer, objective, maxIterations, window, initial));
        }
        return declarations;
    }

    private static String entryId(String name, String where, Map<String, Object> entry, boolean multiple) {
        Object idValue = entry.get("id");
        if (idValue == null) {
            if (multiple) {
                throw fail("service '" + name + "': " + where + ": `id:` is required when the "
                        + "service declares more than one optimization — each entry names its "
                        + "own run and artefact");
            }
            return name;
        }
        if (!(idValue instanceof String id) || !ID_SHAPE.matcher(id).matches()) {
            throw fail("service '" + name + "': " + where + ": `id:` must be letters, digits, "
                    + "dots, underscores, or hyphens (it names the artefact file), got '"
                    + idValue + "'");
        }
        return id;
    }
}
