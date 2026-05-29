package org.mavai.punit.internal.engine.spec.expiration;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.mavai.punit.verdict.ExpirationStatus;
import org.mavai.punit.statistics.transparent.TransparentStatsConfig.DetailLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WarningLevel}.
 */
@DisplayName("WarningLevel")
class WarningLevelTest {

    @Nested
    @DisplayName("forStatus()")
    class ForStatusTests {

        @Test
        @DisplayName("should return ALWAYS for Expired status")
        void shouldReturnAlwaysForExpired() {
            var status = ExpirationStatus.expired(Duration.ofDays(5));
            
            assertThat(WarningLevel.forStatus(status)).isEqualTo(WarningLevel.ALWAYS);
        }

        @Test
        @DisplayName("should return NORMAL for ExpiringImminently status")
        void shouldReturnNormalForExpiringImminently() {
            var status = ExpirationStatus.expiringImminently(Duration.ofDays(2), 0.07);
            
            assertThat(WarningLevel.forStatus(status)).isEqualTo(WarningLevel.NORMAL);
        }

        @Test
        @DisplayName("should return VERBOSE for ExpiringSoon status")
        void shouldReturnVerboseForExpiringSoon() {
            var status = ExpirationStatus.expiringSoon(Duration.ofDays(7), 0.23);
            
            assertThat(WarningLevel.forStatus(status)).isEqualTo(WarningLevel.VERBOSE);
        }

        @Test
        @DisplayName("should return null for Valid status")
        void shouldReturnNullForValid() {
            var status = ExpirationStatus.valid(Duration.ofDays(20));
            
            assertThat(WarningLevel.forStatus(status)).isNull();
        }

        @Test
        @DisplayName("should return null for NoExpiration status")
        void shouldReturnNullForNoExpiration() {
            var status = ExpirationStatus.noExpiration();
            
            assertThat(WarningLevel.forStatus(status)).isNull();
        }
    }

    @Nested
    @DisplayName("shouldShow()")
    class ShouldShowTests {

        @Test
        @DisplayName("ALWAYS should show at any detail level")
        void alwaysShouldShowAtAnyDetailLevel() {
            assertThat(WarningLevel.ALWAYS.shouldShow(DetailLevel.SUMMARY)).isTrue();
            assertThat(WarningLevel.ALWAYS.shouldShow(DetailLevel.VERBOSE)).isTrue();
        }

        @Test
        @DisplayName("NORMAL should show at any detail level")
        void normalShouldShowAtAnyDetailLevel() {
            assertThat(WarningLevel.NORMAL.shouldShow(DetailLevel.SUMMARY)).isTrue();
            assertThat(WarningLevel.NORMAL.shouldShow(DetailLevel.VERBOSE)).isTrue();
        }

        @Test
        @DisplayName("VERBOSE should only show at VERBOSE detail level")
        void verboseShouldOnlyShowAtVerboseDetailLevel() {
            assertThat(WarningLevel.VERBOSE.shouldShow(DetailLevel.SUMMARY)).isFalse();
            assertThat(WarningLevel.VERBOSE.shouldShow(DetailLevel.VERBOSE)).isTrue();
        }
    }
}

