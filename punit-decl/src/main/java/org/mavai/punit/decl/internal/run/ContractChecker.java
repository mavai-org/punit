package org.mavai.punit.decl.internal.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.model.InputDeclaration;
import org.mavai.punit.decl.internal.parser.ContractParser;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.internal.engine.explore.ExploreOutputWriter;

/**
 * The check verb: validate every load-time join with zero samples —
 * the authoring loop's compile step. Loads the contract, resolves the
 * bindings class and the services file, and runs every load-time join
 * (views, selection expressions, criteria, service configuration, the
 * per-input admission gate) exactly as a run would, without invoking
 * anything. A missing baseline is not checked: absence is a run-time
 * fact, not a configuration defect.
 *
 * <p>Returns one line per validated fact; the first failing join
 * throws with the same refusal a run would give. The stale-artefact
 * advisory names exploration artefacts no current grid point writes —
 * grid contraction within unchanged swept keys strands points, and the
 * pre-flight names what it sees, deleting nothing (the artefact tree
 * is regenerable working output, operator-owned).
 */
public final class ContractChecker {

    private ContractChecker() {}

    /** The empty-registry stand-in when no bindings class exists. */
    static final class NoBindings {}

    public static List<String> check(Path contractFile, String bindingsClassName, Path explorationsDir) {
        String text;
        try {
            text = Files.readString(contractFile);
        } catch (IOException error) {
            throw new ContractConfigurationException(
                    "cannot read contract file " + contractFile + ": " + error.getMessage(),
                    error);
        }
        ContractDeclaration declaration = ContractParser.parse(text, contractFile);
        List<String> facts = new ArrayList<>();
        facts.add("contract '" + declaration.contract() + "': "
                + declaration.criteria().size() + " criteria, "
                + declaration.inputs().size() + " inputs");

        BindingsRegistry registry = registry(bindingsClassName, facts);
        StockViews views = new StockViews(declaration, registry);
        CriteriaCompiler compiler = new CriteriaCompiler(
                declaration, views, new AtomicReference<>(), Map.of(), null, registry);
        compiler.validateEagerly();
        compiler.compile();

        Path servicesFile = contractFile.getParent() != null
                && Files.isRegularFile(contractFile.getParent().resolve("mavai-services.yaml"))
                ? contractFile.getParent().resolve("mavai-services.yaml")
                : null;
        // The facade class anchors conventional discovery: its package
        // carries no resources, so with no file beside the contract the
        // lookup falls through to the project root — nearest first,
        // never this module's own internals.
        ServicesResolver services = ServicesResolver.resolve(
                org.mavai.punit.decl.ContractCheck.class, servicesFile, registry,
                ServicesResolver.Posture.STRICT);
        String serviceName = declaration.service();
        if (services.isDefined(serviceName)) {
            ConfiguredService configured = services.lookup(serviceName);
            for (InputDeclaration input : declaration.inputs()) {
                configured.admit(input.value());
            }
            facts.add("service '" + serviceName + "': definition configured strictly, "
                    + "every input admitted");
            List<Map<String, Object>> grid = services.explorationGrid(serviceName);
            if (grid.size() > 1) {
                int entries = grid.size() - 1;
                facts.add("exploration grid: " + entries
                        + (entries == 1 ? " entry" : " entries") + " constructed and joined");
            }
            // Explore artefacts land under the contract's own id — the
            // declarative explore contract is named by the contract file.
            facts.addAll(staleArtefactAdvisory(declaration.contract(), grid, explorationsDir));
            int optimizations = services.optimizations(serviceName).size();
            if (optimizations > 0) {
                facts.add("optimizations: " + optimizations
                        + (optimizations == 1 ? " entry" : " entries") + " validated");
            }
        } else if (registry.hasBinding(serviceName)) {
            facts.add("service '" + serviceName + "': bare code binding resolved");
        } else {
            throw new ContractConfigurationException(
                    "service '" + serviceName + "' resolves to nothing — no "
                            + "mavai-services.yaml definition and no @Binding of that name "
                            + "(registered types: " + services.registeredTypeNames() + ")");
        }
        return List.copyOf(facts);
    }

    private static BindingsRegistry registry(String bindingsClassName, List<String> facts) {
        if (bindingsClassName == null) {
            return BindingsRegistry.of(NoBindings.class);
        }
        try {
            return BindingsRegistry.of(Class.forName(
                    bindingsClassName, false, ContractChecker.class.getClassLoader()));
        } catch (ClassNotFoundException absent) {
            // The conventional name is a convention, not a requirement:
            // a contract whose service lives entirely in the services
            // file needs no bindings class at all.
            facts.add("no bindings class '" + bindingsClassName
                    + "' — code registrations unavailable to this check");
            return BindingsRegistry.of(NoBindings.class);
        }
    }

    /**
     * Names artefacts in the active experiment directory that no
     * current grid point writes, plus a multi-vintage note — advisory
     * only, never deletion.
     */
    private static List<String> staleArtefactAdvisory(String serviceContractId,
            List<Map<String, Object>> grid, Path explorationsDir) {
        ExploreOutputWriter writer = new ExploreOutputWriter();
        List<FactorBundle> bundles = grid.stream().map(FactorBundle::of).toList();
        Path active = explorationsDir
                .resolve(serviceContractId)
                .resolve(writer.experimentDirectory(bundles));
        if (!Files.isDirectory(active)) {
            return List.of();
        }
        Set<String> currentStems = new LinkedHashSet<>();
        for (FactorBundle bundle : bundles) {
            currentStems.add(writer.filenameFor(bundle));
        }
        List<String> advisories = new ArrayList<>();
        Set<String> vintages = new TreeSet<>();
        try (var artefacts = Files.list(active)) {
            for (Path artefact : artefacts.sorted().toList()) {
                String name = artefact.getFileName().toString();
                if (!name.endsWith(".yaml")) {
                    continue;
                }
                String vintage = generatedDate(artefact);
                if (vintage != null) {
                    vintages.add(vintage);
                }
                if (!currentStems.contains(name.substring(0, name.length() - ".yaml".length()))) {
                    advisories.add("stale: " + name
                            + " — no current grid point resolves to this configuration");
                }
            }
        } catch (IOException error) {
            throw new ContractConfigurationException(
                    "cannot scan explorations directory " + active + ": " + error.getMessage(),
                    error);
        }
        if (vintages.size() > 1) {
            advisories.add("note: artefacts span " + vintages.size() + " run vintages ("
                    + String.join(", ", vintages) + ")");
        }
        return advisories;
    }

    /** The artefact's generation date (the timestamp's date part), or null. */
    private static String generatedDate(Path artefact) {
        try {
            for (String line : Files.readAllLines(artefact)) {
                if (line.startsWith("generatedAt:")) {
                    String stamp = line.substring("generatedAt:".length()).strip()
                            .replace("\"", "").replace("'", "");
                    return stamp.length() >= 10 ? stamp.substring(0, 10) : null;
                }
            }
        } catch (IOException unreadable) {
            return null;
        }
        return null;
    }
}
