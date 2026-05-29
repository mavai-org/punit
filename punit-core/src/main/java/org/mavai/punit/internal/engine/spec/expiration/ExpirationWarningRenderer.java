package org.mavai.punit.internal.engine.spec.expiration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.mavai.punit.verdict.ExpirationPolicy;
import org.mavai.punit.verdict.ExpirationStatus;
import org.mavai.punit.internal.reporting.DurationFormat;
import org.mavai.punit.internal.reporting.PUnitReporter;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;

/**
 * Renders expiration warnings for display in test output.
 *
 * <p>Produces formatted warning messages based on expiration status:
 * <ul>
 *   <li><strong>Expired</strong>: Prominent warning with remediation guidance</li>
 *   <li><strong>Expiring imminently</strong>: Warning format with urgency</li>
 *   <li><strong>Expiring soon</strong>: Informational format</li>
 * </ul>
 *
 * <p>All output is designed to be human-readable and immediately actionable.
 */
public final class ExpirationWarningRenderer {

    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private ExpirationWarningRenderer() {
        // Utility class
    }

    /**
     * Renders an expiration warning for the given status.
     *
     * @param spec the execution specification containing the expiration policy
     * @param status the expiration status
     * @return the rendered warning content, or empty content if no warning is needed
     */
    public static PUnitReporter.WarningContent renderWarning(ExecutionSpecification spec, ExpirationStatus status) {
        if (status == null || !status.requiresWarning()) {
            return new PUnitReporter.WarningContent("", "");
        }

        return switch (status) {
            case ExpirationStatus.Expired expired -> 
                renderExpired(spec.getExpirationPolicy(), expired);
            case ExpirationStatus.ExpiringImminently imminent -> 
                renderExpiringImminently(spec.getExpirationPolicy(), imminent);
            case ExpirationStatus.ExpiringSoon soon -> 
                renderExpiringSoon(spec.getExpirationPolicy(), soon);
            default -> new PUnitReporter.WarningContent("", "");
        };
    }

    /**
     * Renders an expiration warning for the given status.
     *
     * @param spec the execution specification containing the expiration policy
     * @param status the expiration status
     * @return the rendered warning, or empty string if no warning is needed
     * @deprecated Use {@link #renderWarning(ExecutionSpecification, ExpirationStatus)} instead
     */
    @Deprecated
    public static String render(ExecutionSpecification spec, ExpirationStatus status) {
        PUnitReporter.WarningContent content = renderWarning(spec, status);
        if (content.isEmpty()) {
            return "";
        }
        return content.title() + "\n" + content.body();
    }

    /**
     * Renders a prominent warning for an expired baseline.
     */
    private static PUnitReporter.WarningContent renderExpired(ExpirationPolicy policy, ExpirationStatus.Expired status) {
        StringBuilder sb = new StringBuilder();
        sb.append("The baseline used for statistical inference has expired.\n\n");
        sb.append(PUnitReporter.labelValueLn("Baseline created:", formatInstant(policy.baselineEndTime())));
        sb.append(PUnitReporter.labelValueLn("Validity period:", policy.expiresInDays() + " days"));
        sb.append(PUnitReporter.labelValueLn("Expiration date:", formatInstant(policy.expirationTime().orElse(null))));
        sb.append(PUnitReporter.labelValueLn("Expired:", formatDuration(status.expiredAgo()) + " ago"));
        sb.append("\nStatistical inference is based on potentially stale empirical data.\n");
        sb.append("Consider running a fresh MEASURE experiment to update the baseline.");
        return new PUnitReporter.WarningContent("BASELINE EXPIRED", sb.toString());
    }

    /**
     * Renders a warning for imminent expiration.
     */
    private static PUnitReporter.WarningContent renderExpiringImminently(
            ExpirationPolicy policy, ExpirationStatus.ExpiringImminently status) {
        String body = String.format(
                "Baseline expires in %s (on %s).\n\nConsider scheduling a MEASURE experiment to refresh the baseline.",
                formatDuration(status.remaining()),
                formatInstant(policy.expirationTime().orElse(null)));
        return new PUnitReporter.WarningContent("BASELINE EXPIRING IMMINENTLY", body);
    }

    /**
     * Renders an informational message for approaching expiration.
     */
    private static PUnitReporter.WarningContent renderExpiringSoon(
            ExpirationPolicy policy, ExpirationStatus.ExpiringSoon status) {
        String body = String.format("Baseline expires in %s (on %s).",
            formatDuration(status.remaining()),
            formatInstant(policy.expirationTime().orElse(null)));
        return new PUnitReporter.WarningContent("BASELINE EXPIRES SOON", body);
    }

    /**
     * Formats a duration into a human-readable string.
     *
     * @param duration the duration to format
     * @return a human-readable duration string
     * @deprecated Use {@link DurationFormat#calendar(Duration)} instead
     */
    @Deprecated
    public static String formatDuration(Duration duration) {
        return DurationFormat.calendar(duration);
    }

    /**
     * Formats an instant into a human-readable date/time string.
     *
     * @param instant the instant to format
     * @return a formatted date/time string
     */
    public static String formatInstant(Instant instant) {
        if (instant == null) {
            return "unknown";
        }
        return instant.atZone(ZoneId.systemDefault()).format(DATE_FORMATTER);
    }
}
