package org.mavai.punit.internal.engine.spec.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates spec file content against the expected schema.
 *
 * <p>This validator ensures that spec files contain all required fields
 * with valid values, preventing regressions in spec generation.
 *
 * <h2>Schema Version: punit-spec-1</h2>
 * <p>Required fields:
 * <ul>
 *   <li>{@code schemaVersion} - must be "punit-spec-1"</li>
 *   <li>{@code serviceContractId} - non-empty string</li>
 *   <li>{@code generatedAt} - ISO-8601 timestamp</li>
 *   <li>{@code execution.samplesPlanned} - positive integer</li>
 *   <li>{@code execution.samplesExecuted} - non-negative integer</li>
 *   <li>{@code execution.terminationReason} - valid termination reason</li>
 *   <li>{@code statistics.successRate.observed} - number in [0, 1]</li>
 *   <li>{@code statistics.successRate.standardError} - non-negative number</li>
 *   <li>{@code statistics.successRate.confidenceInterval95} - array of two numbers</li>
 *   <li>{@code statistics.successes} - non-negative integer</li>
 *   <li>{@code statistics.failures} - non-negative integer</li>
 *   <li>{@code cost.totalTimeMs} - non-negative integer</li>
 *   <li>{@code cost.totalTokens} - non-negative integer</li>
 *   <li>{@code contentFingerprint} - 64-character hex string (SHA-256)</li>
 * </ul>
 */
public final class SpecSchemaValidator {

    private static final String CURRENT_SCHEMA_VERSION = "punit-spec-1";
    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("punit-spec-1", "punit-spec-2");
    
    private static final Set<String> VALID_TERMINATION_REASONS = Set.of(
        "COMPLETED",
        "TOKEN_BUDGET_EXHAUSTED",
        "TIME_BUDGET_EXHAUSTED",
        "EARLY_TERMINATION",
        "ERROR"
    );
    
    private static final Pattern ISO_TIMESTAMP_PATTERN = Pattern.compile(
        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"
    );
    
    private static final Pattern HEX_64_PATTERN = Pattern.compile("[a-f0-9]{64}");

    private SpecSchemaValidator() {
        // Utility class
    }

