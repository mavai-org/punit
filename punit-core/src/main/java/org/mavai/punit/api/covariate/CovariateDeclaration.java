package org.mavai.punit.api.covariate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.internal.util.HashUtils;

/**
 * The set of covariates declared by a service contract.
 *
 * <p>A covariate declaration captures which covariates are relevant for a service contract:
 * <ul>
 *   <li>Standard covariates with built-in definitions (day groups, time periods, region groups, timezone)</li>
 *   <li>Custom covariates with explicit categories</li>
 * </ul>
 *
 * <p><strong>Every covariate has a category.</strong> Standard covariate keys map to
 * hardcoded categories; custom covariates have explicit categories in the map.
 *
 * <p>The declaration is used for:
 * <ul>
 *   <li>Footprint computation (covariate names contribute to the footprint)</li>
 *   <li>Covariate resolution (which covariates to capture during experiments)</li>
 *   <li>Baseline selection (matching declarations must match)</li>
 *   <li>Category-aware matching (CONFIGURATION = hard gate, others = soft match)</li>
 * </ul>
 *
 * @param dayGroups the day-of-week partition groups
 * @param timePeriods the time-of-day partition periods
 * @param regionGroups the region partition groups
 * @param timezoneEnabled whether timezone identity covariate is active
 * @param customCovariates map of custom covariate key to category
 * @param serviceContractAttributes service contract attributes (warmup, maxConcurrent, etc.)
 */
