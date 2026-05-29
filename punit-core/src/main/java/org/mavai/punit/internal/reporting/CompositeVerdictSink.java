package org.mavai.punit.internal.reporting;

import java.util.List;
import java.util.Objects;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.VerdictSink;

/**
 * A {@link VerdictSink} that dispatches each verdict to multiple sinks.
 *
 * <p>Sinks are invoked in registration order. If a sink throws an exception,
 * it is logged and the remaining sinks still receive the verdict.
 */
public final class CompositeVerdictSink implements VerdictSink {

    private final List<VerdictSink> sinks;

    public CompositeVerdictSink(List<VerdictSink> sinks) {
        Objects.requireNonNull(sinks, "sinks must not be null");
        this.sinks = List.copyOf(sinks);
    }

    @Override
    public void accept(ProbabilisticTestVerdict verdict) {
        for (VerdictSink sink : sinks) {
            try {
                sink.accept(verdict);
            } catch (Exception e) {
                System.err.println("VerdictSink " + sink.getClass().getSimpleName()
                        + " failed: " + e.getMessage());
            }
        }
    }

    /**
     * Returns the number of sinks in this composite.
     */
    public int size() {
        return sinks.size();
    }
}
