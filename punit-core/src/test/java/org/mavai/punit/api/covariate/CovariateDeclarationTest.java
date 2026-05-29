package org.mavai.punit.api.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mavai.punit.api.ServiceContractAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateDeclaration}.
 */
@DisplayName("CovariateDeclaration")
class CovariateDeclarationTest {

    private static final DayGroupDefinition WEEKDAY_GROUP = new DayGroupDefinition(
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            "WEEKDAY");

    private static final DayGroupDefinition WEEKEND_GROUP = new DayGroupDefinition(
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            "WEEKEND");

    private static final TimePeriodDefinition MORNING_PERIOD = new TimePeriodDefinition(
            LocalTime.of(8, 0), 240);

    private static final RegionGroupDefinition EU_REGION = new RegionGroupDefinition(
            Set.of("DE", "FR", "IT"), "EU");

    private static final RegionGroupDefinition US_REGION = new RegionGroupDefinition(
            Set.of("US"), "US");

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("EMPTY should have no covariates")
        void emptyShouldHaveNoCovariates() {
            assertThat(CovariateDeclaration.EMPTY.isEmpty()).isTrue();
            assertThat(CovariateDeclaration.EMPTY.size()).isEqualTo(0);
            assertThat(CovariateDeclaration.EMPTY.dayGroups()).isEmpty();
            assertThat(CovariateDeclaration.EMPTY.timePeriods()).isEmpty();
            assertThat(CovariateDeclaration.EMPTY.regionGroups()).isEmpty();
            assertThat(CovariateDeclaration.EMPTY.timezoneEnabled()).isFalse();
            assertThat(CovariateDeclaration.EMPTY.customCovariates()).isEmpty();
            assertThat(CovariateDeclaration.EMPTY.serviceContractAttributes().warmup()).isEqualTo(0);
        }

