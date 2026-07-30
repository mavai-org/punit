package org.mavai.punit.decl;

import java.nio.file.Path;
import java.util.List;

/**
 * The check verb — validate a declared contract's every load-time join
 * with zero samples: the authoring loop's compile step, and the
 * {@code mavaiCheck} Gradle task's entry point.
 *
 * <p>Loads the contract file, resolves the conventional bindings class
 * (when one exists on the classpath) and the {@code mavai-services.yaml}
 * beside the contract, and constructs everything a run would construct —
 * views, selection expressions, criteria, the service's strict
 * configuration, the per-input admission gate, the exploration grid,
 * the declared optimizations — without invoking anything. A missing
 * baseline is not checked: absence is a run-time fact, not a
 * configuration defect.
 *
 * <p>Returns one line per validated fact, including the stale-artefact
 * advisory (exploration artefacts no current grid point writes —
 * named, never deleted). The first failing join throws
 * {@link ContractConfigurationException} with the same refusal a run
 * would give.
 */
public final class ContractCheck {

    private ContractCheck() {}

    /**
     * Checks one contract file.
     *
     * @param contractFile the contract file on disk
     * @param bindingsClassName the fully-qualified bindings class to
     *     resolve code registrations against, or {@code null} for none;
     *     a named class absent from the classpath is noted, not refused
     *     — the conventional name is a convention, not a requirement
     * @param explorationsDir the explorations output directory the
     *     stale-artefact advisory scans (the convention path is
     *     {@code build/punit/explorations})
     * @return one line per validated fact
     * @throws ContractConfigurationException on the first failing join,
     *     with the same refusal a run would give
     */
    public static List<String> run(Path contractFile, String bindingsClassName,
            Path explorationsDir) {
        return org.mavai.punit.decl.internal.run.ContractChecker.check(
                contractFile, bindingsClassName, explorationsDir);
    }
}
