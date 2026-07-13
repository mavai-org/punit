package org.mavai.punit.verdict;

import java.time.Instant;
import java.util.Map;

import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.statistics.RiskDrivenSizingCalculator;
import org.mavai.punit.statistics.StatisticalDefaults;
import org.mavai.punit.statistics.transparent.BaselineData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SizingDisclosureTest {

    private static final Instant GENERATED = Instant.parse("2026-07-01T00:00:00Z");

    private BaselineData baseline(int samples, int successes) {
        return new BaselineData("svc.yaml", GENERATED, samples, successes);
    }

    private Map<String, String> entries(
            ThresholdOrigin origin, int planned, BaselineData baseline, long tokens) {
        return SizingDisclosure.entries(
                origin, planned, planned, 0.90, 0.95, baseline, 5000, tokens);
    }

    @Nested
    @DisplayName("Approach disclosure")
    class ApproachDisclosure {

        @Test
        void empiricalOriginDisclosesSampleSizeFirstWithDeclaredParameters() {
            Map<String, String> entries =
                    entries(ThresholdOrigin.EMPIRICAL, 100, baseline(100, 96), 0);

            assertThat(entries)
                    .containsEntry("sizing-approach", "sample-size-first")
                    .containsEntry("sizing-declared-samples", "100")
                    .containsEntry("sizing-declared-confidence", "0.95")
                    .doesNotContainKey("sizing-declared-min-pass-rate");
        }

        @Test
        void declaredThresholdOriginDisclosesThresholdFirstWithTheDeclaredBar() {
            Map<String, String> entries = entries(ThresholdOrigin.SLA, 100, null, 0);

            assertThat(entries)
                    .containsEntry("sizing-approach", "threshold-first")
                    .containsEntry("sizing-declared-samples", "100")
                    .containsEntry("sizing-declared-min-pass-rate", "0.9")
                    .doesNotContainKey("sizing-declared-confidence");
        }

        @Test
        void absentOriginStillDisclosesTheThresholdFirstDesign() {
            Map<String, String> entries = entries(null, 100, null, 0);

            assertThat(entries).containsEntry("sizing-approach", "threshold-first");
        }
    }

    @Nested
    @DisplayName("Downsizing and efficiency disclosures")
    class DownsizingDisclosure {

        @Test
        void appearIffTheRunWasSizedBelowTheBaselinesOwnMeasurement() {
            Map<String, String> downsized =
                    entries(ThresholdOrigin.EMPIRICAL, 100, baseline(1000, 960), 0);
            assertThat(downsized)
                    .containsKey("sizing-detectable-rate")
                    .containsKey("sizing-saved-fraction");

            Map<String, String> fullSize =
                    entries(ThresholdOrigin.EMPIRICAL, 1000, baseline(1000, 960), 0);
            assertThat(fullSize)
                    .doesNotContainKey("sizing-detectable-rate")
                    .doesNotContainKey("sizing-saved-fraction");

            Map<String, String> baselineLess =
                    entries(ThresholdOrigin.EMPIRICAL, 100, null, 0);
            assertThat(baselineLess).doesNotContainKey("sizing-detectable-rate");
        }

        @Test
        void detectableRateComesFromTheSizingCalculator() {
            Map<String, String> entries =
                    entries(ThresholdOrigin.EMPIRICAL, 100, baseline(1000, 960), 0);

            double disclosed = Double.parseDouble(entries.get("sizing-detectable-rate"));
            double expected = new RiskDrivenSizingCalculator().detectableRate(
                    100, 0.96, 0.95, StatisticalDefaults.DEFAULT_TARGET_POWER);
            assertThat(disclosed).isEqualTo(expected);
            assertThat(entries).containsEntry("sizing-detectable-power", "0.8");
        }

        @Test
        void savingsDeriveFromTheRunsRecordedCosts() {
            // 100 samples over 5,000 ms and 120,000 tokens: 50 ms and 1,200
            // tokens per sample; 900 saved samples versus the baseline's 1,000.
            Map<String, String> entries =
                    entries(ThresholdOrigin.EMPIRICAL, 100, baseline(1000, 960), 120_000);

            assertThat(entries)
                    .containsEntry("sizing-saved-fraction", "0.9")
                    .containsEntry("sizing-time-saved-ms", "45000")
                    .containsEntry("sizing-tokens-saved", "1080000");
        }

        @Test
        void tokenHalfDegradesAwayWhenNoTokensAreRecorded() {
            Map<String, String> entries =
                    entries(ThresholdOrigin.EMPIRICAL, 100, baseline(1000, 960), 0);

            assertThat(entries)
                    .containsKey("sizing-time-saved-ms")
                    .doesNotContainKey("sizing-tokens-saved");
        }

        @Test
        void aPerfectBaselineRateSuppressesTheDownsizingPair() {
            Map<String, String> entries =
                    entries(ThresholdOrigin.EMPIRICAL, 100, baseline(1000, 1000), 0);

            assertThat(entries)
                    .containsEntry("sizing-approach", "sample-size-first")
                    .doesNotContainKey("sizing-detectable-rate")
                    .doesNotContainKey("sizing-saved-fraction");
        }
    }
}
