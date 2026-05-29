package org.mavai.punit.api.covariate;

/**
 * Classification of covariates by their nature and matching semantics.
 *
 * <p>Each category determines:
 * <ul>
 *   <li>How mismatches are handled (hard fail vs soft warning)</li>
 *   <li>The language used in statistical reports</li>
 * </ul>
 *
 * @see Covariate
 */
public enum CovariateCategory {

    /**
     * Temporal and cyclical factors affecting system behavior.
     *
     * <p>Examples: day_of_week, time_of_day
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Temporal factors may influence system behavior"
     */
    TEMPORAL(false),

    /**
     * External services and dependencies outside our control.
     *
     * <p>Examples: third_party_api_version, upstream_service
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Third-party service behavior may have changed"
     */
    EXTERNAL_DEPENDENCY(false),

    /**
     * Execution environment characteristics.
     *
     * <p>Examples: cloud_provider, instance_type
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Resource availability and latency characteristics may vary"
     */
    INFRASTRUCTURE(false),

    /**
     * Execution operational characteristics.
     *
     * <p>Examples: region, timezone
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Resource availability and latency characteristics may vary"
     */
    OPERATIONAL(false),

    /**
     * Data state affecting behavior.
     *
     * <p>Examples: cache_state, index_version, training_data_version, catalog_size
     *
     * <p><strong>Matching:</strong> Soft match — test proceeds with warning on mismatch
     * <p><strong>Report language:</strong> "Data volume or distribution may affect performance"
     */
    DATA_STATE(false),

    /**
     * Deliberate system configuration choices.
     *
     * <p>Examples: llm_model, prompt_version, temperature
     *
     * <p><strong>Matching:</strong> Hard gate — no compatible baseline found on mismatch
     * <p><strong>Report language:</strong> Actionable error guiding to EXPLORE/MEASURE
     *
     * <p>A mismatch in CONFIGURATION covariates indicates the developer should
     * use EXPLORE mode to compare configurations, or MEASURE to establish a
     * new baseline with the current configuration.
     */
    CONFIGURATION(true);

    private final boolean hardGate;

    CovariateCategory(boolean hardGate) {
        this.hardGate = hardGate;
    }

    /**
     * Returns true if this category requires exact match (hard gate).
     *
     * @return true for CONFIGURATION, false for all others
     */
    public boolean isHardGate() {
        return hardGate;
    }
}
