package org.mavai.punit.api.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TimePeriodDefinition")
class TimePeriodDefinitionTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("creates with valid parameters in hours")
        void createsWithValidParametersInHours() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 120);

            assertThat(period.start()).isEqualTo(LocalTime.of(8, 0));
            assertThat(period.durationMinutes()).isEqualTo(120);
        }

        @Test
        @DisplayName("creates with sub-hour duration")
        void createsWithSubHourDuration() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 30);

            assertThat(period.start()).isEqualTo(LocalTime.of(8, 0));
            assertThat(period.durationMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("creates with mixed hours and minutes duration")
        void createsWithMixedDuration() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 150);

            assertThat(period.start()).isEqualTo(LocalTime.of(8, 0));
            assertThat(period.durationMinutes()).isEqualTo(150);
        }

        @Test
        @DisplayName("rejects null start")
        void rejectsNullStart() {
            assertThatThrownBy(() -> new TimePeriodDefinition(null, 120))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects zero duration with format guidance")
        void rejectsZeroDuration() {
            assertThatThrownBy(() -> new TimePeriodDefinition(LocalTime.of(8, 0), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive")
                    .hasMessageContaining("got: 0")
                    .hasMessageContaining("HH:mm/Nh")
                    .hasMessageContaining("HH:mm/Nm")
                    .hasMessageContaining("HH:mm/NhMm");
        }

        @Test
        @DisplayName("rejects negative duration with format guidance")
        void rejectsNegativeDuration() {
            assertThatThrownBy(() -> new TimePeriodDefinition(LocalTime.of(8, 0), -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive")
                    .hasMessageContaining("got: -1")
                    .hasMessageContaining("HH:mm/Nh")
                    .hasMessageContaining("HH:mm/Nm")
                    .hasMessageContaining("HH:mm/NhMm");
        }

        @Test
        @DisplayName("rejects midnight crossing with format guidance")
        void rejectsMidnightCrossing() {
            assertThatThrownBy(() -> new TimePeriodDefinition(LocalTime.of(23, 0), 120))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("midnight")
                    .hasMessageContaining("23:00")
                    .hasMessageContaining("120m")
                    .hasMessageContaining("HH:mm/Nh")
                    .hasMessageContaining("HH:mm/Nm")
                    .hasMessageContaining("HH:mm/NhMm");
        }

        @Test
        @DisplayName("accepts period ending exactly at midnight")
        void acceptsPeriodEndingAtMidnight() {
            var period = new TimePeriodDefinition(LocalTime.of(23, 0), 60);

            assertThat(period.end()).isEqualTo(LocalTime.MIDNIGHT);
        }
    }

    @Nested
    @DisplayName("label()")
    class LabelTests {

        @Test
        @DisplayName("formats whole hours")
        void formatsWholeHours() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 120);

            assertThat(period.label()).isEqualTo("08:00/2h");
        }

        @Test
        @DisplayName("formats minutes only")
        void formatsMinutesOnly() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 30);

            assertThat(period.label()).isEqualTo("08:00/30m");
        }

        @Test
        @DisplayName("formats mixed hours and minutes")
        void formatsMixedHoursAndMinutes() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 150);

            assertThat(period.label()).isEqualTo("08:00/2h30m");
        }

        @Test
        @DisplayName("formats non-zero start minutes")
        void formatsNonZeroStartMinutes() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 30), 90);

            assertThat(period.label()).isEqualTo("08:30/1h30m");
        }

        @Test
        @DisplayName("formats midnight start")
        void formatsMidnightStart() {
            var period = new TimePeriodDefinition(LocalTime.of(0, 0), 480);

            assertThat(period.label()).isEqualTo("00:00/8h");
        }
    }

    @Nested
    @DisplayName("end()")
    class EndTests {

        @Test
        @DisplayName("computes end time from start + duration in hours")
        void computesEndTimeFromHours() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 120);

            assertThat(period.end()).isEqualTo(LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("computes end time from start + sub-hour duration")
        void computesEndTimeFromMinutes() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 30);

            assertThat(period.end()).isEqualTo(LocalTime.of(8, 30));
        }

        @Test
        @DisplayName("computes end time from start + mixed duration")
        void computesEndTimeFromMixedDuration() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 150);

            assertThat(period.end()).isEqualTo(LocalTime.of(10, 30));
        }

        @Test
        @DisplayName("handles non-zero start minutes")
        void handlesNonZeroMinutes() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 30), 180);

            assertThat(period.end()).isEqualTo(LocalTime.of(11, 30));
        }
    }

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        @Test
        @DisplayName("start is inclusive")
        void startIsInclusive() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 120);

            assertThat(period.contains(LocalTime.of(8, 0))).isTrue();
        }

        @Test
        @DisplayName("end is exclusive")
        void endIsExclusive() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 120);

            assertThat(period.contains(LocalTime.of(10, 0))).isFalse();
        }

        @Test
        @DisplayName("time within period matches")
        void timeWithinPeriodMatches() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 120);

            assertThat(period.contains(LocalTime.of(9, 30))).isTrue();
        }

        @Test
        @DisplayName("time before period does not match")
        void timeBeforePeriodDoesNotMatch() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 120);

            assertThat(period.contains(LocalTime.of(7, 59))).isFalse();
        }

        @Test
        @DisplayName("time after period does not match")
        void timeAfterPeriodDoesNotMatch() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 120);

            assertThat(period.contains(LocalTime.of(10, 1))).isFalse();
        }

        @Test
        @DisplayName("time within sub-hour period matches")
        void timeWithinSubHourPeriodMatches() {
            var period = new TimePeriodDefinition(LocalTime.of(8, 0), 30);

            assertThat(period.contains(LocalTime.of(8, 15))).isTrue();
            assertThat(period.contains(LocalTime.of(8, 30))).isFalse();
        }
    }
}
