package org.mavai.punit.internal.engine.covariate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.covariate.CustomCovariate;
import org.mavai.punit.api.covariate.DayOfWeekCovariate;
import org.mavai.punit.api.covariate.RegionCovariate;
import org.mavai.punit.api.covariate.TimeOfDayCovariate;
import org.mavai.punit.api.covariate.TimezoneCovariate;

/**
 * Resolves a service contract's declared covariates into a
 * {@link CovariateProfile} at the start of an experiment or test run.
 *
 * <p>Built-in resolution dispatches on the {@link Covariate} sealed
 * hierarchy:
 *
 * <ul>
 *   <li>{@link DayOfWeekCovariate} — match the current day against
 *       the declared partitions; days outside every partition resolve
 *       to a complement-derived label (e.g. {@code WEEKEND} declared
 *       → remainder labelled from {@code MONDAY..FRIDAY}).</li>
 *   <li>{@link TimeOfDayCovariate} — parse the declared
 *       {@code HH:mm/Nh} periods, find the one containing the current
 *       local time; "no match" resolves to {@value #TIME_OUTSIDE_LABEL}.</li>
 *   <li>{@link RegionCovariate} — read the configured region from
 *       (in order) the {@value #REGION_PROPERTY} system property and
 *       the {@value #REGION_ENV_VAR} environment variable; match
 *       against the declared partitions; missing region resolves to
 *       {@value #REGION_UNDEFINED_LABEL}, unmatched region to
 *       {@value #REGION_OTHER_LABEL}.</li>
 *   <li>{@link TimezoneCovariate} — return the configured zone's IANA
 *       id (e.g. {@code Europe/Zurich}). No partitioning.</li>
 *   <li>{@link CustomCovariate} — invoke the supplier the service contract
 *       provides; fail fast if no resolver is registered.</li>
 * </ul>
 *
 * <p>Resolvers are deterministic for a given {@code Clock},
 * {@code ZoneId}, and environment lookup. Inject custom values
 * through {@link Builder} for tests; production code uses
 * {@link #defaults()}.
 *
 * <p>This resolver is invoked once per experiment run or test
 * execution — the resulting profile travels with every sample
 * in that run. Re-resolving per sample would let the profile drift
 * mid-run (the clock advances, the system property could mutate),
 * defeating the purpose of recording a snapshot.
 */
// javai-ref: JVI-GYHECWN — do not remove (resolves in javai-orchestrator)
public final class CovariateResolver {

    static final String REGION_PROPERTY = "punit.region";
    static final String REGION_ENV_VAR = "PUNIT_REGION";
    static final String REGION_OTHER_LABEL = "OTHER";
    static final String REGION_UNDEFINED_LABEL = "UNDEFINED";
    static final String TIME_OUTSIDE_LABEL = "OUTSIDE";

    private static final Set<DayOfWeek> WEEKEND_DAYS =
            EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    private static final Set<DayOfWeek> WEEKDAY_DAYS =
            EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final Clock clock;
    private final ZoneId zoneId;
    private final Function<String, Optional<String>> environment;

    private CovariateResolver(
            Clock clock,
            ZoneId zoneId,
            Function<String, Optional<String>> environment) {
        this.clock = clock;
        this.zoneId = zoneId;
        this.environment = environment;
    }

