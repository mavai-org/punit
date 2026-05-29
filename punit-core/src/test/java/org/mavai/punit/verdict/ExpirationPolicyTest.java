package org.mavai.punit.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExpirationPolicy}.
 */
@DisplayName("ExpirationPolicy")
class ExpirationPolicyTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("should accept expiresInDays = 0 (no expiration)")
        void shouldAcceptZeroExpiresInDays() {
            var policy = new ExpirationPolicy(0, null);
            
            assertThat(policy.expiresInDays()).isEqualTo(0);
            assertThat(policy.hasExpiration()).isFalse();
        }

        @Test
        @DisplayName("should accept positive expiresInDays with end time")
        void shouldAcceptPositiveExpiresInDays() {
            var endTime = Instant.now();
            var policy = new ExpirationPolicy(30, endTime);
            
            assertThat(policy.expiresInDays()).isEqualTo(30);
            assertThat(policy.baselineEndTime()).isEqualTo(endTime);
            assertThat(policy.hasExpiration()).isTrue();
        }

        @Test
        @DisplayName("should reject negative expiresInDays")
        void shouldRejectNegativeExpiresInDays() {
            assertThatThrownBy(() -> new ExpirationPolicy(-1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresInDays must be non-negative");
        }

        @Test
        @DisplayName("should reject null baselineEndTime when expiresInDays > 0")
        void shouldRejectNullEndTimeWhenExpiring() {
            assertThatThrownBy(() -> new ExpirationPolicy(30, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baselineEndTime");
        }
    }

    @Nested
    @DisplayName("expirationTime()")
    class ExpirationTimeTests {

        @Test
        @DisplayName("should return empty for no expiration")
        void shouldReturnEmptyForNoExpiration() {
            var policy = ExpirationPolicy.noExpiration();
            
            assertThat(policy.expirationTime()).isEmpty();
        }

        @Test
        @DisplayName("should compute correct expiration time")
        void shouldComputeCorrectExpirationTime() {
            var endTime = Instant.parse("2026-01-10T14:00:00Z");
            var policy = ExpirationPolicy.of(30, endTime);
            
            var expiration = policy.expirationTime();
            
            assertThat(expiration).isPresent();
            assertThat(expiration.get()).isEqualTo(Instant.parse("2026-02-09T14:00:00Z"));
        }
    }

    @Nested
    @DisplayName("evaluateAt()")
    class EvaluateAtTests {

        @Test
        @DisplayName("should return NoExpiration for policy with no expiration")
        void shouldReturnNoExpirationForNoExpirationPolicy() {
            var policy = ExpirationPolicy.noExpiration();
            
            var status = policy.evaluateAt(Instant.now());
            
            assertThat(status).isInstanceOf(ExpirationStatus.NoExpiration.class);
            assertThat(status.requiresWarning()).isFalse();
            assertThat(status.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should return Valid when > 25% time remaining")
        void shouldReturnValidWhenPlentyOfTimeRemaining() {
            var endTime = Instant.now();
            var policy = ExpirationPolicy.of(30, endTime);
            
            // Check immediately after baseline creation (100% remaining)
            var status = policy.evaluateAt(endTime);
            
            assertThat(status).isInstanceOf(ExpirationStatus.Valid.class);
            assertThat(status.requiresWarning()).isFalse();
            assertThat(status.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should return ExpiringSoon when <= 25% time remaining")
        void shouldReturnExpiringSoonWhen25PercentRemaining() {
            var endTime = Instant.now().minus(Duration.ofDays(23)); // 7 days left of 30
            var policy = ExpirationPolicy.of(30, endTime);
            
            var status = policy.evaluateAt(Instant.now());
            
            assertThat(status).isInstanceOf(ExpirationStatus.ExpiringSoon.class);
            assertThat(status.requiresWarning()).isTrue();
            assertThat(status.isExpired()).isFalse();
            
            var expiringSoon = (ExpirationStatus.ExpiringSoon) status;
            assertThat(expiringSoon.remainingPercent()).isLessThanOrEqualTo(0.25);
            assertThat(expiringSoon.remainingPercent()).isGreaterThan(0.10);
        }

        @Test
        @DisplayName("should return ExpiringImminently when <= 10% time remaining")
        void shouldReturnExpiringImminentlyWhen10PercentRemaining() {
            var endTime = Instant.now().minus(Duration.ofDays(28)); // 2 days left of 30
            var policy = ExpirationPolicy.of(30, endTime);
            
            var status = policy.evaluateAt(Instant.now());
            
            assertThat(status).isInstanceOf(ExpirationStatus.ExpiringImminently.class);
            assertThat(status.requiresWarning()).isTrue();
            assertThat(status.isExpired()).isFalse();
            
            var imminent = (ExpirationStatus.ExpiringImminently) status;
            assertThat(imminent.remainingPercent()).isLessThanOrEqualTo(0.10);
        }

        @Test
        @DisplayName("should return Expired when past expiration date")
        void shouldReturnExpiredWhenPastExpirationDate() {
            var endTime = Instant.now().minus(Duration.ofDays(35)); // Expired 5 days ago
            var policy = ExpirationPolicy.of(30, endTime);
            
            var status = policy.evaluateAt(Instant.now());
            
            assertThat(status).isInstanceOf(ExpirationStatus.Expired.class);
            assertThat(status.requiresWarning()).isTrue();
            assertThat(status.isExpired()).isTrue();
            
            var expired = (ExpirationStatus.Expired) status;
            assertThat(expired.expiredAgo().toDays()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("noExpiration() should create policy with no expiration")
        void noExpirationShouldCreateNoExpirationPolicy() {
            var policy = ExpirationPolicy.noExpiration();
            
            assertThat(policy.expiresInDays()).isEqualTo(0);
            assertThat(policy.hasExpiration()).isFalse();
        }

        @Test
        @DisplayName("of() should create policy with given values")
        void ofShouldCreatePolicyWithGivenValues() {
            var endTime = Instant.now();
            var policy = ExpirationPolicy.of(14, endTime);
            
            assertThat(policy.expiresInDays()).isEqualTo(14);
            assertThat(policy.baselineEndTime()).isEqualTo(endTime);
            assertThat(policy.hasExpiration()).isTrue();
        }
    }
}

