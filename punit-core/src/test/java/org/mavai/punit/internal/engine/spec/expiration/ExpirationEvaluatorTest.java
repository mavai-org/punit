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
 * Tests for {@link ExpirationEvaluator}.
 */
@DisplayName("ExpirationEvaluator")
class ExpirationEvaluatorTest {

    @Nested
    @DisplayName("evaluate()")
    class EvaluateTests {

        @Test
        @DisplayName("should return NoExpiration for null spec")
        void shouldReturnNoExpirationForNullSpec() {
            ExpirationStatus status = ExpirationEvaluator.evaluate(null);
            
            assertThat(status).isInstanceOf(ExpirationStatus.NoExpiration.class);
        }

        @Test
        @DisplayName("should return NoExpiration for spec without policy")
        void shouldReturnNoExpirationForSpecWithoutPolicy() {
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .build();
            
            ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
            
            assertThat(status).isInstanceOf(ExpirationStatus.NoExpiration.class);
        }

        @Test
        @DisplayName("should return NoExpiration for spec with no-expiration policy")
        void shouldReturnNoExpirationForNoExpirationPolicy() {
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(ExpirationPolicy.noExpiration())
                .build();
            
            ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
            
            assertThat(status).isInstanceOf(ExpirationStatus.NoExpiration.class);
        }

        @Test
        @DisplayName("should return Valid for fresh baseline")
        void shouldReturnValidForFreshBaseline() {
            var endTime = Instant.now();
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(30, endTime)
                .build();
            
            ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
            
            assertThat(status).isInstanceOf(ExpirationStatus.Valid.class);
        }

        @Test
        @DisplayName("should return Expired for old baseline")
        void shouldReturnExpiredForOldBaseline() {
            var endTime = Instant.now().minus(Duration.ofDays(35));
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(30, endTime)
                .build();
            
            ExpirationStatus status = ExpirationEvaluator.evaluate(spec);
            
            assertThat(status).isInstanceOf(ExpirationStatus.Expired.class);
            assertThat(status.isExpired()).isTrue();
        }
    }

    @Nested
    @DisplayName("evaluateAt()")
    class EvaluateAtTests {

        @Test
        @DisplayName("should evaluate at specified time")
        void shouldEvaluateAtSpecifiedTime() {
            var endTime = Instant.parse("2026-01-01T00:00:00Z");
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(30, endTime)
                .build();
            
            // 35 days after end time - should be expired
            var checkTime = endTime.plus(Duration.ofDays(35));
            ExpirationStatus status = ExpirationEvaluator.evaluateAt(spec, checkTime);
            
            assertThat(status).isInstanceOf(ExpirationStatus.Expired.class);
        }
    }

    @Nested
    @DisplayName("Convenience methods")
    class ConvenienceMethodTests {

        @Test
        @DisplayName("hasExpiration() should return false for null spec")
        void hasExpirationShouldReturnFalseForNull() {
            assertThat(ExpirationEvaluator.hasExpiration(null)).isFalse();
        }

        @Test
        @DisplayName("hasExpiration() should return true for spec with policy")
        void hasExpirationShouldReturnTrueForPolicy() {
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(30, Instant.now())
                .build();
            
            assertThat(ExpirationEvaluator.hasExpiration(spec)).isTrue();
        }

        @Test
        @DisplayName("isExpired() should return false for fresh baseline")
        void isExpiredShouldReturnFalseForFresh() {
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(30, Instant.now())
                .build();
            
            assertThat(ExpirationEvaluator.isExpired(spec)).isFalse();
        }

        @Test
        @DisplayName("isExpired() should return true for old baseline")
        void isExpiredShouldReturnTrueForOld() {
            var endTime = Instant.now().minus(Duration.ofDays(35));
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(30, endTime)
                .build();
            
            assertThat(ExpirationEvaluator.isExpired(spec)).isTrue();
        }

        @Test
        @DisplayName("requiresWarning() should return false for Valid baseline")
        void requiresWarningShouldReturnFalseForValid() {
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(30, Instant.now())
                .build();
            
            assertThat(ExpirationEvaluator.requiresWarning(spec)).isFalse();
        }

        @Test
        @DisplayName("requiresWarning() should return true for expired baseline")
        void requiresWarningShouldReturnTrueForExpired() {
            var endTime = Instant.now().minus(Duration.ofDays(35));
            var spec = ExecutionSpecification.builder()
                .serviceContractId("test.usecase")
                .expirationPolicy(30, endTime)
                .build();
            
            assertThat(ExpirationEvaluator.requiresWarning(spec)).isTrue();
        }
    }
}