        @Test
        @DisplayName("should create with day groups and custom covariates")
        void shouldCreateWithDayGroupsAndCustomCovariates() {
            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP, WEEKEND_GROUP),
                    List.of(),
                    List.of(),
                    false,
                    Map.of("custom1", CovariateCategory.CONFIGURATION),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.dayGroups()).containsExactly(WEEKDAY_GROUP, WEEKEND_GROUP);
            assertThat(declaration.customCovariates())
                    .containsEntry("custom1", CovariateCategory.CONFIGURATION);
        }

        @Test
        @DisplayName("should create immutable copies")
        void shouldCreateImmutableCopies() {
            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(MORNING_PERIOD),
                    List.of(EU_REGION),
                    true,
                    Map.of("custom", CovariateCategory.OPERATIONAL),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.dayGroups()).isUnmodifiable();
            assertThat(declaration.timePeriods()).isUnmodifiable();
            assertThat(declaration.regionGroups()).isUnmodifiable();
            assertThat(declaration.customCovariates()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("allKeys()")
    class AllKeysTests {

        @Test
        @DisplayName("should return empty list for empty declaration")
        void shouldReturnEmptyListForEmptyDeclaration() {
            assertThat(CovariateDeclaration.EMPTY.allKeys()).isEmpty();
        }

        @Test
        @DisplayName("warmup should appear first when present")
        void warmupShouldAppearFirstWhenPresent() {
            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(),
                    List.of(),
                    false,
                    Map.of("custom1", CovariateCategory.OPERATIONAL),
                    new ServiceContractAttributes(5)
            );

            assertThat(declaration.allKeys()).containsExactly("warmup", "day_of_week", "custom1");
        }

        @Test
        @DisplayName("warmup should be absent when zero")
        void warmupShouldBeAbsentWhenZero() {
            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(),
                    List.of(),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.allKeys()).containsExactly("day_of_week");
        }

        @Test
        @DisplayName("should return standard keys in fixed order then custom")
        void shouldReturnStandardKeysInFixedOrderThenCustom() {
            var customMap = new LinkedHashMap<String, CovariateCategory>();
            customMap.put("custom1", CovariateCategory.OPERATIONAL);
            customMap.put("custom2", CovariateCategory.CONFIGURATION);

            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(MORNING_PERIOD),
                    List.of(EU_REGION),
                    true,
                    customMap,
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.allKeys()).containsExactly(
                    "day_of_week",
                    "time_of_day",
                    "region",
                    "timezone",
                    "custom1",
                    "custom2"
            );
        }

        @Test
        @DisplayName("should omit absent standard covariates")
        void shouldOmitAbsentStandardCovariates() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    true,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.allKeys()).containsExactly("region", "timezone");
        }
    }

    @Nested
    @DisplayName("getCategory()")
    class GetCategoryTests {

        @Test
        @DisplayName("should return CONFIGURATION for warmup")
        void shouldReturnConfigurationForWarmup() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Map.of(),
                    new ServiceContractAttributes(5)
            );

            assertThat(declaration.getCategory("warmup"))
                    .isEqualTo(CovariateCategory.CONFIGURATION);
        }

        @Test
        @DisplayName("should throw for warmup when warmup is zero")
        void shouldThrowForWarmupWhenZero() {
            assertThatThrownBy(() -> CovariateDeclaration.EMPTY.getCategory("warmup"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared");
        }

        @Test
        @DisplayName("should return TEMPORAL for day_of_week")
        void shouldReturnTemporalForDayOfWeek() {
            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(),
                    List.of(),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.getCategory("day_of_week"))
                    .isEqualTo(CovariateCategory.TEMPORAL);
        }

        @Test
        @DisplayName("should return TEMPORAL for time_of_day")
        void shouldReturnTemporalForTimeOfDay() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(MORNING_PERIOD),
                    List.of(),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.getCategory("time_of_day"))
                    .isEqualTo(CovariateCategory.TEMPORAL);
        }

        @Test
        @DisplayName("should return OPERATIONAL for region")
        void shouldReturnOperationalForRegion() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.getCategory("region"))
                    .isEqualTo(CovariateCategory.OPERATIONAL);
        }

        @Test
        @DisplayName("should return OPERATIONAL for timezone")
        void shouldReturnOperationalForTimezone() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(),
                    true,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.getCategory("timezone"))
                    .isEqualTo(CovariateCategory.OPERATIONAL);
        }

        @Test
        @DisplayName("should return category for custom covariate")
        void shouldReturnCategoryForCustomCovariate() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Map.of("llm_model", CovariateCategory.CONFIGURATION),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.getCategory("llm_model"))
                    .isEqualTo(CovariateCategory.CONFIGURATION);
        }

        @Test
        @DisplayName("should throw for unknown covariate")
        void shouldThrowForUnknownCovariate() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThatThrownBy(() -> declaration.getCategory("unknown"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared");
        }
    }

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        @Test
        @DisplayName("should return true for warmup when warmup > 0")
        void shouldReturnTrueForWarmupWhenPositive() {
            var declaration = new CovariateDeclaration(
                    List.of(), List.of(), List.of(), false, Map.of(), new ServiceContractAttributes(5));

            assertThat(declaration.contains("warmup")).isTrue();
        }

        @Test
        @DisplayName("should return false for warmup when warmup is zero")
        void shouldReturnFalseForWarmupWhenZero() {
            assertThat(CovariateDeclaration.EMPTY.contains("warmup")).isFalse();
        }

        @Test
        @DisplayName("should return true for declared day_of_week")
        void shouldReturnTrueForDeclaredDayOfWeek() {
            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(),
                    List.of(),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.contains("day_of_week")).isTrue();
        }

        @Test
        @DisplayName("should return true for declared region")
        void shouldReturnTrueForDeclaredRegion() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.contains("region")).isTrue();
        }

        @Test
        @DisplayName("should return true for declared custom covariate")
        void shouldReturnTrueForDeclaredCustomCovariate() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Map.of("custom", CovariateCategory.OPERATIONAL),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.contains("custom")).isTrue();
        }

        @Test
        @DisplayName("should return false for undeclared covariate")
        void shouldReturnFalseForUndeclaredCovariate() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.contains("unknown")).isFalse();
        }

        @Test
        @DisplayName("should return false for inactive standard covariate")
        void shouldReturnFalseForInactiveStandardCovariate() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.contains("timezone")).isFalse();
            assertThat(declaration.contains("day_of_week")).isFalse();
        }
    }

    @Nested
    @DisplayName("computeDeclarationHash()")
    class ComputeDeclarationHashTests {

        @Test
        @DisplayName("should return empty string for empty declaration")
        void shouldReturnEmptyStringForEmptyDeclaration() {
            assertThat(CovariateDeclaration.EMPTY.computeDeclarationHash()).isEmpty();
        }

        @Test
        @DisplayName("hash should be stable across calls")
        void hashShouldBeStableAcrossCalls() {
            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(),
                    List.of(),
                    false,
                    Map.of("custom", CovariateCategory.OPERATIONAL),
                    ServiceContractAttributes.DEFAULT
            );

            var hash1 = declaration.computeDeclarationHash();
            var hash2 = declaration.computeDeclarationHash();

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("hash should be 8 characters")
        void hashShouldBe8Characters() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.computeDeclarationHash()).hasSize(8);
        }

        @Test
        @DisplayName("different declarations should produce different hashes")
        void differentDeclarationsShouldProduceDifferentHashes() {
            var declaration1 = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );
            var declaration2 = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(),
                    true,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration1.computeDeclarationHash())
                    .isNotEqualTo(declaration2.computeDeclarationHash());
        }

        @Test
        @DisplayName("warmup changes the declaration hash")
        void warmupChangesTheDeclarationHash() {
            var withoutWarmup = new CovariateDeclaration(
                    List.of(), List.of(), List.of(EU_REGION), false, Map.of(), ServiceContractAttributes.DEFAULT);
            var withWarmup = new CovariateDeclaration(
                    List.of(), List.of(), List.of(EU_REGION), false, Map.of(), new ServiceContractAttributes(5));

            assertThat(withWarmup.computeDeclarationHash())
                    .isNotEqualTo(withoutWarmup.computeDeclarationHash());
        }

        @Test
        @DisplayName("warmup-only declaration produces non-empty hash")
        void warmupOnlyDeclarationProducesNonEmptyHash() {
            var declaration = new CovariateDeclaration(
                    List.of(), List.of(), List.of(), false, Map.of(), new ServiceContractAttributes(5));

            assertThat(declaration.computeDeclarationHash()).hasSize(8);
        }

        @Test
        @DisplayName("same standard covariates should produce same hash regardless of definition details")
        void sameStandardCovariatesShouldProduceSameHash() {
            // Hash is based on keys only, not the definition details
            var declaration1 = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );
            var declaration2 = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(US_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration1.computeDeclarationHash())
                    .isEqualTo(declaration2.computeDeclarationHash());
        }
    }

    @Nested
    @DisplayName("isEmpty()")
    class IsEmptyTests {

        @Test
        @DisplayName("should return true for empty declaration")
        void shouldReturnTrueForEmptyDeclaration() {
            assertThat(CovariateDeclaration.EMPTY.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should return false when day groups present")
        void shouldReturnFalseWhenDayGroupsPresent() {
            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(),
                    List.of(),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("should return false when time periods present")
        void shouldReturnFalseWhenTimePeriodsPresent() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(MORNING_PERIOD),
                    List.of(),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("should return false when region groups present")
        void shouldReturnFalseWhenRegionGroupsPresent() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("should return false when timezone enabled")
        void shouldReturnFalseWhenTimezoneEnabled() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(),
                    true,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("should return false when warmup > 0")
        void shouldReturnFalseWhenWarmupPositive() {
            var declaration = new CovariateDeclaration(
                    List.of(), List.of(), List.of(), false, Map.of(), new ServiceContractAttributes(5));

            assertThat(declaration.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("should return false when custom covariates present")
        void shouldReturnFalseWhenCustomCovariatesPresent() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Map.of("custom", CovariateCategory.OPERATIONAL),
                    ServiceContractAttributes.DEFAULT
            );

            assertThat(declaration.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("size()")
    class SizeTests {

        @Test
        @DisplayName("should return 0 for empty declaration")
        void shouldReturnZeroForEmptyDeclaration() {
            assertThat(CovariateDeclaration.EMPTY.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should count all covariates")
        void shouldCountAllCovariates() {
            var customMap = new LinkedHashMap<String, CovariateCategory>();
            customMap.put("custom1", CovariateCategory.OPERATIONAL);
            customMap.put("custom2", CovariateCategory.CONFIGURATION);
            customMap.put("custom3", CovariateCategory.DATA_STATE);

            var declaration = new CovariateDeclaration(
                    List.of(WEEKDAY_GROUP),
                    List.of(MORNING_PERIOD),
                    List.of(EU_REGION),
                    true,
                    customMap,
                    ServiceContractAttributes.DEFAULT
            );

            // 4 standard (day_of_week, time_of_day, region, timezone) + 3 custom = 7
            assertThat(declaration.size()).isEqualTo(7);
        }

        @Test
        @DisplayName("should count only active standard covariates")
        void shouldCountOnlyActiveStandardCovariates() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    true,
                    Map.of(),
                    ServiceContractAttributes.DEFAULT
            );

            // region + timezone = 2
            assertThat(declaration.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("should include warmup in count when positive")
        void shouldIncludeWarmupInCountWhenPositive() {
            var declaration = new CovariateDeclaration(
                    List.of(),
                    List.of(),
                    List.of(EU_REGION),
                    false,
                    Map.of(),
                    new ServiceContractAttributes(5)
            );

            // warmup + region = 2
            assertThat(declaration.size()).isEqualTo(2);
        }
    }
}
