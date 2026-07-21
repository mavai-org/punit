package org.mavai.punit.internal.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.FactorsStepper.IterationResult;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.api.spec.Spec;
import org.mavai.punit.api.spec.TypedSpec;
import org.mavai.punit.internal.engine.optimize.OptimizeOutputWriter;

/**
 * Translates a completed OPTIMIZE {@link Experiment} into the
 * optimize-output YAML artefact. Called from
 * {@link PUnit.OptimizeBuilder#run} after the engine finishes the
 * iteration loop.
 *
 * <p>One file per optimize run at
 * {@code {baseDir}/{serviceContractId}/{experimentId}.yaml}. The file
 * carries the full iteration history (one block per iteration with
 * factors / score / counts) plus a {@code convergence:} block
 * (best iteration, best score, best factors, termination reason).
 *
 * <p>Two overloads:
 *
 * <ul>
 *   <li>{@link #emit(Experiment, Path)} — production: writes to
 *       disk under the supplied directory.</li>
 *   <li>{@link #emit(Experiment, BiConsumer)} — test seam: yields a
 *       single {@code (relativePath, content)} pair to the supplied
 *       sink so tests can scrutinise the output without touching
 *       disk.</li>
 * </ul>
 *
 * <p>The Path overload is a thin wrapper around the BiConsumer
 * overload; both share the same artefact-assembly logic.
 */
// mavai-ref: JVI-FJK9SN9 — do not remove (resolves in mavai-orchestrator)
public final class OptimizeEmitter {

    private OptimizeEmitter() { }

    /**
     * Persist the OPTIMIZE artefact under {@code baseDir}.
     *
     * @param experiment a completed {@link Experiment.Kind#OPTIMIZE OPTIMIZE} experiment
     * @param baseDir directory under which the file
     *                {@code {serviceContractId}/{experimentId}.yaml} is
     *                written. Created if missing.
     */
    public static void emit(Experiment experiment, Path baseDir) {
        Objects.requireNonNull(baseDir, "baseDir");
        emit(experiment, (relativePath, content) -> {
            Path target = baseDir.resolve(relativePath);
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(target, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to write OPTIMIZE artefact " + relativePath
                                + " under " + baseDir, e);
            }
        });
    }

    /**
     * Emit the OPTIMIZE artefact to a sink. The sink receives one
     * {@code (relativePath, yamlContent)} pair —
     * {@code relativePath} is {@code {serviceContractId}/{experimentId}.yaml}.
     *
     * <p>This is the test-seam overload: a test passes a
     * {@code BiConsumer} that captures into a {@code Map<String, String>}
     * and asserts on the captured YAML, without ever touching disk.
     *
     * @param experiment a completed {@link Experiment.Kind#OPTIMIZE OPTIMIZE} experiment
     * @param sink receives {@code (relativePath, yamlContent)}
     */
    public static void emit(Experiment experiment, BiConsumer<String, String> sink) {
        Objects.requireNonNull(experiment, "experiment");
        Objects.requireNonNull(sink, "sink");
        if (experiment.kind() != Experiment.Kind.OPTIMIZE) {
            throw new IllegalArgumentException(
                    "OptimizeEmitter.emit only accepts OPTIMIZE-flavour Experiments; got "
                            + experiment.kind() + ".");
        }
        List<IterationResult<?>> history = experiment.history();
        if (history.isEmpty()) {
            // No iterations completed — nothing to emit.
            return;
        }
        List<SampleSummary<?>> iterationSummaries = experiment.iterationSummaries();
        Optional<IterationResult<?>> best = experiment.bestOptimizeIteration();
        String objective = experiment.optimizeObjective().orElse("MAXIMIZE");
        String scorerName = experiment.optimizeScorerName().orElse(null);
        String terminationReason = experiment.optimizeTerminationReason().orElse("UNKNOWN");
        String experimentId = experiment.experimentId();

        OptimizeOutputWriter writer = new OptimizeOutputWriter();
        String serviceContractId = serviceContractIdFor(experiment, history.get(0).factors());
        String yaml = writer.writeYaml(
                serviceContractId,
                experimentId,
                objective,
                scorerName,
                history,
                iterationSummaries,
                best.orElse(null),
                terminationReason);
        sink.accept(serviceContractId + "/" + experimentId + ".yaml", yaml);
    }

    private static String serviceContractIdFor(Experiment experiment, Object factors) {
        return experiment.dispatch(new Spec.Dispatcher<String>() {
            @Override
            @SuppressWarnings("unchecked")
            public <FT, IT, OT> String apply(TypedSpec<FT, IT, OT> typed) {
                return typed.serviceContractFactory().apply((FT) factors).id();
            }
        });
    }
}
