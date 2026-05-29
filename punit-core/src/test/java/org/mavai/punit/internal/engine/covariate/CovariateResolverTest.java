package org.mavai.punit.internal.engine.covariate;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.mavai.punit.api.covariate.CovariateCategory;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CovariateResolver — runtime resolution of declared covariates")
class CovariateResolverTest {

    // 2026-04-29 is a Wednesday.
    private static final Instant WEDNESDAY_NOON_UTC =
            Instant.parse("2026-04-29T12:00:00Z");

    private final Clock clock = Clock.fixed(WEDNESDAY_NOON_UTC, ZoneOffset.UTC);

    @Test
    @DisplayName("empty declarations resolve to the empty profile")
    void emptyDeclarations() {
        CovariateResolver resolver = CovariateResolver.builder()
                .clock(clock)
                .zoneId(ZoneOffset.UTC)
                .build();

        CovariateProfile profile = resolver.resolve(List.of(), Map.of());

        assertThat(profile.isEmpty()).isTrue();
        assertThat(profile).isSameAs(CovariateProfile.empty());
    }

    @Test
    @DisplayName("resolution preserves declaration order in the profile")
    void preservesOrder() {
        CovariateResolver resolver = CovariateResolver.builder()
                .clock(clock)
                .zoneId(ZoneOffset.UTC)
                .build();

        CovariateProfile profile = resolver.resolve(
                List.of(
                        Covariate.timezone(),
                        Covariate.dayOfWeek(List.of(Set.of(SATURDAY, SUNDAY)))),
                Map.of());

        assertThat(profile.values().keySet())
                .containsExactly("timezone", "day_of_week");
    }

    @Nested
    @DisplayName("DayOfWeek resolution")
    class DayOfWeekTests {

        @Test
        @DisplayName("returns the matching partition's label for a declared partition")
        void inDeclaredPartition() {
            // Wednesday is in the {MON-FRI} weekday partition.
            CovariateResolver resolver = resolverAtUtc();
            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.dayOfWeek(List.of(
                            Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
                            Set.of(SATURDAY, SUNDAY)))),
                    Map.of());

