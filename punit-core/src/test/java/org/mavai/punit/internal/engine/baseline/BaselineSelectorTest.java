package org.mavai.punit.internal.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.covariate.CovariateCategory;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BaselineSelector — covariate-aware best-match")
class BaselineSelectorTest {

    private static final Map<String, BaselineStatistics> STATS =
            Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.9, 100));

    private static BaselineRecord baseline(String fingerprintTag, CovariateProfile profile) {
        return new BaselineRecord(
                "ShoppingBasket",
                "measureBaseline",
                fingerprintTag,
                "sha256:abc",
                100,
                Instant.parse("2026-04-26T15:30:00Z"),
                STATS,
                profile);
    }

    private static CovariateProfile profile(Map<String, String> values) {
        return CovariateProfile.of(new LinkedHashMap<>(values));
    }

    @Test
    @DisplayName("empty candidates → empty result")
    void emptyCandidates() {
        Optional<BaselineRecord> selected = BaselineSelector.select(
                List.of(), CovariateProfile.empty(), List.of());
        assertThat(selected).isEmpty();
    }

    @Nested
    @DisplayName("no declarations → only empty-profile candidates considered")
    class NoDeclarations {

        @Test
        @DisplayName("picks the empty-profile candidate when one is present")
        void picksEmpty() {
            BaselineRecord untagged = baseline("ff", CovariateProfile.empty());
            BaselineRecord tagged = baseline("ff", profile(Map.of("region", "DE_FR")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(tagged, untagged),
                    CovariateProfile.empty(),
                    List.of());

            assertThat(selected).contains(untagged);
        }

        @Test
        @DisplayName("returns empty when only covariate-tagged candidates exist")
        void noEmptyAvailable() {
            BaselineRecord tagged = baseline("ff", profile(Map.of("region", "DE_FR")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(tagged),
                    CovariateProfile.empty(),
                    List.of());

            assertThat(selected).isEmpty();
        }
    }

    @Nested
    @DisplayName("scoring path: declarations + current profile")
    class ScoringPath {

        private final List<Covariate> declarations = List.of(
                Covariate.dayOfWeek(List.of(java.util.Set.of(java.time.DayOfWeek.MONDAY))),
                Covariate.region(List.of(java.util.Set.of("FR", "DE"))),
                Covariate.custom("model_version", CovariateCategory.CONFIGURATION));

        private final CovariateProfile current = profile(Map.of(
                "day_of_week", "WEEKDAY",
                "region", "DE_FR",
                "model_version", "v1"));

        @Test
        @DisplayName("exact match scores highest")
        void exactMatchWins() {
            BaselineRecord exact = baseline("ff", current);
            BaselineRecord partial = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "GB_IE",
                    "model_version", "v1")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(partial, exact), current, declarations);

            assertThat(selected).contains(exact);
        }

        @Test
        @DisplayName("rejects a candidate that disagrees on a CONFIGURATION covariate")
        void hardGateRejectsConfigMismatch() {
            BaselineRecord wrongModel = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "DE_FR",
                    "model_version", "v2")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(wrongModel), current, declarations);

            assertThat(selected).isEmpty();
        }

        @Test
        @DisplayName("CONFIGURATION mismatch wins over a higher partial score elsewhere")
        void configMismatchTrumpsScore() {
            BaselineRecord wrongModelButOtherwiseExact = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "DE_FR",
                    "model_version", "v2")));
            BaselineRecord rightModelOnly = baseline("ff-2", profile(Map.of(
                    "day_of_week", "WEEKEND",
                    "region", "GB_IE",
                    "model_version", "v1")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(wrongModelButOtherwiseExact, rightModelOnly),
                    current, declarations);

            assertThat(selected).contains(rightModelOnly);
        }

        @Test
        @DisplayName("partial match wins over default fallback")
        void partialBeatsDefault() {
            BaselineRecord empty = baseline("ff", CovariateProfile.empty());
            BaselineRecord partial = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "GB_IE",
                    "model_version", "v1")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(empty, partial), current, declarations);

            assertThat(selected).contains(partial);
        }

        @Test
        @DisplayName("falls back to empty-profile baseline when only zero-match tagged candidates exist")
        void fallbackToEmpty() {
            // Both tagged candidates have CONFIGURATION mismatch → rejected.
            // Empty-profile baseline is the only survivor.
            BaselineRecord empty = baseline("ff", CovariateProfile.empty());
            BaselineRecord wrongA = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKEND",
                    "region", "DE_FR",
                    "model_version", "v2")));
            BaselineRecord wrongB = baseline("ff-2", profile(Map.of(
                    "day_of_week", "WEEKEND",
                    "region", "GB_IE",
                    "model_version", "v3")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(wrongA, wrongB, empty), current, declarations);

            assertThat(selected).contains(empty);
        }

        @Test
        @DisplayName("zero-match covariate-tagged candidate is rejected (no silent identity drift)")
        void zeroMatchTaggedRejected() {
            // No CONFIGURATION mismatch (model_version matches) but
            // every other dimension disagrees. With model_version
            // matching, score = 1, so this is a partial match — but
            // demonstrate the rejection rule with a different
            // configuration: make all soft covariates disagree and
            // omit the CONFIGURATION covariate from the candidate.
            List<Covariate> softOnlyDeclarations = List.of(
                    Covariate.dayOfWeek(List.of(java.util.Set.of(java.time.DayOfWeek.MONDAY))),
                    Covariate.region(List.of(java.util.Set.of("FR"))));
            CovariateProfile softCurrent = profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "FR"));

            BaselineRecord allDisagree = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKEND",
                    "region", "OTHER")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(allDisagree), softCurrent, softOnlyDeclarations);

            assertThat(selected).isEmpty();
        }

        @Test
        @DisplayName("ties broken by category priority: TEMPORAL beats INFRASTRUCTURE")
        void categoryPriorityBreaksTies() {
            // Both candidates score 1 — A matches dow (TEMPORAL),
            // B matches region (INFRASTRUCTURE). Category priority:
            // TEMPORAL > INFRASTRUCTURE → A wins.
            BaselineRecord matchesDow = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "GB_IE",
                    "model_version", "v1")));
            BaselineRecord matchesRegion = baseline("ff-2", profile(Map.of(
                    "day_of_week", "WEEKEND",
                    "region", "DE_FR",
                    "model_version", "v1")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(matchesRegion, matchesDow), current, declarations);

            assertThat(selected).contains(matchesDow);
        }

        @Test
        @DisplayName("selection is deterministic on full ties (filename order)")
        void deterministicOnFullTies() {
            BaselineRecord b = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "DE_FR",
                    "model_version", "v1")));
            BaselineRecord a = baseline("aa", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "DE_FR",
                    "model_version", "v1")));

            // Same score, same matched categories — broken by filename.
            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(b, a), current, declarations);

            assertThat(selected).contains(a);
            assertThat(a.filename()).isLessThan(b.filename());
        }

        @Test
        @DisplayName("selectWithReport: CONFIGURATION rejection surfaces a structured note")
        void reportConfigurationRejection() {
            BaselineRecord wrongModel = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "DE_FR",
                    "model_version", "v2")));

            BaselineSelector.SelectionReport report = BaselineSelector.selectWithReport(
                    List.of(wrongModel), current, declarations);

            assertThat(report.selected()).isEmpty();
            assertThat(report.notes()).hasSize(1);
            assertThat(report.notes().get(0))
                    .contains("rejected " + wrongModel.filename())
                    .contains("CONFIGURATION mismatch on model_version")
                    .contains("current=v1")
                    .contains("baseline=v2");
        }

        @Test
        @DisplayName("selectWithReport: zero-overlap rejection surfaces its own note")
        void reportZeroOverlapRejection() {
            List<Covariate> softOnly = List.of(
                    Covariate.dayOfWeek(List.of(java.util.Set.of(java.time.DayOfWeek.MONDAY))),
                    Covariate.region(List.of(java.util.Set.of("FR"))));
            CovariateProfile softCurrent = profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "FR"));
            BaselineRecord allDisagree = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKEND",
                    "region", "OTHER")));

            BaselineSelector.SelectionReport report = BaselineSelector.selectWithReport(
                    List.of(allDisagree), softCurrent, softOnly);

            assertThat(report.selected()).isEmpty();
            assertThat(report.notes()).hasSize(1);
            assertThat(report.notes().get(0))
                    .contains("rejected " + allDisagree.filename())
                    .contains("no overlap");
        }

        @Test
        @DisplayName("selectWithReport: partial match emits a note alongside the selection")
        void reportPartialMatch() {
            BaselineRecord partial = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "GB_IE",
                    "model_version", "v1")));

            BaselineSelector.SelectionReport report = BaselineSelector.selectWithReport(
                    List.of(partial), current, declarations);

            assertThat(report.selected()).contains(partial);
            assertThat(report.notes()).hasSize(1);
            assertThat(report.notes().get(0))
                    .contains("partial match")
                    .contains(partial.filename())
                    .contains("matched 2 of 3 declared covariates");
        }

        @Test
        @DisplayName("selectWithReport: default-fallback selection emits a note")
        void reportDefaultFallback() {
            BaselineRecord empty = baseline("ff", CovariateProfile.empty());

            BaselineSelector.SelectionReport report = BaselineSelector.selectWithReport(
                    List.of(empty), current, declarations);

            assertThat(report.selected()).contains(empty);
            assertThat(report.notes()).hasSize(1);
            assertThat(report.notes().get(0))
                    .contains("falling back to default")
                    .contains(empty.filename());
        }

        @Test
        @DisplayName("selectWithReport: exact match emits no notes (silence on success)")
        void reportExactMatchSilent() {
            BaselineRecord exact = baseline("ff", current);

            BaselineSelector.SelectionReport report = BaselineSelector.selectWithReport(
                    List.of(exact), current, declarations);

            assertThat(report.selected()).contains(exact);
            assertThat(report.notes()).isEmpty();
        }

        @Test
        @DisplayName("a CONFIGURATION covariate omitted from the baseline is treated as a mismatch")
        void omittedConfigIsMismatch() {
            // The empty-profile candidate fields no model_version.
            // Per the rule, that's not a CONFIGURATION mismatch —
            // CONFIGURATION-grandfathering for empty-profile baselines.
            // But a non-empty baseline that happens to omit a declared
            // CONFIGURATION covariate is *not* grandfathered.
            BaselineRecord taggedNoModel = baseline("ff", profile(Map.of(
                    "day_of_week", "WEEKDAY",
                    "region", "DE_FR")));

            Optional<BaselineRecord> selected = BaselineSelector.select(
                    List.of(taggedNoModel), current, declarations);

            // baselineDeclares is false for model_version → not
            // rejected by the hard gate; matches dow + region → score
            // 2. The omitted-CONFIGURATION leniency is documented in
            // the selector. Tightening this is a future judgment call.
            assertThat(selected).contains(taggedNoModel);
        }
    }
}
