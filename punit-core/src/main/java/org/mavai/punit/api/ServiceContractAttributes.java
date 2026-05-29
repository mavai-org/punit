package org.mavai.punit.api;

/**
 * Bundles use-case-level attributes declared on {@code @ServiceContract}.
 *
 * <p>This value object acts as a single carrier for attributes that flow from
 * the {@code @ServiceContract} annotation through configs, baselines, specs, and verdicts.
 * Adding a new attribute requires changing only this record plus the semantic
 * sites that act on it — not the ~35 pass-through plumbing files.
 *
 * @param warmup number of warmup invocations to discard before counting (0 = no warmup)
 * @param maxConcurrent maximum concurrent sample executions (0 = framework default)
 */
// javai-ref: JVI-FX47WDW — do not remove (resolves in javai-orchestrator)
public record ServiceContractAttributes(int warmup, int maxConcurrent) {

    /** Default attributes (no warmup, no concurrency override). */
    public static final ServiceContractAttributes DEFAULT = new ServiceContractAttributes(0, 0);

    public ServiceContractAttributes {
        if (warmup < 0) {
            throw new IllegalArgumentException("warmup must be >= 0, but was " + warmup);
        }
        if (maxConcurrent < 0) {
            throw new IllegalArgumentException("maxConcurrent must be >= 0, but was " + maxConcurrent);
        }
    }

    /**
     * Convenience constructor for warmup-only (maxConcurrent defaults to 0).
     */
    public ServiceContractAttributes(int warmup) {
        this(warmup, 0);
    }

    /** Returns true if warmup invocations are configured. */
    public boolean hasWarmup() {
        return warmup > 0;
    }

    /** Returns true if a max concurrency override is configured. */
    public boolean hasMaxConcurrent() {
        return maxConcurrent > 0;
    }
}
