package org.mavai.punit.internal.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Map;

import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BaselineRecord")
class BaselineRecordTest {

    private static final Map<String, BaselineStatistics> ONE_ENTRY =
            Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000));

    @Test
    @DisplayName("filename is {serviceContractId}.{methodName}-{factorsFingerprint}.yaml")
    void filename() {
        BaselineRecord record = new BaselineRecord(
                "ShoppingBasketServiceContract", "measureBaseline", "a1b2c3d4",
                "sha256:abc", 1000, Instant.parse("2026-04-26T15:30:00Z"),
                ONE_ENTRY);

        assertThat(record.filename())
                .isEqualTo("ShoppingBasketServiceContract.measureBaseline-a1b2c3d4.yaml");
    }

    @Test
    @DisplayName("rejects null required fields")
    void rejectsNulls() {
        Instant now = Instant.now();

        assertThatNullPointerException().isThrownBy(() -> new BaselineRecord(
                null, "m", "f", "sha256:x", 1, now, ONE_ENTRY));
        assertThatNullPointerException().isThrownBy(() -> new BaselineRecord(
                "u", null, "f", "sha256:x", 1, now, ONE_ENTRY));
        assertThatNullPointerException().isThrownBy(() -> new BaselineRecord(
                "u", "m", null, "sha256:x", 1, now, ONE_ENTRY));
        assertThatNullPointerException().isThrownBy(() -> new BaselineRecord(
                "u", "m", "f", null, 1, now, ONE_ENTRY));
        assertThatNullPointerException().isThrownBy(() -> new BaselineRecord(
                "u", "m", "f", "sha256:x", 1, null, ONE_ENTRY));
        assertThatNullPointerException().isThrownBy(() -> new BaselineRecord(
                "u", "m", "f", "sha256:x", 1, now, null));
    }

    @Test
    @DisplayName("rejects blank identity fields")
    void rejectsBlanks() {
        Instant now = Instant.now();

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new BaselineRecord("", "m", "f", "sha256:x", 1, now, ONE_ENTRY));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new BaselineRecord("u", " ", "f", "sha256:x", 1, now, ONE_ENTRY));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new BaselineRecord("u", "m", "", "sha256:x", 1, now, ONE_ENTRY));
    }

    @Test
    @DisplayName("rejects negative sampleCount")
    void rejectsNegativeSampleCount() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new BaselineRecord("u", "m", "f", "sha256:x", -1, Instant.now(), ONE_ENTRY));
    }

    @Test
    @DisplayName("rejects empty statistics map — a baseline without entries cannot be consumed")
    void rejectsEmptyStatistics() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new BaselineRecord("u", "m", "f", "sha256:x", 1, Instant.now(), Map.of()))
                .withMessageContaining("must not be empty");
    }

    @Test
    @DisplayName("statistics map is defensively copied — caller mutations don't leak in")
    void defensiveCopy() {
        java.util.HashMap<String, BaselineStatistics> mutable = new java.util.HashMap<>();
        mutable.put("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000));

        BaselineRecord record = new BaselineRecord(
                "u", "m", "f", "sha256:x", 1, Instant.now(), mutable);

        mutable.clear();

        assertThat(record.statisticsByCriterionName()).hasSize(1);
    }

    @Test
    @DisplayName("filename appends one 4-char hash per covariate, in declaration order")
    void filenameWithCovariates() {
        java.util.LinkedHashMap<String, String> profile =
                new java.util.LinkedHashMap<>();
        profile.put("day_of_week", "WEEKDAY");
        profile.put("region", "DE_FR");

        BaselineRecord record = new BaselineRecord(
                "ShoppingBasketServiceContract", "measureBaseline", "a1b2c3d4",
                "sha256:abc", 1000, Instant.parse("2026-04-26T15:30:00Z"),
                ONE_ENTRY,
                CovariateProfile.of(profile));

        String dowHash = CovariateHashing.hashOne("day_of_week", "WEEKDAY");
        String regionHash = CovariateHashing.hashOne("region", "DE_FR");

        assertThat(record.filename())
                .isEqualTo("ShoppingBasketServiceContract.measureBaseline-a1b2c3d4-"
                        + dowHash + "-" + regionHash + ".yaml");
    }

    @Test
    @DisplayName("filename for empty covariate profile is the covariate-insensitive form")
    void filenameUnchangedWhenProfileEmpty() {
        BaselineRecord record = new BaselineRecord(
                "ShoppingBasketServiceContract", "measureBaseline", "a1b2c3d4",
                "sha256:abc", 1000, Instant.parse("2026-04-26T15:30:00Z"),
                ONE_ENTRY,
                CovariateProfile.empty());

        assertThat(record.filename())
                .isEqualTo("ShoppingBasketServiceContract.measureBaseline-a1b2c3d4.yaml");
    }
}
