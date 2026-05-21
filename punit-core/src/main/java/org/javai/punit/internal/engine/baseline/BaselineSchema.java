package org.javai.punit.internal.engine.baseline;

/**
 * Schema constants shared by the baseline writer and reader.
 *
 * <p>See {@code docs/DES-BASELINE-YAML-SCHEMA.md} for the full
 * specification. Keeping the field names and the discriminator value
 * in one place avoids a drift class — the writer and reader either
 * agree, or the build breaks.
 */
final class BaselineSchema {

    private BaselineSchema() { }

    /** The structural-version discriminator for the baseline YAML schema. */
    static final String SCHEMA_VERSION_VALUE = "punit-baseline-3";

    /**
     * Previous schema version retained as a literal so the reader can
     * give a precise migration message when it encounters an
     * out-of-date file.
     */
    static final String SCHEMA_VERSION_PREVIOUS = "punit-baseline-2";

    /**
     * The per-methodology-criterion nesting key under
     * {@code statistics.bernoulli-pass-rate}. From punit-baseline-3
     * onward, the bernoulli stats are always nested per methodology
     * criterion (uniformly, including K=1).
     */
    static final String FIELD_CRITERIA = "criteria";

    static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    static final String FIELD_USE_CASE_ID = "useCaseId";
    static final String FIELD_METHOD_NAME = "methodName";
    static final String FIELD_FACTORS_FINGERPRINT = "factorsFingerprint";
    static final String FIELD_INPUTS_IDENTITY = "inputsIdentity";
    static final String FIELD_SAMPLE_COUNT = "sampleCount";
    static final String FIELD_GENERATED_AT = "generatedAt";
    static final String FIELD_STATISTICS = "statistics";
    static final String FIELD_COVARIATES = "covariates";

    // Baseline expiration. Both omitted entirely when there is
    // no expiration window (expiresInDays == 0). expiresInDays is the
    // authoritative input; expiresAt is the derived (generatedAt +
    // expiresInDays) convenience the reader recomputes rather than trusts.
    static final String FIELD_EXPIRES_IN_DAYS = "expiresInDays";
    static final String FIELD_EXPIRES_AT = "expiresAt";

    // PassRate / PassRateStatistics
    static final String FIELD_OBSERVED_PASS_RATE = "observedPassRate";

    // PercentileLatency / LatencyStatistics
    static final String FIELD_PERCENTILES = "percentiles";
    static final String PERCENTILE_KEY_P50 = "p50";
    static final String PERCENTILE_KEY_P90 = "p90";
    static final String PERCENTILE_KEY_P95 = "p95";
    static final String PERCENTILE_KEY_P99 = "p99";

    // Criterion-name keys (mirror Criterion.name() values).
    static final String CRITERION_BERNOULLI_PASS_RATE = "bernoulli-pass-rate";
    static final String CRITERION_PERCENTILE_LATENCY = "percentile-latency";
}
