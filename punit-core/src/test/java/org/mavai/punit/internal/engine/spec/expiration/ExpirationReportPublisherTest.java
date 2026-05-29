package org.mavai.punit.internal.engine.spec.expiration;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.mavai.punit.verdict.ExpirationPolicy;
import org.mavai.punit.verdict.ExpirationStatus;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExpirationReportPublisher}.
 */
@DisplayName("ExpirationReportPublisher")
class ExpirationReportPublisherTest {

    @Nested
    @DisplayName("buildProperties()")
    class BuildPropertiesTests {

        @Test
        @DisplayName("should publish only status for NoExpiration")
        void shouldPublishOnlyStatusForNoExpiration() {
            var spec = createSpec(ExpirationPolicy.noExpiration());
            var status = ExpirationStatus.noExpiration();
            
            Map<String, String> props = ExpirationReportPublisher.buildProperties(spec, status);
            
            assertThat(props)
                .containsEntry("punit.baseline.expirationStatus", "NO_EXPIRATION")
                .hasSize(1);
        }

        @Test
        @DisplayName("should publish all properties for Valid status with expiration policy")
        void shouldPublishAllPropertiesForValid() {
            var endTime = Instant.parse("2026-01-10T14:00:00Z");
            var policy = ExpirationPolicy.of(30, endTime);
            var spec = createSpec(policy);
            var status = ExpirationStatus.valid(Duration.ofDays(20));
            
            Map<String, String> props = ExpirationReportPublisher.buildProperties(spec, status);
            
            assertThat(props)
                .containsEntry("punit.baseline.expirationStatus", "VALID")
                .containsEntry("punit.baseline.expiresInDays", "30")
                .containsEntry("punit.baseline.endTime", "2026-01-10T14:00:00Z")
                .containsKey("punit.baseline.expirationDate")
                .doesNotContainKey("punit.baseline.expiredAgoDays");
        }

        @Test
        @DisplayName("should publish ExpiringSoon status")
        void shouldPublishExpiringSoonStatus() {
            var endTime = Instant.now().minus(Duration.ofDays(23));
            var policy = ExpirationPolicy.of(30, endTime);
            var spec = createSpec(policy);
            var status = ExpirationStatus.expiringSoon(Duration.ofDays(7), 0.23);
            
            Map<String, String> props = ExpirationReportPublisher.buildProperties(spec, status);
            
            assertThat(props)
                .containsEntry("punit.baseline.expirationStatus", "EXPIRING_SOON");
        }

        @Test
        @DisplayName("should publish ExpiringImminently status")
        void shouldPublishExpiringImminentlyStatus() {
            var endTime = Instant.now().minus(Duration.ofDays(28));
            var policy = ExpirationPolicy.of(30, endTime);
            var spec = createSpec(policy);
            var status = ExpirationStatus.expiringImminently(Duration.ofDays(2), 0.07);
            
            Map<String, String> props = ExpirationReportPublisher.buildProperties(spec, status);
            
            assertThat(props)
                .containsEntry("punit.baseline.expirationStatus", "EXPIRING_IMMINENTLY");
        }

        @Test
        @DisplayName("should publish expiredAgoDays for Expired status")
        void shouldPublishExpiredAgoDaysForExpired() {
            var endTime = Instant.now().minus(Duration.ofDays(35));
            var policy = ExpirationPolicy.of(30, endTime);
            var spec = createSpec(policy);
            var status = ExpirationStatus.expired(Duration.ofDays(5));
            
            Map<String, String> props = ExpirationReportPublisher.buildProperties(spec, status);
            
            assertThat(props)
                .containsEntry("punit.baseline.expirationStatus", "EXPIRED")
                .containsEntry("punit.baseline.expiredAgoDays", "5");
        }

        @Test
        @DisplayName("should handle null spec gracefully")
        void shouldHandleNullSpec() {
            var status = ExpirationStatus.noExpiration();
            
            Map<String, String> props = ExpirationReportPublisher.buildProperties(null, status);
            
            assertThat(props)
                .containsEntry("punit.baseline.expirationStatus", "NO_EXPIRATION")
                .hasSize(1);
        }
    }

    @Nested
    @DisplayName("getStatusName()")
    class GetStatusNameTests {

        @Test
        @DisplayName("should return correct name for each status type")
        void shouldReturnCorrectNames() {
            assertThat(ExpirationReportPublisher.getStatusName(ExpirationStatus.noExpiration()))
                .isEqualTo("NO_EXPIRATION");
            assertThat(ExpirationReportPublisher.getStatusName(ExpirationStatus.valid(Duration.ofDays(20))))
                .isEqualTo("VALID");
            assertThat(ExpirationReportPublisher.getStatusName(ExpirationStatus.expiringSoon(Duration.ofDays(7), 0.23)))
                .isEqualTo("EXPIRING_SOON");
            assertThat(ExpirationReportPublisher.getStatusName(ExpirationStatus.expiringImminently(Duration.ofDays(2), 0.07)))
                .isEqualTo("EXPIRING_IMMINENTLY");
            assertThat(ExpirationReportPublisher.getStatusName(ExpirationStatus.expired(Duration.ofDays(5))))
                .isEqualTo("EXPIRED");
        }
    }

    private ExecutionSpecification createSpec(ExpirationPolicy policy) {
        return ExecutionSpecification.builder()
            .serviceContractId("test.usecase")
            .expirationPolicy(policy)
            .build();
    }
}

