package org.mavai.punit.internal.engine.optimize;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.time.Duration;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.spec.CriterionSampleCounts;
import org.mavai.punit.api.spec.FactorsStepper.IterationResult;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.api.spec.Trial;
import org.mavai.punit.internal.engine.emit.EmittedKeys;
import org.mavai.punit.internal.engine.emit.FailureDistributions;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.internal.engine.emit.ResultProjections;
import org.mavai.punit.internal.engine.emit.StandingsBlocks;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Serialises a completed OPTIMIZE run's history to the mavai
 * family's canonical optimize interchange schema. Pure — performs
 * no I/O. The
 * {@link org.mavai.punit.internal.runtime.OptimizeEmitter OPTIMIZE emitter}
 * orchestrates persistence (writing to disk or to an in-memory sink
 * for tests).
 *
 * <p>One file per optimize run, carrying the full iteration history
 * and a {@code convergence:} block. Filename is
 * {@code {serviceContractId}/{experimentId}.yaml} — assembled by the
 * emitter, not the writer.
 */
// mavai-ref: JVI-FJK9SN9 — do not remove (resolves in mavai-orchestrator)
public final class OptimizeOutputWriter {

    /** Schema-version value carried in every emitted file. */
    public static final String SCHEMA_VERSION = "mavai-optimize-1";

    /**
     * Build the optimize-output YAML for one optimize run. Pure —
     * no I/O.
     *
     * @param serviceContractId the service contract identifier
     * @param experimentId the experiment identifier (becomes the
     *                     filename stem)
     * @param objective {@code "MAXIMIZE"} or {@code "MINIMIZE"}
     * @param scorerName the scorer's stable domain name (e.g.
     *                   {@code observed-pass-rate}), or {@code null}
     *                   for an unnamed ad-hoc scorer — stated in the
     *                   additive {@code scorer} field only when the
     *                   author declared one
     * @param history the full iteration history in execution order;
     *                each {@link IterationResult} carries its
     *                factors, score, raw counts, and per-clause
     *                failure histogram.
     * @param bestIteration the iteration the optimisation chose as
     *                      best per the declared direction
     * @param terminationReason why the iteration loop stopped
     *                          ({@code MAX_ITERATIONS},
     *                          {@code NO_IMPROVEMENT},
     *                          {@code STEPPER_STOP}, or another
     *                          framework-recognised value)
     * @return YAML matching the canonical optimize interchange schema
     */
    public String writeYaml(
            String serviceContractId,
            String experimentId,
            String objective,
            String scorerName,
            List<? extends IterationResult<?>> history,
            List<? extends SampleSummary<?>> iterationSummaries,
            IterationResult<?> bestIteration,
            String terminationReason) {
        return writeYaml(serviceContractId, experimentId, objective, scorerName,
                history, iterationSummaries, bestIteration, terminationReason, List.of());
    }

    /**
     * As above, with the run's criteria — each iteration's
     * per-criterion statistics then state the postcondition standings
     * (the {@code mavai-optimize-1} schema's binding-when-present
     * {@code standings} block, identical in shape to the exploration
     * artefact's per the family's identical-statistics-shape rule).
     */
    public String writeYaml(
            String serviceContractId,
            String experimentId,
            String objective,
            String scorerName,
            List<? extends IterationResult<?>> history,
            List<? extends SampleSummary<?>> iterationSummaries,
            IterationResult<?> bestIteration,
            String terminationReason,
            List<? extends org.mavai.punit.api.criterion.Criterion<?>> criteria) {

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("serviceContractId", serviceContractId);
        root.put("experimentId", experimentId);
        root.put("objective", objective);
        if (scorerName != null) {
            root.put("scorer", scorerName);
        }
        root.put("generatedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        root.put("iterations", iterationsBlock(history, iterationSummaries, criteria));
        root.put("convergence", convergenceBlock(history, bestIteration, terminationReason));

        String dump = yaml().dump(root);
        return ResultProjections.injectAnchorComments(dump, allAnchors(iterationSummaries));
    }

    /**
     * Concatenate every iteration's per-trial anchors in iteration
     * order. The post-processor consumes them in document order as
     * it walks the dumped YAML's {@code sample[N]:} lines, so the
     * concatenated list must mirror that traversal: iteration[0]'s
     * trials, then iteration[1]'s trials, and so on.
     */
    private static List<String> allAnchors(List<? extends SampleSummary<?>> iterationSummaries) {
        List<String> all = new ArrayList<>();
        for (SampleSummary<?> summary : iterationSummaries) {
            all.addAll(ResultProjections.anchorsFor(summary.trials()));
        }
        return all;
    }

    private static List<Map<String, Object>> iterationsBlock(
            List<? extends IterationResult<?>> history,
            List<? extends SampleSummary<?>> iterationSummaries,
            List<? extends org.mavai.punit.api.criterion.Criterion<?>> criteria) {
        List<Map<String, Object>> out = new ArrayList<>(history.size());
        for (int idx = 0; idx < history.size(); idx++) {
            IterationResult<?> ir = history.get(idx);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("iteration", idx);
            entry.put("factors", factorsBlock(FactorBundle.of(ir.factors())));
            // score is optimize-specific (no analogue in EXPLORE) so
            // it sits flat next to factors; the rest of the iteration
            // mirrors an EXPLORE cell's block layout — execution,
            // statistics, cost, latency, resultProjection — so a
            // reader can navigate optimize iterations with the same
            // path knowledge they use on exploration outputs.
            entry.put("score", ir.score());
            SampleSummary<?> iterSummary = idx < iterationSummaries.size()
                    ? iterationSummaries.get(idx) : null;
            entry.put("execution", executionBlock(ir, iterSummary));
            entry.put("statistics", statisticsBlock(ir, iterSummary, criteria));
            if (iterSummary != null) {
                entry.put("cost", costBlock(iterSummary));
                LatencySection.blockFor(iterSummary)
                        .ifPresent(block -> entry.put("latency", block));
                List<? extends Trial<?, ?>> trials = iterSummary.trials();
                entry.put("resultProjection", ResultProjections.resultProjectionMap(trials));
            }
            out.add(entry);
        }
        return out;
    }

    private static Map<String, Object> executionBlock(
            IterationResult<?> ir, SampleSummary<?> iterSummary) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("samplesExecuted", ir.samplesExecuted());
        if (iterSummary != null) {
            block.put("terminationReason", iterSummary.terminationReason().name());
        }
        return block;
    }

