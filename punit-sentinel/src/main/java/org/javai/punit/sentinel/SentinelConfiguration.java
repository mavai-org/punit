package org.javai.punit.sentinel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.javai.punit.internal.reporting.CompositeVerdictSink;
import org.javai.punit.internal.reporting.LogVerdictSink;
import org.javai.punit.verdict.VerdictSink;

/**
 * Configuration for a Sentinel run.
 *
 * <p>Built via a fluent builder. The only required element is at
 * least one registered class — a class declaring methods annotated
 * with {@code @ProbabilisticTest} or {@code @Experiment}.
 *
 * <pre>{@code
 * SentinelConfiguration config = SentinelConfiguration.builder()
 *     .sentinelClass(ShoppingBasketReliability.class)
 *     .verdictSink(WebhookVerdictSink.builder("https://alerts.example.com/punit").build())
 *     .environmentMetadata(EnvironmentMetadata.fromEnvironment())
 *     .build();
 * }</pre>
 */
public final class SentinelConfiguration {

    private final List<Class<?>> sentinelClasses;
    private final VerdictSink verdictSink;
    private final EnvironmentMetadata environmentMetadata;

    private SentinelConfiguration(Builder builder) {
        if (builder.sentinelClasses.isEmpty()) {
            throw new IllegalArgumentException("At least one registered class is required");
        }
        this.sentinelClasses = List.copyOf(builder.sentinelClasses);
        this.verdictSink = buildVerdictSink(builder.verdictSinks);
        this.environmentMetadata = builder.environmentMetadata != null
                ? builder.environmentMetadata
                : EnvironmentMetadata.fromEnvironment();
    }

    public List<Class<?>> sentinelClasses() {
        return sentinelClasses;
    }

    public VerdictSink verdictSink() {
        return verdictSink;
    }

    public EnvironmentMetadata environmentMetadata() {
        return environmentMetadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static VerdictSink buildVerdictSink(List<VerdictSink> sinks) {
        if (sinks.isEmpty()) {
            return new LogVerdictSink();
        }
        if (sinks.size() == 1) {
            return sinks.getFirst();
        }
        return new CompositeVerdictSink(sinks);
    }

    public static final class Builder {

        private final List<Class<?>> sentinelClasses = new ArrayList<>();
        private final List<VerdictSink> verdictSinks = new ArrayList<>();
        private EnvironmentMetadata environmentMetadata;

        private Builder() {}

        /**
         * Registers a class for Sentinel execution. The class must
         * declare a public no-arg constructor and one or more methods
         * annotated with {@code @ProbabilisticTest} or
         * {@code @Experiment}.
         */
        public Builder sentinelClass(Class<?> sentinelClass) {
            Objects.requireNonNull(sentinelClass, "sentinelClass must not be null");
            this.sentinelClasses.add(sentinelClass);
            return this;
        }

        /**
         * Registers multiple classes for Sentinel execution.
         */
        public Builder sentinelClasses(List<Class<?>> classes) {
            Objects.requireNonNull(classes, "classes must not be null");
            this.sentinelClasses.addAll(classes);
            return this;
        }

        /**
         * Adds a verdict sink. Multiple sinks are composed automatically.
         * Defaults to {@link LogVerdictSink} if none are added.
         */
        public Builder verdictSink(VerdictSink sink) {
            Objects.requireNonNull(sink, "sink must not be null");
            this.verdictSinks.add(sink);
            return this;
        }

        /**
         * Sets the environment metadata attached to every verdict.
         * Defaults to {@link EnvironmentMetadata#fromEnvironment()}.
         */
        public Builder environmentMetadata(EnvironmentMetadata metadata) {
            this.environmentMetadata = metadata;
            return this;
        }

        public SentinelConfiguration build() {
            return new SentinelConfiguration(this);
        }
    }
}
