package org.mavai.punit.api.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RegionGroupDefinition")
class RegionGroupDefinitionTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("creates with valid regions and label")
        void createsWithValidRegionsAndLabel() {
            var group = new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE");

            assertThat(group.regions()).containsExactlyInAnyOrder("FR", "DE");
            assertThat(group.label()).isEqualTo("EU_CORE");
        }

        @Test
        @DisplayName("normalizes regions to uppercase")
        void normalizesRegionsToUppercase() {
            var group = new RegionGroupDefinition(Set.of("fr", "de"), "EU_CORE");

            assertThat(group.regions()).containsExactlyInAnyOrder("FR", "DE");
        }

        @Test
        @DisplayName("rejects null regions")
        void rejectsNullRegions() {
            assertThatThrownBy(() -> new RegionGroupDefinition(null, "label"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects empty regions")
        void rejectsEmptyRegions() {
            assertThatThrownBy(() -> new RegionGroupDefinition(Set.of(), "label"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("rejects blank label")
        void rejectsBlankLabel() {
            assertThatThrownBy(() -> new RegionGroupDefinition(Set.of("US"), "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("regions are defensively copied")
        void regionsAreDefensivelyCopied() {
            var mutableRegions = new HashSet<>(Set.of("FR", "DE"));
            var group = new RegionGroupDefinition(mutableRegions, "EU");
            mutableRegions.add("GB");

            assertThat(group.regions()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        @Test
        @DisplayName("returns true for region in group")
        void returnsTrueForRegionInGroup() {
            var group = new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE");

            assertThat(group.contains("FR")).isTrue();
            assertThat(group.contains("DE")).isTrue();
        }

        @Test
        @DisplayName("returns false for region not in group")
        void returnsFalseForRegionNotInGroup() {
            var group = new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE");

            assertThat(group.contains("GB")).isFalse();
        }

        @Test
        @DisplayName("matching is case-insensitive")
        void matchingIsCaseInsensitive() {
            var group = new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE");

            assertThat(group.contains("fr")).isTrue();
            assertThat(group.contains("Fr")).isTrue();
        }
    }
}
