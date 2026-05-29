package org.mavai.punit.internal.engine.spec.expiration;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.mavai.punit.verdict.ExpirationPolicy;
import org.mavai.punit.verdict.ExpirationStatus;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;

/**
 * Publishes expiration-related properties to JUnit test reports.
 *
 * <p>Published properties:
 * <ul>
 *   <li>{@code punit.baseline.expiresInDays}: Validity period in days</li>
 *   <li>{@code punit.baseline.endTime}: Baseline end timestamp (ISO-8601)</li>
 *   <li>{@code punit.baseline.expirationDate}: Expiration timestamp (ISO-8601)</li>
 *   <li>{@code punit.baseline.expirationStatus}: Current status (e.g., VALID, EXPIRED)</li>
 *   <li>{@code punit.baseline.expiredAgoDays}: Days since expiration (if expired)</li>
 * </ul>
 */
public final class ExpirationReportPublisher {

    private ExpirationReportPublisher() {
        // Utility class
    }

    /**
     * Builds a map of expiration properties for publishing.
     *
     * @param spec the execution specification
     * @param status the evaluated expiration status
     * @return a map of property keys to values
     */
    public static Map<String, String> buildProperties(
            ExecutionSpecification spec, ExpirationStatus status) {
        
        Map<String, String> properties = new LinkedHashMap<>();
        
        // Always publish the status
        properties.put("punit.baseline.expirationStatus", getStatusName(status));
        
        if (spec == null) {
            return properties;
        }
        
        ExpirationPolicy policy = spec.getExpirationPolicy();
        if (policy == null || !policy.hasExpiration()) {
            return properties;
        }
        
        // Publish policy details
        properties.put("punit.baseline.expiresInDays", 
            String.valueOf(policy.expiresInDays()));
        properties.put("punit.baseline.endTime", 
            formatInstant(policy.baselineEndTime()));
        
        policy.expirationTime().ifPresent(exp ->
            properties.put("punit.baseline.expirationDate", formatInstant(exp)));
        
        // Publish additional info for expired baselines
        if (status instanceof ExpirationStatus.Expired expired) {
            long expiredDays = expired.expiredAgo().toDays();
            properties.put("punit.baseline.expiredAgoDays", String.valueOf(expiredDays));
        }
        
        return properties;
    }

    /**
     * Returns the status name for reporting.
     *
     * @param status the expiration status
     * @return the status name
     */
    public static String getStatusName(ExpirationStatus status) {
        return switch (status) {
            case ExpirationStatus.NoExpiration n -> "NO_EXPIRATION";
            case ExpirationStatus.Valid v -> "VALID";
            case ExpirationStatus.ExpiringSoon s -> "EXPIRING_SOON";
            case ExpirationStatus.ExpiringImminently i -> "EXPIRING_IMMINENTLY";
            case ExpirationStatus.Expired e -> "EXPIRED";
        };
    }

    /**
     * Formats an instant to ISO-8601 string for reporting.
     */
    private static String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : "unknown";
    }
}

