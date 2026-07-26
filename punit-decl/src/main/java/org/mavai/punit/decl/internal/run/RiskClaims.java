package org.mavai.punit.decl.internal.run;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.model.CriterionDeclaration;
import org.mavai.punit.decl.internal.model.DeclaredIntent;
import org.mavai.punit.statistics.StatisticalDefaults;

/**
 * The invocation's risk-claim surface, resolved and validated: the
 * {@code .tolerating(...)}/{@code .atPower(...)} builder calls and their
 * per-contract property channels
 * ({@code -Dpunit.tolerate.<contract>[.<criterion>]},
 * {@code -Dpunit.power.<contract>}) merged over the file's declared
 * claims — flag over key, property over builder — under the format's
 * rules: one sizing source per invocation (an explicit budget together
 * with a tolerate/power <em>override</em> is contradictory; an explicit
 * budget against <em>file-declared</em> claims is legitimate explicit
 * sizing), a bare tolerate only against exactly one empirical
 * criterion, and named criteria that must exist and be empirical.
 */
final class RiskClaims {

    private final Map<String, Double> tolerateByCriterion;
    private final Double power;

    private RiskClaims(Map<String, Double> tolerateByCriterion, Double power) {
        this.tolerateByCriterion = tolerateByCriterion;
        this.power = power;
    }

    Map<String, Double> tolerateByCriterion() {
        return tolerateByCriterion;
    }

    Double power() {
        return power;
    }

    static RiskClaims resolve(ContractDeclaration declaration, Integer explicitSamples,
            Double builderPower, Double builderBare, Map<String, Double> builderNamed) {
        String contract = declaration.contract();

        Double power = builderPower;
        String powerProperty = "punit.power." + contract;
        String powerValue = System.getProperty(powerProperty);
        if (powerValue != null) {
            power = parseRate(powerProperty, powerValue);
        }

        Double bare = builderBare;
        String bareProperty = "punit.tolerate." + contract;
        String bareValue = System.getProperty(bareProperty);
        if (bareValue != null) {
            bare = parseRate(bareProperty, bareValue);
        }
        Map<String, Double> named = new LinkedHashMap<>(builderNamed);
        String namedPrefix = bareProperty + ".";
        Properties properties = System.getProperties();
        for (String property : properties.stringPropertyNames()) {
            if (property.startsWith(namedPrefix)) {
                named.put(property.substring(namedPrefix.length()),
                        parseRate(property, properties.getProperty(property)));
            }
        }

        boolean overridesPresent = power != null || bare != null || !named.isEmpty();
        if (explicitSamples != null && overridesPresent) {
            throw new ContractConfigurationException(
                    "one sizing source per invocation: .samples(" + explicitSamples + ") "
                            + "contradicts the tolerate/power overrides — drop one side "
                            + "(an explicit budget against file-declared `tolerate:` claims "
                            + "is fine; the contradiction is with overrides)");
        }
        if (power != null && bare == null && named.isEmpty() && fileClaims(declaration).isEmpty()) {
            throw new ContractConfigurationException(
                    ".atPower(...) targets the risk-driven design — declare `tolerate:` on an "
                            + "empirical criterion, or override one with .tolerating(...)");
        }

        List<CriterionDeclaration> empirical = declaration.criteria().stream()
                .filter(criterion -> criterion.threshold() == null)
                .toList();
        Map<String, Double> merged = new LinkedHashMap<>();
        for (CriterionDeclaration criterion : declaration.criteria()) {
            if (criterion.tolerate() != null) {
                merged.put(criterion.name(), criterion.tolerate());
            }
        }
        if (bare != null) {
            if (empirical.size() != 1) {
                throw new ContractConfigurationException(
                        "a bare .tolerating(rate) targets the sole empirical criterion, but "
                                + "this contract has " + empirical.size() + " — use "
                                + ".tolerating(\"<criterion>\", rate) to name one");
            }
            merged.put(empirical.get(0).name(), bare);
        }
        for (Map.Entry<String, Double> override : named.entrySet()) {
            CriterionDeclaration criterion = declaration.criteria().stream()
                    .filter(c -> c.name().equals(override.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new ContractConfigurationException(
                            ".tolerating(\"" + override.getKey() + "\", ...) names no criterion "
                                    + "of this contract — declared: "
                                    + declaration.criteria().stream()
                                            .map(CriterionDeclaration::name)
                                            .reduce((a, b) -> a + ", " + b).orElse("none")));
            if (criterion.threshold() != null) {
                throw new ContractConfigurationException(
                        ".tolerating(\"" + override.getKey() + "\", ...) targets a criterion "
                                + "with a declared `threshold:` — a stipulated bar has no "
                                + "baseline claim to protect");
            }
            merged.put(override.getKey(), override.getValue());
        }

        if (!merged.isEmpty() && explicitSamples == null
                && System.getProperty("punit.samples." + contract) == null
                && declaration.intent() != DeclaredIntent.SMOKE) {
            throw new ContractConfigurationException(
                    "deriving the run size from `tolerate:` claims consumes a measured "
                            + "baseline; risk-driven derivation arrives with the declarative "
                            + "measure phase — size the run explicitly with .samples(N) or "
                            + "-Dpunit.samples." + contract + "=N meanwhile");
        }

        double confidence = StatisticalDefaults.DEFAULT_CONFIDENCE;
        if (declaration.confidenceDeclared()
                && Math.abs(declaration.confidence() - confidence) > 1e-9
                && declaration.criteria().stream().anyMatch(c -> c.threshold() != null)) {
            throw new ContractConfigurationException(
                    "punit judges declared thresholds at the framework confidence ("
                            + confidence + ") — a contract-level `confidence: "
                            + declaration.confidence() + "` cannot rebind thresholded "
                            + "criteria; per-criterion `confidence:` remains available on "
                            + "empirical criteria");
        }

        return new RiskClaims(merged, power);
    }

    private static List<CriterionDeclaration> fileClaims(ContractDeclaration declaration) {
        return declaration.criteria().stream()
                .filter(criterion -> criterion.tolerate() != null)
                .toList();
    }

    private static double parseRate(String property, String value) {
        try {
            double rate = Double.parseDouble(value.trim());
            if (rate <= 0 || rate >= 1) {
                throw new NumberFormatException();
            }
            return rate;
        } catch (NumberFormatException error) {
            throw new ContractConfigurationException(
                    "-D" + property + "=" + value + " must be a rate in (0, 1)");
        }
    }
}
