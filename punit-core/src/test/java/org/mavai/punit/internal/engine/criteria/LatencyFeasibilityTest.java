package org.mavai.punit.internal.engine.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.spec.BaselineProvider;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.PercentileLatency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Latency-criterion preflight feasibility")
class LatencyFeasibilityTest {

    private static final String CONTRACT_ID = "feasibility-test";
    private static final FactorBundle EMPTY_FACTORS = FactorBundle.empty();
    private static final BaselineProvider NULL_PROVIDER = new BaselineProvider() {
        @Override
        public <S extends BaselineStatistics> java.util.Optional<S> baselineFor(
                String id, FactorBundle factors, String name, Class<S> type,
                org.mavai.punit.api.covariate.CovariateProfile profile,
                java.util.List<org.mavai.punit.api.covariate.Covariate> declarations) {
            return java.util.Optional.empty();
        }
        @Override
        public java.util.Optional<String> baselineInputsIdentityFor(
                String id, FactorBundle factors,
                org.mavai.punit.api.covariate.CovariateProfile profile,
                java.util.List<org.mavai.punit.api.covariate.Covariate> declarations) {
            return java.util.Optional.empty();
        }
    };

    // ── Existence gate (companion §12.5.2.1): ⌈log(α) / log(p)⌉ ──
    //
    // At α = 0.05 (the default 0.95 confidence):
    //   P50 → 5
    //   P90 → 29
    //   P95 → 59
    //   P99 → 299

    @Test
    @DisplayName("VERIFICATION: samples below P95 existence floor (59 at α=0.05) → IllegalStateException")
    void verificationP95Below59Aborts() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> Feasibility.check(
                        50,
                        PercentileLatency.<Integer>empirical(PercentileKey.P95),
                        CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER))
                .withMessageContaining("P95")
                .withMessageContaining("50 samples")
                .withMessageContaining("at least 59")
                .withMessageContaining("§12.5.2.1");
    }

    @Test
    @DisplayName("VERIFICATION: samples at P95 existence floor → no abort, no warning")
    void verificationP95AtFloorSilent() {
        List<String> warnings = Feasibility.check(
                59,
                PercentileLatency.<Integer>empirical(PercentileKey.P95),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER);
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("VERIFICATION: samples below P99 existence floor (299) → IllegalStateException")
    void verificationP99Below299Aborts() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> Feasibility.check(
                        100,
                        PercentileLatency.<Integer>empirical(PercentileKey.P99),
                        CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER))
                .withMessageContaining("P99")
                .withMessageContaining("at least 299");
    }

    @Test
    @DisplayName("VERIFICATION: gate fires on the strictest failing percentile when multiple are asserted")
    void verificationMultiPercentileFiresOnStrictest() {
        // 100 samples: passes P95's floor of 59, fails P99's floor of 299.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> Feasibility.check(
                        100,
                        PercentileLatency.<Integer>empirical(PercentileKey.P95, PercentileKey.P99),
                        CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER))
                .withMessageContaining("P99");
    }

    @Test
    @DisplayName("SMOKE: silences both gates, no abort, no warnings")
    void smokeSilencesAllGates() {
        List<String> warnings = Feasibility.check(
                5,
                PercentileLatency.<Integer>empirical(PercentileKey.P99),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.SMOKE, NULL_PROVIDER);
        assertThat(warnings).isEmpty();
    }

    // ── Non-degeneracy floor (§12.5.2) — only surfaces when the
    // existence gate is relaxed by lowering confidence enough that
    // ⌈log(α) / log(p)⌉ falls below the non-degeneracy floor.

    @Test
    @DisplayName("VERIFICATION + relaxed confidence: non-degeneracy floor warns when existence gate doesn't fire")
    void nonDegeneracyWarningWhenExistenceGatePasses() {
        // At confidence 0.50, α=0.50, existence floor for P95 is
        // ⌈log(0.50)/log(0.95)⌉ = 14. Non-degeneracy floor is 20.
        // A 15-sample run clears the existence gate but trips the
        // non-degeneracy warning.
        List<String> warnings = Feasibility.check(
                15,
                PercentileLatency.<Integer>empirical(0.50, PercentileKey.P95),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("P95")
                .contains("at least 20");
    }
}
