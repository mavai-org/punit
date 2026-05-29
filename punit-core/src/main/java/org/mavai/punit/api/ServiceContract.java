package org.mavai.punit.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.mavai.punit.api.covariate.Covariate;

/**
 * A stochastic service invocation expressed as a typed, three-parameter
 * function. The metadata layer of a service contract: how the framework
 * identifies it, what covariates it is sensitive to, what pacing and
 * warmup it requires.
 *
 * <p>{@code ServiceContract} extends {@link Contract} — the operational layer
 * that carries the service call ({@link Contract#invoke invoke}) and
 * the acceptance criteria ({@link Contract#criteria() criteria}). An
 * author writing one service contract implements one interface and
 * overrides three methods: {@code invoke}, {@code criteria()}, and
 * whichever metadata methods diverge from the framework defaults.
 *
 * <p>{@code FT} is the factor record — the configuration the author has
 * chosen to vary. {@code IT} is the per-sample input. {@code OT} is the
 * per-sample output value type, wrapped in an {@link ServiceContractOutcome}
 * assembled by the framework's {@code apply} dispatch on
 * {@link Contract}.
 *
 * <p>A {@code ServiceContract} is constructed by the framework once per factor
 * configuration (via the factory declared on the spec). Once
 * constructed the instance must behave as if immutable for the duration
 * of sampling — the framework caches and reuses the same instance
 * across samples for a given configuration.
 *
 * <p>Implementations are free to keep internal caches, connection
 * handles, or other per-configuration state, but must not mutate such
 * state in a way that changes the behaviour observed by {@code invoke}.
 * Pre-existing caches and pools are fine; live reconfiguration in
 * response to sample outcomes is not.
 *
 * @param <FT> the factor record type — typically a {@code record}
 * @param <IT> the per-sample input type
 * @param <OT> the per-sample output value type
 */
// javai-ref: JVI-SMQ2S1R — do not remove (resolves in javai-orchestrator)
public interface ServiceContract<FT, IT, OT> extends Contract<IT, OT> {

    /**
     * An optional per-sample wall-clock bound. When present, the engine
     * records a duration violation for any sample whose
     * {@link ServiceContractOutcome#duration() duration} exceeds the bound. The
     * sample's postcondition results are still collected; the violation
     * is an additional facet, not a short-circuit.
     *
     * <p>Most service contracts do not have a per-sample bound — aggregate
     * latency claims (e.g. 95th-percentile under N ms) belong on the
     * test via the {@code PercentileLatency} criterion. The two
     * statements have two distinct homes.
     *
     * @return the per-sample latency bound; never null. The default is
     *         {@link Optional#empty()}.
     */
    default Optional<Duration> maxLatency() {
        return Optional.empty();
    }

    /**
     * The stable identifier used in baseline filenames, logs, and
     * diagnostics.
     *
     * <p>Defaults to a kebab-cased form of the simple class name
     * (e.g. {@code ShoppingBasketServiceContract} becomes
     * {@code shopping-basket-use-case}). Implementations that outgrow
     * the default should override with a fixed, filename-safe string.
     *
     * <p>The value must be stable across runs, non-empty, and must not
     * contain characters that are invalid in baseline filenames
     * ({@code / \\ : * ? " < > |} or ASCII control characters).
     *
     * @return the service contract id
     */
    default String id() {
        return defaultIdFor(getClass());
    }

    /**
     * A human-readable description of the service contract. Empty by default.
     * Surfaced in reports and logs; has no semantic effect.
     *
     * @return the description
     */
    default String description() {
        return "";
    }

    /**
     * The number of warmup invocations to run before counted samples
     * begin. Warmup outcomes are discarded — they do not contribute to
     * pass rate or any statistic — but they do consume budget.
     *
     * <p>A value of {@code 0} (the default) disables warmup.
     *
     * @return the warmup invocation count; never negative
     */
    default int warmup() {
        return 0;
    }

