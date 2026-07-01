package org.mavai.punit.report;

/**
 * One variant's or iteration's outcome for a single named criterion, read
 * straight from a comparison-report YAML's {@code statistics.criteria.<name>}
 * block (Explore) or {@code iterations[].statistics.criteria.<name>} block
 * (Optimize).
 *
 * <p>Carries only values already present in the YAML — no derived
 * statistics. The pass-rate is the observed proportion the producer wrote;
 * the renderer re-presents it, it does not recompute it. Shared by the
 * {@code explore} and {@code optimize} comparison-report renderers, whose
 * per-criterion result shape is otherwise identical.
 *
 * @param name             the criterion identifier
 * @param observedPassRate the observed pass proportion in {@code [0, 1]}
 * @param pass             passing sample count
 * @param fail             failing sample count
 * @param conditionFail    samples that failed the precondition
 * @param transformFail    samples that failed the response transform
 */
public record CriterionResult(
        String name,
        double observedPassRate,
        int pass,
        int fail,
        int conditionFail,
        int transformFail) {
}