    /**
     * @return a resolver wired to the system clock, system default
     *         zone, and a system-property-then-env-var environment
     *         lookup
     */
    public static CovariateResolver defaults() {
        return new CovariateResolver(
                Clock.systemDefaultZone(),
                ZoneId.systemDefault(),
                CovariateResolver::lookupSystemPropertyThenEnv);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolve all declarations into a profile.
     *
     * @param declarations the covariates declared by the service contract;
     *                     never null
     * @param customCovariateResolvers resolvers for custom-covariate
     *                                 names; never null. Missing
     *                                 resolvers for declared custom
     *                                 covariates cause an
     *                                 {@link IllegalStateException}.
     */
    public CovariateProfile resolve(
            List<Covariate> declarations,
            Map<String, Supplier<String>> customCovariateResolvers) {
        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(customCovariateResolvers, "customCovariateResolvers");
        if (declarations.isEmpty()) {
            return CovariateProfile.empty();
        }
        Map<String, String> values = new LinkedHashMap<>(declarations.size());
        for (Covariate c : declarations) {
            String value = switch (c) {
                case DayOfWeekCovariate d -> resolveDayOfWeek(d);
                case TimeOfDayCovariate t -> resolveTimeOfDay(t);
                case RegionCovariate r    -> resolveRegion(r);
                case TimezoneCovariate t  -> resolveTimezone();
                case CustomCovariate cu   -> resolveCustom(cu, customCovariateResolvers);
            };
            values.put(c.name(), value);
        }
        return CovariateProfile.of(values);
    }

    // ── Built-in resolution ─────────────────────────────────────────

    private String resolveDayOfWeek(DayOfWeekCovariate covariate) {
        DayOfWeek today = LocalDate.now(clock.withZone(zoneId)).getDayOfWeek();
        for (Set<DayOfWeek> partition : covariate.partitions()) {
            if (partition.contains(today)) {
                return dayPartitionLabel(partition);
            }
        }
        return dayRemainderLabel(covariate.partitions());
    }

    private String resolveTimeOfDay(TimeOfDayCovariate covariate) {
        List<PeriodMatcher.Parsed> parsed = PeriodMatcher.parse(covariate.periods());
        LocalTime now = LocalTime.now(clock.withZone(zoneId));
        return PeriodMatcher.match(now, parsed).orElse(TIME_OUTSIDE_LABEL);
    }

    private String resolveRegion(RegionCovariate covariate) {
        Optional<String> raw = environment.apply(REGION_PROPERTY)
                .or(() -> environment.apply(REGION_ENV_VAR));
        if (raw.isEmpty() || raw.get().isBlank()) {
            return REGION_UNDEFINED_LABEL;
        }
        String code = raw.get().toUpperCase(Locale.ROOT);
        for (Set<String> partition : covariate.partitions()) {
            if (partition.contains(code)) {
                return regionPartitionLabel(partition);
            }
        }
        return REGION_OTHER_LABEL;
    }

    private String resolveTimezone() {
        return zoneId.getId();
    }

    private String resolveCustom(
            CustomCovariate covariate,
            Map<String, Supplier<String>> customResolvers) {
        Supplier<String> resolver = customResolvers.get(covariate.name());
        if (resolver == null) {
            throw new IllegalStateException(
                    "no resolver registered for custom covariate '" + covariate.name()
                            + "' — declare it in ServiceContract.customCovariateResolvers()");
        }
        String value = resolver.get();
        if (value == null) {
            throw new IllegalStateException(
                    "resolver for custom covariate '" + covariate.name() + "' returned null");
        }
        return value;
    }

    // ── Label derivation ────────────────────────────────────────────

    static String dayPartitionLabel(Set<DayOfWeek> days) {
        if (days.equals(WEEKEND_DAYS)) {
            return "WEEKEND";
        }
        if (days.equals(WEEKDAY_DAYS)) {
            return "WEEKDAY";
        }
        return days.stream()
                .sorted()
                .map(DayOfWeek::name)
                .collect(Collectors.joining("_"));
    }

    static String dayRemainderLabel(List<Set<DayOfWeek>> partitions) {
        EnumSet<DayOfWeek> declared = EnumSet.noneOf(DayOfWeek.class);
        for (Set<DayOfWeek> p : partitions) {
            declared.addAll(p);
        }
        EnumSet<DayOfWeek> complement = EnumSet.complementOf(declared);
        if (complement.isEmpty()) {
            // All seven days were declared somewhere; remainder is
            // unreachable. The label is empty by convention.
            return "";
        }
        return dayPartitionLabel(complement);
    }

    static String regionPartitionLabel(Set<String> regions) {
        // ISO codes uppercased on the way in; sort ascending for a
        // deterministic label irrespective of declaration order.
        TreeSet<String> sorted = new TreeSet<>(regions);
        return String.join("_", sorted);
    }

    // ── Environment lookup ──────────────────────────────────────────

    private static Optional<String> lookupSystemPropertyThenEnv(String key) {
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return Optional.of(prop);
        }
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return Optional.of(env);
        }
        return Optional.empty();
    }

    // ── Builder ─────────────────────────────────────────────────────

    public static final class Builder {
        private Clock clock = Clock.systemDefaultZone();
        private ZoneId zoneId = ZoneId.systemDefault();
        private Function<String, Optional<String>> environment =
                CovariateResolver::lookupSystemPropertyThenEnv;

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public Builder zoneId(ZoneId zoneId) {
            this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
            return this;
        }

        /** Override the environment lookup. Receives a key, returns
         *  the value or empty. Used by tests to inject region
         *  without mutating real system state. */
        public Builder environment(Function<String, Optional<String>> environment) {
            this.environment = Objects.requireNonNull(environment, "environment");
            return this;
        }

        public CovariateResolver build() {
            return new CovariateResolver(clock, zoneId, environment);
        }
    }
}