    /**
     * The rate and concurrency limits the engine must respect when
     * invoking this service contract.
     *
     * <p>Pacing belongs to the service under test, not to a specific
     * experiment or probabilistic test exercising it — every test of
     * the same service should respect the same rate limit. Authors
     * override this method on their service contract implementation; spec
     * builders do not expose pacing knobs.
     *
     * <p>Defaults to {@link Pacing#unlimited()} (no rate or
     * concurrency cap).
     *
     * @return the pacing record; never null
     */
    default Pacing pacing() {
        return Pacing.unlimited();
    }

    /**
     * The covariates this service contract is sensitive to — environmental
     * variables that the developer does not control but that
     * influence outcomes (day of week, region, model version, …).
     *
     * <p>Declarations are pure metadata: they describe what
     * <i>can</i> vary, not what <i>is</i> varying right now.
     * Resolution to concrete values happens at sample time and is
     * the framework's responsibility.
     *
     * <p>Covariate values resolved at measurement time become part of
     * the baseline's identity — a baseline measured on a Sunday in
     * the EU region cannot be matched to a test running on a Tuesday
     * in the US region without surfacing the misalignment. This
     * preserves the statistical guarantees of the empirical pair.
     *
     * <p>The default is an empty list — a service contract with no declared
     * covariates is treated as covariate-insensitive and matches any
     * baseline regardless of capture context.
     *
     * @return the covariate declarations; never null. The default is
     *         empty.
     */
    default List<Covariate> covariates() {
        return List.of();
    }

    /**
     * Resolvers for any {@code CustomCovariate} entries declared by
     * {@link #covariates()}.
     *
     * <p>The framework owns the four built-in resolvers (day-of-week,
     * time-of-day, region, timezone). For each {@code CustomCovariate}
     * the service contract declares, the framework consults this map (keyed
     * by the covariate's {@link Covariate#name() name}) and invokes
     * the supplier exactly once at the start of an experiment or
     * test execution.
     *
     * <p>The supplier must be deterministic within a single
     * execution: calling it twice in the same run must yield the
     * same value, since all samples in that run share one resolved
     * profile.
     *
     * <p>If the service contract declares a {@code CustomCovariate} whose
     * name has no entry in this map, the framework fails fast before
     * any samples run — silent fallbacks would corrupt baseline
     * identity.
     *
     * @return the resolver map; never null. The default is empty.
     */
    default Map<String, Supplier<String>> customCovariateResolvers() {
        return Map.of();
    }

    /**
     * Computes the default id for a class: the simple class name with
     * any {@code ServiceContract} suffix stripped, camel-case boundaries
     * converted to kebab-case, and the whole string lower-cased.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code ShoppingBasketServiceContract}   → {@code shopping-basket}</li>
     *   <li>{@code PaymentGateway}          → {@code payment-gateway}</li>
     *   <li>{@code HTTPClientServiceContract}       → {@code http-client}</li>
     *   <li>{@code SimpleLLMServiceContract}        → {@code simple-llm}</li>
     * </ul>
     *
     * @param type the class to derive an id from
     * @return the kebab-cased id
     */
    static String defaultIdFor(Class<?> type) {
        String name = type.getSimpleName();
        if (name.endsWith("ServiceContract") && name.length() > "ServiceContract".length()) {
            name = name.substring(0, name.length() - "ServiceContract".length());
        }
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && isWordBoundary(name, i)) {
                out.append('-');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private static boolean isWordBoundary(String s, int i) {
        char prev = s.charAt(i - 1);
        char curr = s.charAt(i);
        if (Character.isLowerCase(prev) && Character.isUpperCase(curr)) {
            return true;
        }
        if (Character.isUpperCase(prev) && Character.isUpperCase(curr)
                && i + 1 < s.length() && Character.isLowerCase(s.charAt(i + 1))) {
            return true;
        }
        return false;
    }
}
