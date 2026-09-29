package org.mavai.punit.internal.engine.criteria;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.spec.BaselineProvider;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.PercentileLatency;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pre-run latency checks of a baseline-derived constraint
 * (Statistical Companion §12.5.3): non-degeneracy and the precedence
 * rank's existence at the expected number of successful latencies. They
 * are warnings with a planning figure — never a refusal: the run goes
 * ahead, and both are decided on the actual count after it.
 */
@DisplayName("Latency-criterion preflight planning")
class LatencyFeasibilityTest {

    private static final String CONTRACT_ID = "feasibility-test";
    private static final FactorBundle EMPTY_FACTORS = FactorBundle.empty();

    private static BaselineProvider providerOf(Optional<LatencyStatistics> baseline) {
        return new BaselineProvider() {
            @Override
            @SuppressWarnings("unchecked")
            public <S extends BaselineStatistics> Optional<S> baselineFor(
                    String id, FactorBundle factors, String name, Class<S> type,
                    org.mavai.punit.api.covariate.CovariateProfile profile,
                    List<org.mavai.punit.api.covariate.Covariate> declarations) {
                return type == LatencyStatistics.class ? (Optional<S>) baseline : Optional.empty();
            }

            @Override
            public Optional<String> baselineInputsIdentityFor(
                    String id, FactorBundle factors,
                    org.mavai.punit.api.covariate.CovariateProfile profile,
                    List<org.mavai.punit.api.covariate.Covariate> declarations) {
                return Optional.empty();
            }
        };
    }

    /** A baseline of {@code latencies} successful latencies from a run of {@code runSamples}. */
    private static LatencyStatistics baseline(int latencies, int runSamples) {
        long[] ms = new long[latencies];
        for (int i = 0; i < latencies; i++) {
            ms[i] = i + 1;
        }
        return new LatencyStatistics(LatencyResult.empty(), ms, latencies, runSamples);
    }

    @Test
    @DisplayName("the §12.5.3 non-degeneracy example: 110 planned at a passing rate of 0.80 expects 88 < 100 at p99 — a warning naming 125")
    void nonDegeneracyWarningWithPlanningFigure() {
        List<String> warnings = Feasibility.check(
                110,
                PercentileLatency.<Integer>empirical(PercentileKey.P99),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION,
                providerOf(Optional.of(baseline(4000, 5000))));

        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("p99").contains("88").contains("minimum of 100").contains("125"));
    }

    @Test
    @DisplayName("the §12.5.3 existence example: no rank for 160 expected latencies against 400 — a warning naming 554")
    void existenceWarningWithPlanningFigure() {
        List<String> warnings = Feasibility.check(
                200,
                PercentileLatency.<Integer>empirical(PercentileKey.P99),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION,
                providerOf(Optional.of(baseline(400, 500))));

        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("p99").contains("160").contains("saturated").contains("554"));
    }

    @Test
    @DisplayName("a design with room to spare draws no warning")
    void noWarningWhenPlanningIsSound() {
        List<String> warnings = Feasibility.check(
                200,
                PercentileLatency.<Integer>empirical(PercentileKey.P95),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION,
                providerOf(Optional.of(baseline(2000, 2000))));

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("SMOKE, a missing baseline and an explicit ceiling draw no planning warning")
    void silentWhereNoPlanningApplies() {
        assertThat(Feasibility.check(5, PercentileLatency.<Integer>empirical(PercentileKey.P99),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.SMOKE,
                providerOf(Optional.of(baseline(400, 500))))).isEmpty();
        assertThat(Feasibility.check(5, PercentileLatency.<Integer>empirical(PercentileKey.P99),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION,
                providerOf(Optional.empty()))).isEmpty();
    }
}
