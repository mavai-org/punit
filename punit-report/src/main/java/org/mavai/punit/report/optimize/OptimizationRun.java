package org.mavai.punit.report.optimize;

import java.util.List;

/**
 * One OPTIMIZE experiment — the unit the comparison report renders as a
 * single section. Each optimization YAML file is one run.
 *
 * @param service      the service contract identifier ({@code serviceContractId})
 * @param experimentId the experiment identifier
 * @param objective    {@code MAXIMIZE} or {@code MINIMIZE} — the sense in
 *                     which a higher/lower score is "better"
 * @param iterations   the run's iterations, in execution order (the
 *                     renderer applies its own ranking)
 * @param convergence  the framework's convergence summary
 */
record OptimizationRun(
        String service,
        String experimentId,
        String objective,
        List<Iteration> iterations,
        Convergence convergence) {

    /** Whether a higher score is better (the default when unspecified). */
    boolean maximize() {
        return !"MINIMIZE".equalsIgnoreCase(objective);
    }
}