    /**
     * Validates spec content against the schema.
     *
     * @param content the YAML content to validate
     * @return validation result with any errors found
     */
    public static ValidationResult validate(String content) {
        List<String> errors = new ArrayList<>();
        
        if (content == null || content.isBlank()) {
            errors.add("Spec content is null or empty");
            return new ValidationResult(false, errors);
        }
        
        // Schema version
        String schemaVersion = extractField(content, "schemaVersion");
        if (schemaVersion == null || schemaVersion.isEmpty()) {
            errors.add("Missing required field: schemaVersion");
        } else if (!SUPPORTED_SCHEMA_VERSIONS.contains(schemaVersion)) {
            errors.add("Unsupported schemaVersion: " + schemaVersion + 
                       ". Supported: " + SUPPORTED_SCHEMA_VERSIONS);
        }
        
        // Service contract ID
        String serviceContractId = extractField(content, "useCaseId");
        if (serviceContractId == null || serviceContractId.isEmpty()) {
            errors.add("Missing required field: serviceContractId");
        }
        
        // Generated timestamp
        String generatedAt = extractField(content, "generatedAt");
        if (generatedAt == null || generatedAt.isEmpty()) {
            errors.add("Missing required field: generatedAt");
        } else if (!ISO_TIMESTAMP_PATTERN.matcher(generatedAt).matches()) {
            errors.add("Invalid generatedAt format. Expected ISO-8601 timestamp, got: " + generatedAt);
        }
        
        // Execution section
        validateExecutionSection(content, errors);
        
        // Statistics section (required unless latency-only spec)
        boolean hasStatistics = content.contains("statistics:");
        boolean hasLatency = content.contains("latency:");
        if (!hasStatistics && !hasLatency) {
            errors.add("Spec must contain at least one dimension: statistics or latency");
        }
        if (hasStatistics) {
            validateStatisticsSection(content, errors);
        }
        
        // Cost section
        validateCostSection(content, errors);
        
        // Content fingerprint
        String fingerprint = extractField(content, "contentFingerprint");
        if (fingerprint == null || fingerprint.isEmpty()) {
            errors.add("Missing required field: contentFingerprint");
        } else if (!HEX_64_PATTERN.matcher(fingerprint).matches()) {
            errors.add("Invalid contentFingerprint format. Expected 64-character hex string, got: " + 
                       fingerprint.length() + " characters");
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * Validates spec content and throws if invalid.
     *
     * @param content the YAML content to validate
     * @throws SpecificationIntegrityException if validation fails
     */
    public static void validateOrThrow(String content) {
        ValidationResult result = validate(content);
        if (!result.isValid()) {
            throw new SpecificationIntegrityException(
                "Spec schema validation failed:\n  - " + String.join("\n  - ", result.errors()));
        }
    }
    
    private static void validateExecutionSection(String content, List<String> errors) {
        if (!content.contains("execution:")) {
            errors.add("Missing required section: execution");
            return;
        }
        
        String samplesPlanned = extractNestedField(content, "execution", "samplesPlanned");
        if (samplesPlanned == null) {
            errors.add("Missing required field: execution.samplesPlanned");
        } else if (!isPositiveInteger(samplesPlanned)) {
            errors.add("execution.samplesPlanned must be a positive integer, got: " + samplesPlanned);
        }
        
        String samplesExecuted = extractNestedField(content, "execution", "samplesExecuted");
        if (samplesExecuted == null) {
            errors.add("Missing required field: execution.samplesExecuted");
        } else if (!isNonNegativeInteger(samplesExecuted)) {
            errors.add("execution.samplesExecuted must be a non-negative integer, got: " + samplesExecuted);
        }
        
        String terminationReason = extractNestedField(content, "execution", "terminationReason");
        if (terminationReason == null) {
            errors.add("Missing required field: execution.terminationReason");
        } else if (!VALID_TERMINATION_REASONS.contains(terminationReason)) {
            errors.add("Invalid execution.terminationReason: " + terminationReason + 
                       ". Valid values: " + VALID_TERMINATION_REASONS);
        }
    }
    
    private static void validateStatisticsSection(String content, List<String> errors) {
        if (!content.contains("statistics:")) {
            errors.add("Missing required section: statistics");
            return;
        }
        
        // Success rate subsection
        if (!content.contains("successRate:")) {
            errors.add("Missing required section: statistics.successRate");
        } else {
            String observed = extractDeepNestedField(content, "statistics", "successRate", "observed");
            if (observed == null) {
                errors.add("Missing required field: statistics.successRate.observed");
            } else if (!isValidProportion(observed)) {
                errors.add("statistics.successRate.observed must be a number in [0, 1], got: " + observed);
            }
            
            String standardError = extractDeepNestedField(content, "statistics", "successRate", "standardError");
            if (standardError == null) {
                errors.add("Missing required field: statistics.successRate.standardError");
            } else if (!isNonNegativeNumber(standardError)) {
                errors.add("statistics.successRate.standardError must be non-negative, got: " + standardError);
            }
            
            // Confidence interval check (simplified - just check it exists)
            if (!content.contains("confidenceInterval95:")) {
                errors.add("Missing required field: statistics.successRate.confidenceInterval95");
            }
        }
        
        String successes = extractNestedField(content, "statistics", "successes");
        if (successes == null) {
            errors.add("Missing required field: statistics.successes");
        } else if (!isNonNegativeInteger(successes)) {
            errors.add("statistics.successes must be a non-negative integer, got: " + successes);
        }
        
        String failures = extractNestedField(content, "statistics", "failures");
        if (failures == null) {
            errors.add("Missing required field: statistics.failures");
        } else if (!isNonNegativeInteger(failures)) {
            errors.add("statistics.failures must be a non-negative integer, got: " + failures);
        }
    }
    
    private static void validateCostSection(String content, List<String> errors) {
        if (!content.contains("cost:")) {
            errors.add("Missing required section: cost");
            return;
        }
        
        String totalTimeMs = extractNestedField(content, "cost", "totalTimeMs");
        if (totalTimeMs == null) {
            errors.add("Missing required field: cost.totalTimeMs");
        } else if (!isNonNegativeInteger(totalTimeMs)) {
            errors.add("cost.totalTimeMs must be a non-negative integer, got: " + totalTimeMs);
        }
        
        String totalTokens = extractNestedField(content, "cost", "totalTokens");
        if (totalTokens == null) {
            errors.add("Missing required field: cost.totalTokens");
        } else if (!isNonNegativeInteger(totalTokens)) {
            errors.add("cost.totalTokens must be a non-negative integer, got: " + totalTokens);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FIELD EXTRACTION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static String extractField(String content, String fieldName) {
        String prefix = fieldName + ":";
        int idx = content.indexOf("\n" + prefix);
        if (idx < 0) {
            // Check if it's at the start
            if (content.startsWith(prefix)) {
                idx = -1; // Will become 0 after +1
            } else {
                return null;
            }
        }
        idx++; // Skip the newline
        
        int lineEnd = content.indexOf('\n', idx + prefix.length());
        if (lineEnd < 0) lineEnd = content.length();
        
        String value = content.substring(idx + prefix.length(), lineEnd).trim();
        // Remove quotes if present
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
    
    private static String extractNestedField(String content, String section, String field) {
        int sectionIdx = content.indexOf(section + ":");
        if (sectionIdx < 0) return null;
        
        // Find the next top-level section (line starting without spaces)
        int sectionEnd = findNextTopLevelSection(content, sectionIdx + section.length());
        String sectionContent = content.substring(sectionIdx, sectionEnd);
        
        String prefix = "  " + field + ":";
        int fieldIdx = sectionContent.indexOf(prefix);
        if (fieldIdx < 0) return null;
        
        int lineEnd = sectionContent.indexOf('\n', fieldIdx + prefix.length());
        if (lineEnd < 0) lineEnd = sectionContent.length();
        
        return sectionContent.substring(fieldIdx + prefix.length(), lineEnd).trim();
    }
    
    private static String extractDeepNestedField(String content, String section, String subsection, String field) {
        int sectionIdx = content.indexOf(section + ":");
        if (sectionIdx < 0) return null;
        
        int subsectionIdx = content.indexOf("  " + subsection + ":", sectionIdx);
        if (subsectionIdx < 0) return null;
        
        // Find the field within the subsection
        String prefix = "    " + field + ":";
        int fieldIdx = content.indexOf(prefix, subsectionIdx);
        if (fieldIdx < 0) return null;
        
        int lineEnd = content.indexOf('\n', fieldIdx + prefix.length());
        if (lineEnd < 0) lineEnd = content.length();
        
        return content.substring(fieldIdx + prefix.length(), lineEnd).trim();
    }
    
    private static int findNextTopLevelSection(String content, int startIdx) {
        String[] lines = content.substring(startIdx).split("\n");
        int offset = startIdx;
        for (int i = 1; i < lines.length; i++) { // Skip first line (current section)
            offset += lines[i - 1].length() + 1;
            if (!lines[i].isEmpty() && !lines[i].startsWith(" ") && !lines[i].startsWith("#")) {
                return offset;
            }
        }
        return content.length();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VALUE VALIDATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static boolean isPositiveInteger(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private static boolean isNonNegativeInteger(String value) {
        try {
            return Integer.parseInt(value) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private static boolean isNonNegativeNumber(String value) {
        try {
            return Double.parseDouble(value) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private static boolean isValidProportion(String value) {
        try {
            double d = Double.parseDouble(value);
            return d >= 0 && d <= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT CLASS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Result of schema validation.
     *
     * @param valid true if the spec is valid
     * @param errors list of validation errors (empty if valid)
     */
    public record ValidationResult(boolean valid, List<String> errors) {
        public boolean isValid() {
            return valid;
        }
    }
}

