package org.mavai.punit.internal.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.PerConfigSummary;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.api.spec.Spec;
import org.mavai.punit.api.spec.TypedSpec;
import org.mavai.punit.internal.engine.explore.ExploreOutputWriter;

/**
 * Translates a completed EXPLORE {@link Experiment} into one
 * explore-output YAML file per configuration. Called from
 * {@link PUnit.ExploreBuilder#run} after the engine finishes the
 * sampling loop across the grid.
 *
 * <p>Two overloads:
 *
 * <ul>
 *   <li>{@link #emit(Experiment, Path)} — production: writes one
 *       file per configuration under
 *       {@code {baseDir}/{serviceContractId}/{sweptKeys}/{readableStem}.yaml}.</li>
 *   <li>{@link #emit(Experiment, BiConsumer)} — test seam: yields
 *       {@code (relativePath, content)} pairs to the supplied sink so
 *       tests can scrutinise the output without touching disk.</li>
 * </ul>
 *
 * <p>The Path overload is a thin wrapper around the BiConsumer
 * overload; both share the same per-configuration logic so tests
 * exercise the same path production code drives.
 */
// mavai-ref: JVI-8CHB31R — do not remove (resolves in mavai-orchestrator)
public final class ExploreEmitter {

    private ExploreEmitter() { }

    /**
     * Persist EXPLORE artefacts under {@code baseDir}.
     *
     * @param experiment a completed {@link Experiment.Kind#EXPLORE EXPLORE} experiment
     * @param baseDir directory under which {@code {serviceContractId}/...yaml} files
     *                land. Created if missing.
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
                        "Failed to write EXPLORE artefact " + relativePath
                                + " under " + baseDir, e);
            }
        });
    }

    /**
     * Emit EXPLORE artefacts to a sink. The sink receives one
     * {@code (relativePath, yamlContent)} pair per configuration —
     * {@code relativePath} is
     * {@code {serviceContractId}/{sweptKeys}/{readableStem}.yaml} — the
     * middle segment names the experiment's swept factors ({@code +}
     * joined; {@code baseline-only} when nothing varies).
     *
     * <p>This is the test-seam overload: a test passes a
     * {@code BiConsumer} that captures into a {@code Map<String, String>}
     * and asserts on the captured YAML strings, without ever
     * touching disk.
     *
     * @param experiment a completed {@link Experiment.Kind#EXPLORE EXPLORE} experiment
     * @param sink receives {@code (relativePath, yamlContent)}
     */
    public static void emit(Experiment experiment, BiConsumer<String, String> sink) {
        Objects.requireNonNull(experiment, "experiment");
        Objects.requireNonNull(sink, "sink");
        if (experiment.kind() != Experiment.Kind.EXPLORE) {
            throw new IllegalArgumentException(
                    "ExploreEmitter.emit only accepts EXPLORE-flavour Experiments; got "
                            + experiment.kind() + ".");
        }
        ExploreOutputWriter writer = new ExploreOutputWriter();
        List<PerConfigSummary<?, ?>> entries = experiment.perConfigSummaries();
        if (entries.isEmpty()) {
            // The grid had no configurations or the engine hadn't
            // executed any. Nothing to emit; not an error.
            return;
        }
        // The experiment-level segment derives from the whole grid — a
        // factor is swept when its value varies across configurations —
        // so a superseded artefact can never sit beside fresh ones and
        // a no-sweep run and a sweep never share a directory.
        List<FactorBundle> grid = entries.stream()
                .map(entry -> FactorBundle.of(entry.factors()))
                .toList();
        String experimentDirectory = writer.experimentDirectory(grid);
        // The base is stated by identity against the declared grid point,
        // never by position: the emitter reports what the experiment
        // declared, and an experiment declaring none states none.
        java.util.Optional<Object> base = experiment.baseConfiguration();
        experiment.dispatch(new Spec.Dispatcher<Void>() {
            @Override
            public <FT, IT, OT> Void apply(TypedSpec<FT, IT, OT> typed) {
                for (PerConfigSummary<?, ?> entry : entries) {
                    @SuppressWarnings("unchecked")
                    FT factors = (FT) entry.factors();
                    var contract = typed.serviceContractFactory().apply(factors);
                    String serviceContractId = contract.id();
                    FactorBundle bundle = FactorBundle.of(factors);
                    String stem = writer.filenameFor(bundle);
                    String yaml = writer.writeYaml(
                            serviceContractId, bundle, entry, contract.effectiveCriteria(),
                            base.isPresent() && base.get().equals(factors));
                    sink.accept(serviceContractId + "/" + experimentDirectory + "/"
                            + stem + ".yaml", yaml);
                }
                return null;
            }
        });
    }
}
