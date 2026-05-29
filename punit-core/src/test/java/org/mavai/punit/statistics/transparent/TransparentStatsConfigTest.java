package org.mavai.punit.statistics.transparent;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TransparentStatsConfig}.
 */
class TransparentStatsConfigTest {

    private String originalTransparentProp;
    private String originalDetailLevelProp;
    private String originalFormatProp;

    @BeforeEach
    void saveOriginalProperties() {
        originalTransparentProp = System.getProperty(TransparentStatsConfig.PROP_TRANSPARENT);
        originalDetailLevelProp = System.getProperty(TransparentStatsConfig.PROP_DETAIL_LEVEL);
        originalFormatProp = System.getProperty(TransparentStatsConfig.PROP_FORMAT);
    }

    @AfterEach
    void restoreOriginalProperties() {
        restoreProperty(TransparentStatsConfig.PROP_TRANSPARENT, originalTransparentProp);
        restoreProperty(TransparentStatsConfig.PROP_DETAIL_LEVEL, originalDetailLevelProp);
        restoreProperty(TransparentStatsConfig.PROP_FORMAT, originalFormatProp);
    }

    private void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        @DisplayName("returns disabled by default when no configuration is set")
        void defaultsToDisabled() {
            System.clearProperty(TransparentStatsConfig.PROP_TRANSPARENT);
            
            TransparentStatsConfig config = TransparentStatsConfig.resolve();
            
            assertThat(config.enabled()).isFalse();
        }

        @Test
        @DisplayName("enables when system property is true")
        void enabledViaSystemProperty() {
            System.setProperty(TransparentStatsConfig.PROP_TRANSPARENT, "true");
            
            TransparentStatsConfig config = TransparentStatsConfig.resolve();
            
            assertThat(config.enabled()).isTrue();
        }

        @Test
        @DisplayName("disables when system property is false")
        void disabledViaSystemProperty() {
            System.setProperty(TransparentStatsConfig.PROP_TRANSPARENT, "false");
            
            TransparentStatsConfig config = TransparentStatsConfig.resolve();
            
            assertThat(config.enabled()).isFalse();
        }

        @Test
        @DisplayName("annotation override takes precedence over system property")
        void annotationOverridesTakesPrecedence() {
            System.setProperty(TransparentStatsConfig.PROP_TRANSPARENT, "false");
            
            TransparentStatsConfig config = TransparentStatsConfig.resolve(Boolean.TRUE);
            
            assertThat(config.enabled()).isTrue();
        }

        @Test
        @DisplayName("uses default detail level when not specified")
        void defaultDetailLevel() {
            TransparentStatsConfig config = TransparentStatsConfig.resolve();
            
            assertThat(config.detailLevel()).isEqualTo(TransparentStatsConfig.DetailLevel.VERBOSE);
        }

        @Test
        @DisplayName("resolves detail level from system property")
        void detailLevelFromSystemProperty() {
            System.setProperty(TransparentStatsConfig.PROP_DETAIL_LEVEL, "VERBOSE");
            
            TransparentStatsConfig config = TransparentStatsConfig.resolve();
            
            assertThat(config.detailLevel()).isEqualTo(TransparentStatsConfig.DetailLevel.VERBOSE);
        }

        @Test
        @DisplayName("handles case-insensitive detail level")
        void detailLevelCaseInsensitive() {
            System.setProperty(TransparentStatsConfig.PROP_DETAIL_LEVEL, "summary");
            
            TransparentStatsConfig config = TransparentStatsConfig.resolve();
            
            assertThat(config.detailLevel()).isEqualTo(TransparentStatsConfig.DetailLevel.SUMMARY);
        }

        @Test
        @DisplayName("uses default format when not specified")
        void defaultFormat() {
            TransparentStatsConfig config = TransparentStatsConfig.resolve();
            
            assertThat(config.format()).isEqualTo(TransparentStatsConfig.OutputFormat.CONSOLE);
        }

        @Test
        @DisplayName("resolves format from system property")
        void formatFromSystemProperty() {
            System.setProperty(TransparentStatsConfig.PROP_FORMAT, "JSON");
            
            TransparentStatsConfig config = TransparentStatsConfig.resolve();
            
            assertThat(config.format()).isEqualTo(TransparentStatsConfig.OutputFormat.JSON);
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("disabled() returns disabled configuration")
        void disabledFactoryMethod() {
            TransparentStatsConfig config = TransparentStatsConfig.disabled();
            
            assertThat(config.enabled()).isFalse();
            assertThat(config.detailLevel()).isEqualTo(TransparentStatsConfig.DetailLevel.VERBOSE);
            assertThat(config.format()).isEqualTo(TransparentStatsConfig.OutputFormat.CONSOLE);
        }

        @Test
        @DisplayName("of() creates configuration with specified values")
        void ofFactoryMethod() {
            TransparentStatsConfig config = TransparentStatsConfig.of(
                    true, 
                    TransparentStatsConfig.DetailLevel.VERBOSE, 
                    TransparentStatsConfig.OutputFormat.MARKDOWN
            );
            
            assertThat(config.enabled()).isTrue();
            assertThat(config.detailLevel()).isEqualTo(TransparentStatsConfig.DetailLevel.VERBOSE);
            assertThat(config.format()).isEqualTo(TransparentStatsConfig.OutputFormat.MARKDOWN);
        }
    }

    @Nested
    @DisplayName("supportsUnicode()")
    class UnicodeDetectionTests {

        @Test
        @DisplayName("returns a boolean value")
        void returnsBoolean() {
            // This test just verifies the method doesn't throw
            // The actual value depends on the runtime environment
            boolean result = TransparentStatsConfig.supportsUnicode();
            assertThat(result).isIn(true, false);
        }
    }
}

