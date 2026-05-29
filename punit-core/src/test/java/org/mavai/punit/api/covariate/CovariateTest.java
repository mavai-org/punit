package org.mavai.punit.api.covariate;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Covariate sealed hierarchy — declaration shape and validation")
class CovariateTest {

    @Nested
    @DisplayName("DayOfWeekCovariate")
    class DayOfWeekTests {

        @Test
        @DisplayName("accepts a non-empty list of non-empty partitions")
        void acceptsValidPartitions() {
            Covariate c = Covariate.dayOfWeek(List.of(
                    Set.of(SATURDAY, SUNDAY), Set.of(MONDAY)));
            assertThat(c.name()).isEqualTo("day_of_week");
            assertThat(c.category()).isEqualTo(CovariateCategory.TEMPORAL);
            assertThat(c).isInstanceOf(DayOfWeekCovariate.class);
        }

        @Test
        @DisplayName("rejects an empty partition list")
        void rejectsEmptyList() {
            assertThatThrownBy(() -> Covariate.dayOfWeek(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one partition");
        }

        @Test
        @DisplayName("rejects an empty inner set")
        void rejectsEmptyPartition() {
            assertThatThrownBy(() -> Covariate.dayOfWeek(List.of(Set.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-empty");
        }

        @Test
        @DisplayName("partitions are defensively copied")
        void partitionsAreImmutable() {
            DayOfWeekCovariate c = (DayOfWeekCovariate) Covariate.dayOfWeek(
                    List.of(Set.of(SATURDAY)));
            assertThatThrownBy(() -> c.partitions().add(Set.of(MONDAY)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("TimeOfDayCovariate")
    class TimeOfDayTests {

        @Test
        @DisplayName("accepts a non-empty list of non-blank periods")
        void acceptsValidPeriods() {
            Covariate c = Covariate.timeOfDay(List.of("08:00/2h", "16:00/3h"));
            assertThat(c.name()).isEqualTo("time_of_day");
            assertThat(c.category()).isEqualTo(CovariateCategory.TEMPORAL);
        }

        @Test
        @DisplayName("rejects an empty period list")
        void rejectsEmptyList() {
            assertThatThrownBy(() -> Covariate.timeOfDay(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one period");
        }

        @Test
        @DisplayName("rejects a blank period string")
        void rejectsBlankPeriod() {
            assertThatThrownBy(() -> Covariate.timeOfDay(List.of("08:00/2h", "")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-blank");
        }
    }

    @Nested
    @DisplayName("RegionCovariate")
    class RegionTests {

        @Test
        @DisplayName("accepts valid alpha-2 region codes and uppercases them")
        void acceptsValidRegions() {
            RegionCovariate c = (RegionCovariate) Covariate.region(List.of(
                    Set.of("fr", "DE"), Set.of("gb")));
            assertThat(c.name()).isEqualTo("region");
            assertThat(c.category()).isEqualTo(CovariateCategory.INFRASTRUCTURE);
            assertThat(c.partitions().get(0)).containsExactlyInAnyOrder("FR", "DE");
            assertThat(c.partitions().get(1)).containsExactly("GB");
        }

        @Test
        @DisplayName("rejects a 3-letter code")
        void rejectsInvalidLength() {
            assertThatThrownBy(() -> Covariate.region(List.of(Set.of("FRA"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a 2-letter ISO 3166-1 alpha-2 code");
        }

        @Test
        @DisplayName("rejects a code containing non-letters")
        void rejectsNonLetterCode() {
            assertThatThrownBy(() -> Covariate.region(List.of(Set.of("F1"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a 2-letter");
        }

        @Test
        @DisplayName("rejects an empty inner set")
        void rejectsEmptyPartition() {
            assertThatThrownBy(() -> Covariate.region(List.of(Set.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-empty");
        }
    }

    @Nested
    @DisplayName("TimezoneCovariate")
    class TimezoneTests {

        @Test
        @DisplayName("constructs with no parameters")
        void constructs() {
            Covariate c = Covariate.timezone();
            assertThat(c.name()).isEqualTo("timezone");
            assertThat(c.category()).isEqualTo(CovariateCategory.INFRASTRUCTURE);
        }

        @Test
        @DisplayName("two timezone covariates are equal (records)")
        void recordEquality() {
            assertThat(Covariate.timezone()).isEqualTo(Covariate.timezone());
        }
    }

    @Nested
    @DisplayName("CustomCovariate")
    class CustomTests {

        @Test
        @DisplayName("accepts a non-blank name and any category")
        void acceptsValid() {
            Covariate c = Covariate.custom("model_version", CovariateCategory.CONFIGURATION);
            assertThat(c.name()).isEqualTo("model_version");
            assertThat(c.category()).isEqualTo(CovariateCategory.CONFIGURATION);
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Covariate.custom("  ", CovariateCategory.CONFIGURATION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-blank");
        }

        @Test
        @DisplayName("rejects a null category")
        void rejectsNullCategory() {
            assertThatThrownBy(() -> Covariate.custom("foo", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("pattern match over the sealed hierarchy is exhaustive")
    void exhaustiveSwitch() {
        // The compiler enforces exhaustiveness at compile time. This
        // test exercises every branch at runtime to lock in the
        // behaviour we expect; if a sixth permit is ever added, this
        // switch will stop compiling — the intended signal.
        List<Covariate> all = List.of(
                Covariate.dayOfWeek(List.of(Set.of(MONDAY))),
                Covariate.timeOfDay(List.of("08:00/2h")),
                Covariate.region(List.of(Set.of("CH"))),
                Covariate.timezone(),
                Covariate.custom("flag", CovariateCategory.CONFIGURATION));

        for (Covariate c : all) {
            String label = switch (c) {
                case DayOfWeekCovariate d -> "dow:" + d.partitions().size();
                case TimeOfDayCovariate t -> "tod:" + t.periods().size();
                case RegionCovariate r    -> "reg:" + r.partitions().size();
                case TimezoneCovariate t  -> "tz";
                case CustomCovariate cc   -> "cus:" + cc.name();
            };
            assertThat(label).isNotBlank();
        }
    }
}
