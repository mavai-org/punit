package org.mavai.punit.internal.engine.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PeriodMatcher — HH:mm/Nh parsing and matching")
class PeriodMatcherTest {

    @Nested
    @DisplayName("parse")
    class ParseTests {

        @Test
        @DisplayName("accepts HH:mm/Nh, HH:mm/Nm, and HH:mm/NhMm forms")
        void acceptsAllForms() {
            List<PeriodMatcher.Parsed> parsed = PeriodMatcher.parse(
                    List.of("08:00/2h", "12:00/30m", "16:00/1h15m"));

            assertThat(parsed).hasSize(3);
            assertThat(parsed.get(0).durationMinutes()).isEqualTo(120);
            assertThat(parsed.get(1).durationMinutes()).isEqualTo(30);
            assertThat(parsed.get(2).durationMinutes()).isEqualTo(75);
        }

        @Test
        @DisplayName("rejects malformed period strings")
        void rejectsMalformed() {
            assertThatThrownBy(() -> PeriodMatcher.parse(List.of("8:00/2h")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid time period format");
        }

        @Test
        @DisplayName("rejects out-of-range hour")
        void rejectsBadHour() {
            assertThatThrownBy(() -> PeriodMatcher.parse(List.of("24:00/1h")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid hour");
        }

        @Test
        @DisplayName("rejects zero-duration period")
        void rejectsZeroDuration() {
            assertThatThrownBy(() -> PeriodMatcher.parse(List.of("08:00/0h")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duration must be positive");
        }

        @Test
        @DisplayName("rejects period that crosses midnight")
        void rejectsMidnightCross() {
            assertThatThrownBy(() -> PeriodMatcher.parse(List.of("23:00/2h")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("crosses midnight");
        }

        @Test
        @DisplayName("rejects overlapping periods")
        void rejectsOverlap() {
            assertThatThrownBy(() -> PeriodMatcher.parse(List.of("08:00/2h", "09:00/2h")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("overlap");
        }

        @Test
        @DisplayName("accepts back-to-back periods (touching but not overlapping)")
        void acceptsContiguous() {
            // 08:00–10:00 ends exactly when 10:00–12:00 starts; OK.
            List<PeriodMatcher.Parsed> parsed = PeriodMatcher.parse(
                    List.of("08:00/2h", "10:00/2h"));
            assertThat(parsed).hasSize(2);
        }
    }

    @Nested
    @DisplayName("match")
    class MatchTests {

        private final List<PeriodMatcher.Parsed> periods =
                PeriodMatcher.parse(List.of("08:00/2h", "16:00/3h"));

        @Test
        @DisplayName("returns the label of a period containing now")
        void inside() {
            assertThat(PeriodMatcher.match(LocalTime.of(9, 30), periods))
                    .contains("08:00/2h");
        }

        @Test
        @DisplayName("returns empty when no period contains now")
        void outside() {
            assertThat(PeriodMatcher.match(LocalTime.of(12, 0), periods))
                    .isEmpty();
        }

        @Test
        @DisplayName("treats period as half-open: end is exclusive")
        void endExclusive() {
            // 08:00/2h ends at 10:00 (exclusive).
            assertThat(PeriodMatcher.match(LocalTime.of(10, 0), periods))
                    .isEmpty();
            assertThat(PeriodMatcher.match(LocalTime.of(9, 59), periods))
                    .contains("08:00/2h");
        }

        @Test
        @DisplayName("treats period as half-open: start is inclusive")
        void startInclusive() {
            assertThat(PeriodMatcher.match(LocalTime.of(8, 0), periods))
                    .contains("08:00/2h");
        }
    }
}
