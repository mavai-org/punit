package org.mavai.punit.api.spec;

import java.util.List;
import java.util.Objects;

/**
 * One run-unit as surfaced by {@link Spec#configurations()}: the factor
 * values to instantiate the service contract with, the inputs to cycle through,
 * and how many samples to execute.
 *
 * @param factors the factor record (immutable)
 * @param inputs the per-sample input values; the engine walks them
 *               round-robin for {@code samples} iterations
 * @param samples the number of counted samples to execute
 * @param <FT> the factor record type
 * @param <IT> the input type
 * @param <OT> the outcome value type
 */
public record Configuration<FT, IT, OT>(
        FT factors,
        List<IT> inputs,
        int samples) {

    public Configuration {
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must be non-empty");
        }
        if (samples < 0) {
            throw new IllegalArgumentException("samples must be non-negative");
        }
        inputs = List.copyOf(inputs);
    }

    public static <FT, IT, OT> Configuration<FT, IT, OT> of(FT factors, List<IT> inputs, int samples) {
        return new Configuration<>(factors, inputs, samples);
    }
}
