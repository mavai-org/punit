package org.mavai.punit.decl.internal.parser;

import static org.mavai.punit.decl.internal.parser.Yaml.fail;
import static org.mavai.punit.decl.internal.parser.Yaml.isUnitIntervalRate;
import static org.mavai.punit.decl.internal.parser.Yaml.requireMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mavai.punit.decl.internal.model.CriterionDeclaration;
import org.mavai.punit.decl.internal.model.FormDeclaration;
import org.mavai.punit.decl.internal.model.PostconditionForm;

/**
 * One {@code criteria:} entry, parsed: the key allowlist (with the
 * misplaced-qualifier refusals), the threshold/tolerate/confidence
 * value and exclusivity checks, the closed provenance vocabulary, the
 * inline and {@code postconditions:} forms, and the derived default
 * name.
 */
final class CriteriaParser {

    /** The bar-provenance categories — a closed set; an unknown origin is refused. */
    static final List<String> THRESHOLD_ORIGINS = List.of("sla", "slo", "policy");

    private static final Set<String> CRITERION_KEYS = criterionKeys();

    private static Set<String> criterionKeys() {
        Set<String> keys = new java.util.HashSet<>(Set.of(
                "name",
                "threshold",
                "threshold-origin",
                "contract-ref",
                "tolerate",
                "confidence",
                "postconditions"));
        // Every form has a single-form spelling directly on the entry.
        for (PostconditionForm form : PostconditionForm.values()) {
            keys.add(form.key());
        }
        return Set.copyOf(keys);
    }

    private CriteriaParser() {}

    static CriterionDeclaration parse(Object entryValue, int index, Map<String, String> views) {
        String where = "criteria entry " + (index + 1);
        Map<String, Object> data = requireMapping(entryValue, where);
        for (String key : data.keySet()) {
            if (key.equals("transform") || key.equals("in") || key.equals("path")) {
                throw fail(where + ": `" + key + ":` does not belong on the criterion — declare "
                        + "views in the `transforms:` block and qualify a check's subject with "
                        + "`in:` and `path:` on the check itself");
            }
            if (!CRITERION_KEYS.contains(key)) {
                throw fail(where + ": unknown key `" + key + ":`");
            }
        }

        Double threshold = null;
        Object thresholdValue = data.get("threshold");
        if (thresholdValue != null) {
            if ("empirical".equals(thresholdValue)) {
                throw fail("`threshold: empirical` is " + ContractParser.SEAM_POINTER);
            }
            if (!isUnitIntervalRate(thresholdValue)) {
                throw fail(where + ": `threshold:` must be a number in (0, 1)");
            }
            threshold = ((Number) thresholdValue).doubleValue();
        }

        Double tolerate = null;
        Object tolerateValue = data.get("tolerate");
        if (tolerateValue != null) {
            if (threshold != null) {
                throw fail(where + ": `tolerate:` declares how far below the measured baseline "
                        + "a true rate may drop, so it belongs on an empirical criterion — a "
                        + "criterion with a declared `threshold:` has no baseline claim to "
                        + "protect; drop one of the two keys");
            }
            if (!isUnitIntervalRate(tolerateValue)) {
                throw fail(where + ": `tolerate:` must be a number in (0, 1)");
            }
            tolerate = ((Number) tolerateValue).doubleValue();
        }

        Double confidence = null;
        Object confidenceValue = data.get("confidence");
        if (confidenceValue != null) {
            if (threshold != null) {
                throw fail(where + ": `confidence:` on a criterion belongs to an empirical "
                        + "criterion — a criterion with a declared `threshold:` uses the "
                        + "contract-level confidence; drop one of the two keys");
            }
            if (!isUnitIntervalRate(confidenceValue)) {
                throw fail(where + ": `confidence:` must be a number in (0, 1)");
            }
            confidence = ((Number) confidenceValue).doubleValue();
        }

        String origin = null;
        Object originValue = data.get("threshold-origin");
        if (originValue != null) {
            if (!(originValue instanceof String name) || !THRESHOLD_ORIGINS.contains(name)) {
                throw fail(where + ": `threshold-origin:` names the bar's provenance category — "
                        + "one of " + String.join(", ", THRESHOLD_ORIGINS) + " — got '"
                        + originValue + "'");
            }
            origin = name;
        }

        String contractRef = null;
        Object contractRefValue = data.get("contract-ref");
        if (contractRefValue != null) {
            if (!(contractRefValue instanceof String reference) || reference.isEmpty()) {
                throw fail(where + ": `contract-ref:` must be a non-empty string");
            }
            contractRef = reference;
        }

        List<FormDeclaration> forms = new ArrayList<>();
        for (PostconditionForm form : PostconditionForm.values()) {
            if (data.containsKey(form.key())) {
                Map<String, Object> single = new LinkedHashMap<>();
                single.put(form.key(), data.get(form.key()));
                forms.add(FormParser.parse(single, where, views));
            }
        }
        if (data.containsKey("postconditions")) {
            Object entries = data.get("postconditions");
            if (!(entries instanceof List<?> list) || list.isEmpty()) {
                throw fail(where + ": `postconditions:` must be a non-empty list");
            }
            for (Object formEntry : list) {
                forms.add(FormParser.parse(requireMapping(formEntry, where), where, views));
            }
        }

        String name;
        Object nameValue = data.get("name");
        if (nameValue == null) {
            String source = forms.isEmpty() ? "criterion" : forms.get(0).form().key();
            name = "criterion-" + (index + 1) + "-" + source;
        } else {
            if (!(nameValue instanceof String declared) || declared.isEmpty()) {
                throw fail(where + ": `name:` must be a non-empty string");
            }
            name = declared;
        }

        return new CriterionDeclaration(name, forms, threshold, origin, contractRef, tolerate, confidence);
    }
}
