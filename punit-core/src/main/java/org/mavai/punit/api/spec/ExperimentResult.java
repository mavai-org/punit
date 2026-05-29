package org.mavai.punit.api.spec;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The result of running an experiment spec (measure / explore / optimize).
 *
 * <p>Carries a human-readable acknowledgement and the path the produced
 * artifact was (or would have been) written to. Stage 2 does not yet
 * serialise — the path is reported symbolically so downstream components
 * can verify routing.
 *
 * @param message human-readable acknowledgement
 * @param destination the path the artifact was or would have been
 *                    written to
 */
public record ExperimentResult(String message, Path destination)
        implements EngineResult {

    public ExperimentResult {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(destination, "destination");
    }
}
