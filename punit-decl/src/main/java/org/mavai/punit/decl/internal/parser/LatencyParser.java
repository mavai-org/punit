package org.mavai.punit.decl.internal.parser;

import static org.mavai.punit.decl.internal.parser.Yaml.fail;
import static org.mavai.punit.decl.internal.parser.Yaml.isUnitIntervalRate;
import static org.mavai.punit.decl.internal.parser.Yaml.requireMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mavai.punit.decl.internal.model.LatencyDeclaration;
import org.mavai.punit.decl.internal.model.LatencyDeclaration.PercentileCeiling;

/**
 * The {@code latency:} block: explicit millisecond ceilings, or
 * empirical percentiles — two mutually exclusive shapes, each with an
 * optional {@code confidence:} and the provenance keys.
 */
final class LatencyParser {

    private static final List<String> PERCENTILES = List.of("p50", "p90", "p95", "p99");
    private static final Set<String> LATENCY_KEYS = Set.of(
            "p50", "p90", "p95", "p99", "empirical", "confidence", "threshold-origin", "contract-ref");

    private LatencyParser() {}

    static LatencyDeclaration parse(Map<String, Object> data) {
        if (!data.containsKey("latency")) {
            return null;
        }
        Map<String, Object> block = requireMapping(data.get("latency"), "`latency:`");
        for (String key : block.keySet()) {
            if (!LATENCY_KEYS.contains(key)) {
                throw fail("`latency:` has unknown key `" + key + ":` (supported: "
                        + String.join(", ", LATENCY_KEYS.stream().sorted().toList()) + ")");
            }
        }

        List<PercentileCeiling> ceilings = new ArrayList<>();
        for (String percentile : PERCENTILES) {
            if (!block.containsKey(percentile)) {
                continue;
            }
            Object value = block.get(percentile);
            if (!(value instanceof Integer millis) || millis <= 0) {
                throw fail("`latency: " + percentile + ":` must be a positive whole number of "
                        + "milliseconds, got " + value);
            }
            ceilings.add(new PercentileCeiling(percentile, millis));
        }

        List<String> empirical = new ArrayList<>();
        if (block.containsKey("empirical")) {
            if (!ceilings.isEmpty()) {
                throw fail("`latency:` declares explicit ceilings and `empirical:` together — "
                        + "contradictory: a bound is either stipulated or derived from the "
                        + "measured baseline, not both");
            }
            Object entries = block.get("empirical");
            if (!(entries instanceof List<?> list) || list.isEmpty()) {
                throw fail("`latency: empirical:` must be a non-empty list of percentiles");
            }
            for (Object entry : list) {
                if (!(entry instanceof String percentile) || !PERCENTILES.contains(percentile)) {
                    throw fail("`latency: empirical:` names unknown percentile '" + entry
                            + "' (supported: " + String.join(", ", PERCENTILES) + ")");
                }
            }
            if (list.stream().distinct().count() != list.size()) {
                throw fail("`latency: empirical:` names each percentile at most once");
            }
            for (String percentile : PERCENTILES) {
                if (list.contains(percentile)) {
                    empirical.add(percentile);
                }
            }
        }

        if (ceilings.isEmpty() && empirical.isEmpty()) {
            throw fail("`latency:` declares no bounds — give explicit ceilings (`p95: 500`) or "
                    + "`empirical: [p95]`");
        }
        for (int i = 1; i < ceilings.size(); i++) {
            if (ceilings.get(i).millis() < ceilings.get(i - 1).millis()) {
                throw fail("`latency:` ceilings must be non-decreasing across percentiles — a "
                        + "tighter bound on a higher percentile contradicts itself");
            }
        }

        Double confidence = null;
        Object confidenceValue = block.get("confidence");
        if (confidenceValue != null) {
            if (!isUnitIntervalRate(confidenceValue)) {
                throw fail("`latency: confidence:` must be a number in (0, 1)");
            }
            confidence = ((Number) confidenceValue).doubleValue();
        }

        String origin = null;
        Object originValue = block.get("threshold-origin");
        if (originValue != null) {
            if (!(originValue instanceof String name)
                    || !CriteriaParser.THRESHOLD_ORIGINS.contains(name)) {
                throw fail("`latency: threshold-origin:` names the bar's provenance category — "
                        + "one of " + String.join(", ", CriteriaParser.THRESHOLD_ORIGINS)
                        + " — got '" + originValue + "'");
            }
            origin = name;
        }

        String contractRef = null;
        Object contractRefValue = block.get("contract-ref");
        if (contractRefValue != null) {
            if (!(contractRefValue instanceof String reference) || reference.isEmpty()) {
                throw fail("`latency: contract-ref:` must be a non-empty string");
            }
            contractRef = reference;
        }

        return new LatencyDeclaration(ceilings, empirical, confidence, origin, contractRef);
    }
}