    private static Map<String, Object> statisticsBlock(
            IterationResult<?> ir, SampleSummary<?> iterSummary,
            List<? extends org.mavai.punit.api.criterion.Criterion<?>> criteria) {
        Map<String, Object> block = new LinkedHashMap<>();
        int total = ir.samplesExecuted();
        double observed = total == 0 ? 0.0 : (double) ir.successes() / (double) total;
        block.put("observed", observed);
        block.put("successes", ir.successes());
        block.put("failures", ir.failures());
        // Sequence of {condition, count} entries — first-failing-
        // condition attribution over the iteration's trials, so entry
        // counts sum to the failures total. Never a mapping keyed by
        // free-text identity (artefact key discipline).
        block.put("failureDistribution", iterSummary != null
                ? FailureDistributions.fromTrials(iterSummary.trials())
                : List.of());
        // Per-criterion decomposition — required by the interchange
        // schema, one entry per declared criterion (including the
        // single-criterion case). conditionFail / transformFail are
        // permitted informational extras beyond the canonical fields.
        if (iterSummary != null) {
            // The iteration's standings, derived once and joined onto
            // each criterion's row — the per-check tally is stated
            // beside the counts, binding when present.
            Map<String, Map<String, Object>> standingsByCriterion = criteria.isEmpty()
                    ? Map.of()
                    : StandingsBlocks.byCriterion(
                            org.mavai.punit.api.spec.PostconditionStandings.from(
                                    iterSummary, criteria));
            Map<String, Object> criteriaBlock = new LinkedHashMap<>();
            for (CriterionSampleCounts c : iterSummary.criterionSampleCounts()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("observedPassRate", c.observedPassRate());
                row.put("pass", c.pass());
                row.put("fail", c.fail());
                row.put("conditionFail", c.conditionFail());
                row.put("transformFail", c.transformFail());
                Map<String, Object> standings =
                        standingsByCriterion.get(EmittedKeys.bound(c.criterionId()));
                if (standings != null) {
                    row.put("standings", standings);
                }
                criteriaBlock.put(EmittedKeys.bound(c.criterionId()), row);
            }
            block.put("criteria", criteriaBlock);
        }
        return block;
    }

    private static Map<String, Object> costBlock(SampleSummary<?> summary) {
        Map<String, Object> block = new LinkedHashMap<>();
        Duration elapsed = summary.elapsed();
        long totalMs = elapsed.toMillis();
        block.put("totalTimeMs", totalMs);
        int total = summary.total();
        block.put("avgTimePerSampleMs", total == 0 ? 0L : totalMs / total);
        // Token totals when the run tracked any (the schema's
        // informational cost fields; absent when untracked, so
        // token-less runs stay shape-stable).
        long tokens = summary.tokensConsumed();
        if (tokens > 0) {
            block.put("totalTokens", tokens);
            block.put("avgTokensPerSample", total == 0 ? 0L : tokens / total);
        }
        return block;
    }

    private static Map<String, Object> convergenceBlock(
            List<? extends IterationResult<?>> history,
            IterationResult<?> best,
            String terminationReason) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("totalIterations", history.size());
        int bestIndex = -1;
        if (best != null) {
            for (int i = 0; i < history.size(); i++) {
                if (history.get(i) == best) {
                    bestIndex = i;
                    break;
                }
            }
            block.put("bestIteration", bestIndex);
            block.put("bestScore", best.score());
            block.put("bestFactors", factorsBlock(FactorBundle.of(best.factors())));
        }
        block.put("terminationReason", terminationReason);
        return block;
    }

    private static Map<String, Object> factorsBlock(FactorBundle bundle) {
        Map<String, Object> block = new LinkedHashMap<>();
        for (FactorBundle.Entry e : bundle.entries()) {
            block.put(EmittedKeys.bound(e.name()), e.value().yamlValue());
        }
        return block;
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }
}