public record CovariateDeclaration(
        List<DayGroupDefinition> dayGroups,
        List<TimePeriodDefinition> timePeriods,
        List<RegionGroupDefinition> regionGroups,
        boolean timezoneEnabled,
        Map<String, CovariateCategory> customCovariates,
        ServiceContractAttributes serviceContractAttributes
) {

    /** Implicit covariate key for warmup. */
    public static final String KEY_WARMUP = "warmup";

    /** Standard covariate key for day-of-week. */
    public static final String KEY_DAY_OF_WEEK = "day_of_week";

    /** Standard covariate key for time-of-day. */
    public static final String KEY_TIME_OF_DAY = "time_of_day";

    /** Standard covariate key for region. */
    public static final String KEY_REGION = "region";

    /** Standard covariate key for timezone. */
    public static final String KEY_TIMEZONE = "timezone";

    /** Implicit covariate key for maxConcurrent. */
    public static final String KEY_MAX_CONCURRENT = "maxConcurrent";

    /** An empty covariate declaration. */
    public static final CovariateDeclaration EMPTY = new CovariateDeclaration(
            List.of(), List.of(), List.of(), false, Map.of(), ServiceContractAttributes.DEFAULT);

    public CovariateDeclaration {
        dayGroups = List.copyOf(dayGroups);
        timePeriods = List.copyOf(timePeriods);
        regionGroups = List.copyOf(regionGroups);
        customCovariates = Collections.unmodifiableMap(new LinkedHashMap<>(customCovariates));
    }

    /**
     * Returns all covariate keys in declaration order (standard first in fixed order, then custom).
     *
     * <p>Standard covariates appear in this fixed order when present:
     * day_of_week, time_of_day, region, timezone.
     *
     * @return list of all covariate keys
     */
    public List<String> allKeys() {
        var keys = new ArrayList<String>();
        if (serviceContractAttributes.hasWarmup()) {
            keys.add(KEY_WARMUP);
        }
        if (serviceContractAttributes.hasMaxConcurrent()) {
            keys.add(KEY_MAX_CONCURRENT);
        }
        if (!dayGroups.isEmpty()) {
            keys.add(KEY_DAY_OF_WEEK);
        }
        if (!timePeriods.isEmpty()) {
            keys.add(KEY_TIME_OF_DAY);
        }
        if (!regionGroups.isEmpty()) {
            keys.add(KEY_REGION);
        }
        if (timezoneEnabled) {
            keys.add(KEY_TIMEZONE);
        }
        keys.addAll(customCovariates.keySet());
        return keys;
    }

    /**
     * Computes a stable hash of the covariate declaration (names only).
     *
     * <p>This hash contributes to the invocation footprint. It ensures that
     * baselines are only matched to tests with identical covariate declarations.
     *
     * @return 8-character hex hash, or empty string if no covariates declared
     */
    public String computeDeclarationHash() {
        if (isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        for (String key : allKeys()) {
            sb.append(key).append("\n");
        }

        return HashUtils.truncateHash(HashUtils.sha256(sb.toString()), 8);
    }

    /**
     * Returns true if no covariates are declared.
     *
     * @return true if all standard covariates are inactive and no custom covariates exist
     */
    public boolean isEmpty() {
        return !serviceContractAttributes.hasWarmup()
                && !serviceContractAttributes.hasMaxConcurrent()
                && dayGroups.isEmpty()
                && timePeriods.isEmpty()
                && regionGroups.isEmpty()
                && !timezoneEnabled
                && customCovariates.isEmpty();
    }

    /**
     * Returns the total number of declared covariates.
     *
     * @return count of active standard covariates plus custom covariates
     */
    public int size() {
        int count = 0;
        if (serviceContractAttributes.hasWarmup()) count++;
        if (serviceContractAttributes.hasMaxConcurrent()) count++;
        if (!dayGroups.isEmpty()) count++;
        if (!timePeriods.isEmpty()) count++;
        if (!regionGroups.isEmpty()) count++;
        if (timezoneEnabled) count++;
        count += customCovariates.size();
        return count;
    }

    /**
     * Returns the category for a covariate key.
     *
     * <p>Standard covariate keys return hardcoded categories:
     * <ul>
     *   <li>{@code day_of_week} &rarr; TEMPORAL</li>
     *   <li>{@code time_of_day} &rarr; TEMPORAL</li>
     *   <li>{@code region} &rarr; OPERATIONAL</li>
     *   <li>{@code timezone} &rarr; OPERATIONAL</li>
     * </ul>
     * Custom covariates return their declared category.
     *
     * @param key the covariate key
     * @return the category
     * @throws IllegalArgumentException if the key is not in this declaration
     */
    public CovariateCategory getCategory(String key) {
        return switch (key) {
            case KEY_WARMUP -> {
                if (!serviceContractAttributes.hasWarmup()) throwNotDeclared(key);
                yield CovariateCategory.CONFIGURATION;
            }
            case KEY_MAX_CONCURRENT -> {
                if (!serviceContractAttributes.hasMaxConcurrent()) throwNotDeclared(key);
                yield CovariateCategory.CONFIGURATION;
            }
            case KEY_DAY_OF_WEEK -> {
                if (dayGroups.isEmpty()) throwNotDeclared(key);
                yield CovariateCategory.TEMPORAL;
            }
            case KEY_TIME_OF_DAY -> {
                if (timePeriods.isEmpty()) throwNotDeclared(key);
                yield CovariateCategory.TEMPORAL;
            }
            case KEY_REGION -> {
                if (regionGroups.isEmpty()) throwNotDeclared(key);
                yield CovariateCategory.OPERATIONAL;
            }
            case KEY_TIMEZONE -> {
                if (!timezoneEnabled) throwNotDeclared(key);
                yield CovariateCategory.OPERATIONAL;
            }
            default -> {
                if (!customCovariates.containsKey(key)) {
                    throwNotDeclared(key);
                }
                yield customCovariates.get(key);
            }
        };
    }

    /**
     * Returns true if this declaration contains the given covariate key.
     *
     * @param key the covariate key
     * @return true if declared
     */
    public boolean contains(String key) {
        return switch (key) {
            case KEY_WARMUP -> serviceContractAttributes.hasWarmup();
            case KEY_MAX_CONCURRENT -> serviceContractAttributes.hasMaxConcurrent();
            case KEY_DAY_OF_WEEK -> !dayGroups.isEmpty();
            case KEY_TIME_OF_DAY -> !timePeriods.isEmpty();
            case KEY_REGION -> !regionGroups.isEmpty();
            case KEY_TIMEZONE -> timezoneEnabled;
            default -> customCovariates.containsKey(key);
        };
    }

    /**
     * Returns all custom covariate keys.
     *
     * @return list of custom keys
     */
    public List<String> allCustomKeys() {
        return new ArrayList<>(customCovariates.keySet());
    }

    private void throwNotDeclared(String key) {
        throw new IllegalArgumentException(
                "Covariate '" + key + "' is not declared. Declared covariates: " + allKeys());
    }

}