            assertThat(profile.get("day_of_week")).contains("WEEKDAY");
        }

        @Test
        @DisplayName("returns the remainder label when no declared partition matches")
        void inImplicitRemainder() {
            // Wednesday is outside both declared partitions → remainder.
            CovariateResolver resolver = resolverAtUtc();
            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.dayOfWeek(List.of(
                            Set.of(SATURDAY, SUNDAY),
                            Set.of(MONDAY)))),
                    Map.of());

            // Complement of {SAT, SUN, MON} is {TUE, WED, THU, FRI}
            // — labelled in enum order joined by underscores.
            assertThat(profile.get("day_of_week"))
                    .contains("TUESDAY_WEDNESDAY_THURSDAY_FRIDAY");
        }

        @Test
        @DisplayName("recognises WEEKEND as a special label")
        void weekendLabel() {
            // Override the clock to a Saturday.
            Clock saturday = Clock.fixed(
                    Instant.parse("2026-04-25T12:00:00Z"), ZoneOffset.UTC);
            CovariateResolver resolver = CovariateResolver.builder()
                    .clock(saturday).zoneId(ZoneOffset.UTC).build();

            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.dayOfWeek(List.of(Set.of(SATURDAY, SUNDAY)))),
                    Map.of());

            assertThat(profile.get("day_of_week")).contains("WEEKEND");
        }
    }

    @Nested
    @DisplayName("TimeOfDay resolution")
    class TimeOfDayTests {

        @Test
        @DisplayName("returns the matching period's label")
        void insidePeriod() {
            // 12:00 is within 12:00/3h.
            CovariateResolver resolver = resolverAtUtc();
            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.timeOfDay(List.of("08:00/2h", "12:00/3h"))),
                    Map.of());

            assertThat(profile.get("time_of_day")).contains("12:00/3h");
        }

        @Test
        @DisplayName("returns OUTSIDE when no period contains now")
        void outside() {
            // 12:00 falls between two periods that don't cover noon.
            CovariateResolver resolver = resolverAtUtc();
            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.timeOfDay(List.of("08:00/2h", "16:00/3h"))),
                    Map.of());

            assertThat(profile.get("time_of_day")).contains("OUTSIDE");
        }
    }

    @Nested
    @DisplayName("Region resolution")
    class RegionTests {

        @Test
        @DisplayName("returns the partition label when region matches")
        void inPartition() {
            CovariateResolver resolver = CovariateResolver.builder()
                    .clock(clock).zoneId(ZoneOffset.UTC)
                    .environment(envWith(Map.of("punit.region", "FR")))
                    .build();

            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.region(List.of(
                            Set.of("FR", "DE"), Set.of("GB", "IE")))),
                    Map.of());

            assertThat(profile.get("region")).contains("DE_FR");
        }

        @Test
        @DisplayName("returns OTHER when configured region matches no partition")
        void unmatchedRegion() {
            CovariateResolver resolver = CovariateResolver.builder()
                    .clock(clock).zoneId(ZoneOffset.UTC)
                    .environment(envWith(Map.of("punit.region", "JP")))
                    .build();

            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.region(List.of(Set.of("FR", "DE")))),
                    Map.of());

            assertThat(profile.get("region")).contains("OTHER");
        }

        @Test
        @DisplayName("returns UNDEFINED when no region is configured")
        void undefinedRegion() {
            CovariateResolver resolver = CovariateResolver.builder()
                    .clock(clock).zoneId(ZoneOffset.UTC)
                    .environment(envWith(Map.of()))  // empty environment
                    .build();

            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.region(List.of(Set.of("FR")))),
                    Map.of());

            assertThat(profile.get("region")).contains("UNDEFINED");
        }

        @Test
        @DisplayName("falls back from system property to env var")
        void envFallback() {
            // No 'punit.region' but PUNIT_REGION is set.
            CovariateResolver resolver = CovariateResolver.builder()
                    .clock(clock).zoneId(ZoneOffset.UTC)
                    .environment(envWith(Map.of("PUNIT_REGION", "fr")))
                    .build();

            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.region(List.of(Set.of("FR")))),
                    Map.of());

            // Lower-cased input is uppercased before partition match.
            assertThat(profile.get("region")).contains("FR");
        }
    }

    @Nested
    @DisplayName("Timezone resolution")
    class TimezoneTests {

        @Test
        @DisplayName("returns the configured zone's IANA id")
        void zoneId() {
            CovariateResolver resolver = CovariateResolver.builder()
                    .clock(clock)
                    .zoneId(ZoneId.of("Europe/Zurich"))
                    .build();

            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.timezone()), Map.of());

            assertThat(profile.get("timezone")).contains("Europe/Zurich");
        }
    }

    @Nested
    @DisplayName("Custom resolution")
    class CustomTests {

        @Test
        @DisplayName("invokes the registered resolver and stores its return")
        void resolverInvoked() {
            CovariateResolver resolver = resolverAtUtc();
            Map<String, Supplier<String>> resolvers =
                    Map.of("model_version", () -> "claude-opus-4-7");

            CovariateProfile profile = resolver.resolve(
                    List.of(Covariate.custom("model_version", CovariateCategory.CONFIGURATION)),
                    resolvers);

            assertThat(profile.get("model_version")).contains("claude-opus-4-7");
        }

        @Test
        @DisplayName("fails fast when a declared custom covariate has no resolver")
        void missingResolver() {
            CovariateResolver resolver = resolverAtUtc();

            assertThatThrownBy(() -> resolver.resolve(
                    List.of(Covariate.custom("model_version", CovariateCategory.CONFIGURATION)),
                    Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no resolver registered")
                    .hasMessageContaining("model_version");
        }

        @Test
        @DisplayName("fails fast when a resolver returns null")
        void resolverReturnsNull() {
            CovariateResolver resolver = resolverAtUtc();
            Map<String, Supplier<String>> resolvers = new HashMap<>();
            resolvers.put("flag", () -> null);

            assertThatThrownBy(() -> resolver.resolve(
                    List.of(Covariate.custom("flag", CovariateCategory.CONFIGURATION)),
                    resolvers))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("returned null");
        }
    }

    @Test
    @DisplayName("resolves a mixed declaration in declaration order")
    void mixedDeclaration() {
        CovariateResolver resolver = CovariateResolver.builder()
                .clock(clock)
                .zoneId(ZoneId.of("Europe/Zurich"))
                .environment(envWith(Map.of("punit.region", "FR")))
                .build();

        CovariateProfile profile = resolver.resolve(
                List.of(
                        Covariate.dayOfWeek(List.of(Set.of(SATURDAY, SUNDAY))),
                        Covariate.region(List.of(Set.of("FR", "DE"))),
                        Covariate.timezone(),
                        Covariate.custom("model_version", CovariateCategory.CONFIGURATION)),
                Map.of("model_version", () -> "v1"));

        // Wednesday with weekend declared → remainder = {MON-FRI},
        // which collapses to the special WEEKDAY label.
        assertThat(profile.values()).containsExactly(
                Map.entry("day_of_week", "WEEKDAY"),
                Map.entry("region", "DE_FR"),
                Map.entry("timezone", "Europe/Zurich"),
                Map.entry("model_version", "v1"));
    }

    // ── helpers ─────────────────────────────────────────────────────

    private CovariateResolver resolverAtUtc() {
        return CovariateResolver.builder()
                .clock(clock)
                .zoneId(ZoneOffset.UTC)
                .build();
    }

    private static java.util.function.Function<String, Optional<String>> envWith(
            Map<String, String> entries) {
        return key -> Optional.ofNullable(entries.get(key));
    }
}
