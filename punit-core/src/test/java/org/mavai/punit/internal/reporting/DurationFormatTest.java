package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DurationFormat}.
 */
@DisplayName("DurationFormat")
class DurationFormatTest {

    @Nested
    @DisplayName("calendar()")
    class CalendarTests {

        @Test
        @DisplayName("formats single day")
        void formatsSingleDay() {
            assertThat(DurationFormat.calendar(Duration.ofDays(1)))
                .isEqualTo("1 day");
        }

        @Test
        @DisplayName("formats multiple days")
        void formatsMultipleDays() {
            assertThat(DurationFormat.calendar(Duration.ofDays(5)))
                .isEqualTo("5 days");
        }

        @Test
        @DisplayName("formats single hour")
        void formatsSingleHour() {
            assertThat(DurationFormat.calendar(Duration.ofHours(1)))
                .isEqualTo("1 hour");
        }

        @Test
        @DisplayName("formats multiple hours")
        void formatsMultipleHours() {
            assertThat(DurationFormat.calendar(Duration.ofHours(12)))
                .isEqualTo("12 hours");
        }

        @Test
        @DisplayName("formats single minute")
        void formatsSingleMinute() {
            assertThat(DurationFormat.calendar(Duration.ofMinutes(1)))
                .isEqualTo("1 minute");
        }

        @Test
        @DisplayName("formats multiple minutes")
        void formatsMultipleMinutes() {
            assertThat(DurationFormat.calendar(Duration.ofMinutes(45)))
                .isEqualTo("45 minutes");
        }

        @Test
        @DisplayName("formats sub-minute durations")
        void formatsSubMinuteDurations() {
            assertThat(DurationFormat.calendar(Duration.ofSeconds(30)))
                .isEqualTo("less than a minute");
        }

        @Test
        @DisplayName("handles null duration")
        void handlesNullDuration() {
            assertThat(DurationFormat.calendar(null))
                .isEqualTo("unknown");
        }

        @Test
        @DisplayName("uses most significant unit only")
        void usesMostSignificantUnit() {
            assertThat(DurationFormat.calendar(Duration.ofHours(25)))
                .isEqualTo("1 day");
        }
    }

    @Nested
    @DisplayName("execution()")
    class ExecutionTests {

        @Test
        @DisplayName("formats hours and minutes")
        void formatsHoursAndMinutes() {
            assertThat(DurationFormat.execution(5400_000))
                .isEqualTo("1h 30m");
        }

        @Test
        @DisplayName("formats minutes and seconds")
        void formatsMinutesAndSeconds() {
            assertThat(DurationFormat.execution(225_000))
                .isEqualTo("3m 45s");
        }

        @Test
        @DisplayName("formats seconds only")
        void formatsSecondsOnly() {
            assertThat(DurationFormat.execution(45_000))
                .isEqualTo("45s");
        }

        @Test
        @DisplayName("formats sub-second as <1s")
        void formatsSubSecond() {
            assertThat(DurationFormat.execution(500))
                .isEqualTo("<1s");
        }

        @Test
        @DisplayName("formats zero as <1s")
        void formatsZero() {
            assertThat(DurationFormat.execution(0))
                .isEqualTo("<1s");
        }

        @Test
        @DisplayName("formats exactly one second")
        void formatsOneSecond() {
            assertThat(DurationFormat.execution(1000))
                .isEqualTo("1s");
        }

        @Test
        @DisplayName("formats hours with zero minutes")
        void formatsHoursWithZeroMinutes() {
            assertThat(DurationFormat.execution(3600_000))
                .isEqualTo("1h 0m");
        }
    }
}
