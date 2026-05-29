package org.mavai.punit.internal.engine.spec.expiration;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import org.mavai.punit.verdict.ExpirationPolicy;
import org.mavai.punit.verdict.ExpirationStatus;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExpirationWarningRenderer}.
 */
@DisplayName("ExpirationWarningRenderer")
class ExpirationWarningRendererTest {

    @Nested
    @DisplayName("render()")
    class RenderTests {

        @Test
        @DisplayName("should return empty string for NoExpiration status")
        void shouldReturnEmptyForNoExpiration() {
            var spec = createSpec(ExpirationPolicy.noExpiration());
            var status = ExpirationStatus.noExpiration();
            
            String result = ExpirationWarningRenderer.render(spec, status);
            
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty string for Valid status")
        void shouldReturnEmptyForValid() {
            var spec = createSpec(ExpirationPolicy.of(30, Instant.now()));
            var status = ExpirationStatus.valid(Duration.ofDays(20));
            
            String result = ExpirationWarningRenderer.render(spec, status);
            
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should render ExpiringSoon with informational message")
        void shouldRenderExpiringSoon() {
            var endTime = Instant.now().minus(Duration.ofDays(23));
            var policy = ExpirationPolicy.of(30, endTime);
            var spec = createSpec(policy);
            var status = ExpirationStatus.expiringSoon(Duration.ofDays(7), 0.23);
            
            var result = ExpirationWarningRenderer.renderWarning(spec, status);
            
            assertThat(result.title()).isEqualTo("BASELINE EXPIRES SOON");
            assertThat(result.body()).contains("7 days");
        }

        @Test
        @DisplayName("should render ExpiringImminently with warning message")
        void shouldRenderExpiringImminently() {
            var endTime = Instant.now().minus(Duration.ofDays(28));
            var policy = ExpirationPolicy.of(30, endTime);
            var spec = createSpec(policy);
            var status = ExpirationStatus.expiringImminently(Duration.ofDays(2), 0.07);
            
            String result = ExpirationWarningRenderer.render(spec, status);
            
            assertThat(result)
                .contains("BASELINE EXPIRING IMMINENTLY")
                .contains("2 days")
                .contains("Consider scheduling a MEASURE experiment");
        }

        @Test
        @DisplayName("should render Expired with prominent warning and remediation")
        void shouldRenderExpired() {
            var endTime = Instant.now().minus(Duration.ofDays(35));
            var policy = ExpirationPolicy.of(30, endTime);
            var spec = createSpec(policy);
            var status = ExpirationStatus.expired(Duration.ofDays(5));
            
            var result = ExpirationWarningRenderer.renderWarning(spec, status);
            
            assertThat(result.title()).isEqualTo("BASELINE EXPIRED");
            assertThat(result.body())
                .contains("Validity period:     30 days")
                .contains("5 days ago")
                .contains("potentially stale empirical data");
        }
    }

    @Nested
    @DisplayName("formatDuration()")
    class FormatDurationTests {

        @Test
        @DisplayName("should format days")
        void shouldFormatDays() {
            assertThat(ExpirationWarningRenderer.formatDuration(Duration.ofDays(1)))
                .isEqualTo("1 day");
            assertThat(ExpirationWarningRenderer.formatDuration(Duration.ofDays(5)))
                .isEqualTo("5 days");
        }

        @Test
        @DisplayName("should format hours when less than a day")
        void shouldFormatHours() {
            assertThat(ExpirationWarningRenderer.formatDuration(Duration.ofHours(1)))
                .isEqualTo("1 hour");
            assertThat(ExpirationWarningRenderer.formatDuration(Duration.ofHours(12)))
                .isEqualTo("12 hours");
        }

        @Test
        @DisplayName("should format minutes when less than an hour")
        void shouldFormatMinutes() {
            assertThat(ExpirationWarningRenderer.formatDuration(Duration.ofMinutes(1)))
                .isEqualTo("1 minute");
            assertThat(ExpirationWarningRenderer.formatDuration(Duration.ofMinutes(45)))
                .isEqualTo("45 minutes");
        }

        @Test
        @DisplayName("should format very short durations")
        void shouldFormatVeryShortDurations() {
            assertThat(ExpirationWarningRenderer.formatDuration(Duration.ofSeconds(30)))
                .isEqualTo("less than a minute");
        }

        @Test
        @DisplayName("should handle null duration")
        void shouldHandleNullDuration() {
            assertThat(ExpirationWarningRenderer.formatDuration(null))
                .isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("formatInstant()")
    class FormatInstantTests {

        @Test
        @DisplayName("should format instant as readable date")
        void shouldFormatInstant() {
            var instant = Instant.parse("2026-01-10T14:30:00Z");
            
            String result = ExpirationWarningRenderer.formatInstant(instant);
            
            // Should contain date components (exact format depends on system timezone)
            assertThat(result).contains("2026");
            assertThat(result).matches(".*\\d{4}-\\d{2}-\\d{2}.*");
        }

        @Test
        @DisplayName("should handle null instant")
        void shouldHandleNullInstant() {
            assertThat(ExpirationWarningRenderer.formatInstant(null))
                .isEqualTo("unknown");
        }
    }

    private ExecutionSpecification createSpec(ExpirationPolicy policy) {
        return ExecutionSpecification.builder()
            .serviceContractId("test.usecase")
            .expirationPolicy(policy)
            .build();
    }
}

