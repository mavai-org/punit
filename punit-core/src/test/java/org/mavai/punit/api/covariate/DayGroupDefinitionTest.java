package org.mavai.punit.api.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DayGroupDefinition")
class DayGroupDefinitionTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("creates with valid days and label")
        void createsWithValidDaysAndLabel() {
            var group = new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND");

            assertThat(group.days()).containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(group.label()).isEqualTo("WEEKEND");
        }

        @Test
        @DisplayName("creates with single day")
        void createsWithSingleDay() {
            var group = new DayGroupDefinition(Set.of(DayOfWeek.MONDAY), "MONDAY");

            assertThat(group.days()).containsExactly(DayOfWeek.MONDAY);
        }

        @Test
        @DisplayName("rejects null days")
        void rejectsNullDays() {
            assertThatThrownBy(() -> new DayGroupDefinition(null, "label"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects empty days")
        void rejectsEmptyDays() {
            assertThatThrownBy(() -> new DayGroupDefinition(Set.of(), "label"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("rejects null label")
        void rejectsNullLabel() {
            assertThatThrownBy(() -> new DayGroupDefinition(Set.of(DayOfWeek.MONDAY), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank label")
        void rejectsBlankLabel() {
            assertThatThrownBy(() -> new DayGroupDefinition(Set.of(DayOfWeek.MONDAY), "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("days are defensively copied")
        void daysAreDefensivelyCopied() {
            var mutableDays = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
            var group = new DayGroupDefinition(mutableDays, "MON_TUE");
            mutableDays.add(DayOfWeek.WEDNESDAY);

            assertThat(group.days()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        @Test
        @DisplayName("returns true for day in group")
        void returnsTrueForDayInGroup() {
            var group = new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND");

            assertThat(group.contains(DayOfWeek.SATURDAY)).isTrue();
            assertThat(group.contains(DayOfWeek.SUNDAY)).isTrue();
        }

        @Test
        @DisplayName("returns false for day not in group")
        void returnsFalseForDayNotInGroup() {
            var group = new DayGroupDefinition(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "WEEKEND");

            assertThat(group.contains(DayOfWeek.MONDAY)).isFalse();
            assertThat(group.contains(DayOfWeek.FRIDAY)).isFalse();
        }
    }
}
