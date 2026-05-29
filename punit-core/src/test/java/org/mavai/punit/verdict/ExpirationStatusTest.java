package org.mavai.punit.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExpirationStatus}.
 */
@DisplayName("ExpirationStatus")
class ExpirationStatusTest {

    @Nested
    @DisplayName("NoExpiration")
    class NoExpirationTests {

        @Test
        @DisplayName("should not require warning")
        void shouldNotRequireWarning() {
            var status = ExpirationStatus.noExpiration();
            
            assertThat(status.requiresWarning()).isFalse();
        }

        @Test
        @DisplayName("should not be expired")
        void shouldNotBeExpired() {
            var status = ExpirationStatus.noExpiration();
            
            assertThat(status.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("Valid")
    class ValidTests {

        @Test
        @DisplayName("should not require warning")
        void shouldNotRequireWarning() {
            var status = ExpirationStatus.valid(Duration.ofDays(20));
            
            assertThat(status.requiresWarning()).isFalse();
        }

        @Test
        @DisplayName("should not be expired")
        void shouldNotBeExpired() {
            var status = ExpirationStatus.valid(Duration.ofDays(20));
            
            assertThat(status.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should store remaining duration")
        void shouldStoreRemainingDuration() {
            var remaining = Duration.ofDays(20);
            var status = ExpirationStatus.valid(remaining);
            
            assertThat(status).isInstanceOf(ExpirationStatus.Valid.class);
            assertThat(((ExpirationStatus.Valid) status).remaining()).isEqualTo(remaining);
        }
    }

    @Nested
    @DisplayName("ExpiringSoon")
    class ExpiringSoonTests {

        @Test
        @DisplayName("should require warning")
        void shouldRequireWarning() {
            var status = ExpirationStatus.expiringSoon(Duration.ofDays(7), 0.23);
            
            assertThat(status.requiresWarning()).isTrue();
        }

        @Test
        @DisplayName("should not be expired")
        void shouldNotBeExpired() {
            var status = ExpirationStatus.expiringSoon(Duration.ofDays(7), 0.23);
            
            assertThat(status.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should store remaining duration and percent")
        void shouldStoreRemainingDurationAndPercent() {
            var remaining = Duration.ofDays(7);
            var percent = 0.23;
            var status = ExpirationStatus.expiringSoon(remaining, percent);
            
            assertThat(status).isInstanceOf(ExpirationStatus.ExpiringSoon.class);
            var expiringSoon = (ExpirationStatus.ExpiringSoon) status;
            assertThat(expiringSoon.remaining()).isEqualTo(remaining);
            assertThat(expiringSoon.remainingPercent()).isEqualTo(percent);
        }
    }

    @Nested
    @DisplayName("ExpiringImminently")
    class ExpiringImminentlyTests {

        @Test
        @DisplayName("should require warning")
        void shouldRequireWarning() {
            var status = ExpirationStatus.expiringImminently(Duration.ofDays(2), 0.07);
            
            assertThat(status.requiresWarning()).isTrue();
        }

        @Test
        @DisplayName("should not be expired")
        void shouldNotBeExpired() {
            var status = ExpirationStatus.expiringImminently(Duration.ofDays(2), 0.07);
            
            assertThat(status.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should store remaining duration and percent")
        void shouldStoreRemainingDurationAndPercent() {
            var remaining = Duration.ofDays(2);
            var percent = 0.07;
            var status = ExpirationStatus.expiringImminently(remaining, percent);
            
            assertThat(status).isInstanceOf(ExpirationStatus.ExpiringImminently.class);
            var imminent = (ExpirationStatus.ExpiringImminently) status;
            assertThat(imminent.remaining()).isEqualTo(remaining);
            assertThat(imminent.remainingPercent()).isEqualTo(percent);
        }
    }

    @Nested
    @DisplayName("Expired")
    class ExpiredTests {

        @Test
        @DisplayName("should require warning")
        void shouldRequireWarning() {
            var status = ExpirationStatus.expired(Duration.ofDays(5));
            
            assertThat(status.requiresWarning()).isTrue();
        }

        @Test
        @DisplayName("should be expired")
        void shouldBeExpired() {
            var status = ExpirationStatus.expired(Duration.ofDays(5));
            
            assertThat(status.isExpired()).isTrue();
        }

        @Test
        @DisplayName("should store expired ago duration")
        void shouldStoreExpiredAgoDuration() {
            var expiredAgo = Duration.ofDays(5);
            var status = ExpirationStatus.expired(expiredAgo);
            
            assertThat(status).isInstanceOf(ExpirationStatus.Expired.class);
            assertThat(((ExpirationStatus.Expired) status).expiredAgo()).isEqualTo(expiredAgo);
        }
    }

    @Nested
    @DisplayName("Sealed interface")
    class SealedInterfaceTests {

        @Test
        @DisplayName("should support exhaustive pattern matching")
        void shouldSupportExhaustivePatternMatching() {
            var statuses = new ExpirationStatus[] {
                ExpirationStatus.noExpiration(),
                ExpirationStatus.valid(Duration.ofDays(20)),
                ExpirationStatus.expiringSoon(Duration.ofDays(7), 0.23),
                ExpirationStatus.expiringImminently(Duration.ofDays(2), 0.07),
                ExpirationStatus.expired(Duration.ofDays(5))
            };
            
            for (var status : statuses) {
                String name = switch (status) {
                    case ExpirationStatus.NoExpiration() -> "NoExpiration";
                    case ExpirationStatus.Valid v -> "Valid";
                    case ExpirationStatus.ExpiringSoon s -> "ExpiringSoon";
                    case ExpirationStatus.ExpiringImminently i -> "ExpiringImminently";
                    case ExpirationStatus.Expired e -> "Expired";
                };
                assertThat(name).isNotEmpty();
            }
        }
    }
}

