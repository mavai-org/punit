package org.mavai.punit.internal.engine.baseline;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.NormativeJudgement;

/**
 * In-memory shape of a baseline file. Carries the identity keys the
 * {@link BaselineResolver} matches against, the
 * sampling-population integrity fields the empirical criteria
 * cross-check, and a per-criterion-name map of
 * {@link BaselineStatistics} flavours.
 *
 * <p>Mirrors the schema documented in
 * {@code docs/DES-BASELINE-YAML-SCHEMA.md}.
 *
 * @param serviceContractId           the service contract's stable identity, as
 *                            returned by {@code ServiceContract.id()}
 * @param methodName          the spec-method name that produced
 *                            this baseline
 * @param factorsFingerprint  short hash of the factor values the
 *                            measure ran under
 * @param inputsIdentity      full SHA-256 inputs identity, as
 *                            returned by {@code Sampling.inputsIdentity()}
 * @param sampleCount         the total number of samples the
 *                            measure executed
 * @param generatedAt         when the measure produced this baseline
 * @param statisticsByCriterionName  one
 *        {@link BaselineStatistics} entry per criterion, keyed by
 *        {@code Criterion.name()}
 * @param covariateProfile    the resolved covariate profile under
 *        which this baseline was measured. Empty when the service contract
 *        declared no covariates (covariate-insensitive baselines).
 *        Part of the baseline's identity — a baseline measured under
 *        one profile must not silently match a test running under a
 *        different profile.
 * @param expiresInDays       the baseline validity window in days, or
 *        {@code 0} for no expiration. When positive, the measure
 *        declared {@code .expiresInDays(n)}; the writer emits it and the
 *        derived {@code expiresAt} into the baseline so a paired test can
 *        evaluate staleness.
 * @param normativeJudgements the measure run's judgement of each
 *        normative criterion against its stipulated threshold, in
 *        contract declaration order. Empty when the contract declares
 *        no normative criteria. Serialised as an additive per-criterion
 *        marker in the baseline YAML; documentary only — the
 *        {@link BaselineResolver} and threshold derivation ignore it,
 *        and {@link BaselineReader} does not reconstitute it on load.
 */
public record BaselineRecord(
        String serviceContractId,
        String methodName,
        String factorsFingerprint,
        String inputsIdentity,
        int sampleCount,
        Instant generatedAt,
        Map<String, BaselineStatistics> statisticsByCriterionName,
        CovariateProfile covariateProfile,
        LatencyIndicator latencyIndicator,
        int expiresInDays,
        List<NormativeJudgement> normativeJudgements,
        java.util.Optional<org.mavai.punit.api.spec.PostconditionStandings>
                postconditionStandings) {

    /** Backward-compatible constructor without the standings; defaults them absent. */
    public BaselineRecord(
            String serviceContractId,
            String methodName,
            String factorsFingerprint,
            String inputsIdentity,
            int sampleCount,
            Instant generatedAt,
            Map<String, BaselineStatistics> statisticsByCriterionName,
            CovariateProfile covariateProfile,
            LatencyIndicator latencyIndicator,
            int expiresInDays,
            List<NormativeJudgement> normativeJudgements) {
        this(serviceContractId, methodName, factorsFingerprint, inputsIdentity, sampleCount,
                generatedAt, statisticsByCriterionName, covariateProfile, latencyIndicator,
                expiresInDays, normativeJudgements, java.util.Optional.empty());
    }

    public BaselineRecord {
        Objects.requireNonNull(serviceContractId, "serviceContractId");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(factorsFingerprint, "factorsFingerprint");
        Objects.requireNonNull(inputsIdentity, "inputsIdentity");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(statisticsByCriterionName, "statisticsByCriterionName");
        Objects.requireNonNull(covariateProfile, "covariateProfile");
        Objects.requireNonNull(latencyIndicator, "latencyIndicator");
        Objects.requireNonNull(normativeJudgements, "normativeJudgements");
        if (serviceContractId.isBlank()) {
            throw new IllegalArgumentException("serviceContractId must not be blank");
        }
        if (methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (factorsFingerprint.isBlank()) {
            throw new IllegalArgumentException("factorsFingerprint must not be blank");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                    "sampleCount must be non-negative, got " + sampleCount);
        }
        if (expiresInDays < 0) {
            throw new IllegalArgumentException(
                    "expiresInDays must be non-negative (0 = no expiration), got " + expiresInDays);
        }
        if (statisticsByCriterionName.isEmpty()) {
            throw new IllegalArgumentException(
                    "statisticsByCriterionName must not be empty — a baseline with no "
                            + "criterion entries is not consumable by any empirical criterion");
        }
        statisticsByCriterionName = Map.copyOf(new LinkedHashMap<>(statisticsByCriterionName));
        normativeJudgements = List.copyOf(normativeJudgements);
    }

    /**
     * Convenience constructor matching the pre-normative-judgement
     * canonical signature; defaults {@link #normativeJudgements()} to
     * an empty list (no normative criteria judged).
     */
    public BaselineRecord(
            String serviceContractId,
            String methodName,
            String factorsFingerprint,
            String inputsIdentity,
            int sampleCount,
            Instant generatedAt,
            Map<String, BaselineStatistics> statisticsByCriterionName,
            CovariateProfile covariateProfile,
            LatencyIndicator latencyIndicator,
            int expiresInDays) {
        this(serviceContractId, methodName, factorsFingerprint, inputsIdentity,
                sampleCount, generatedAt, statisticsByCriterionName,
                covariateProfile, latencyIndicator, expiresInDays, List.of());
    }

    /**
     * Convenience constructor matching the pre-expiration canonical
     * signature; defaults {@link #expiresInDays()} to {@code 0}
     * (no expiration).
     */
    public BaselineRecord(
            String serviceContractId,
            String methodName,
            String factorsFingerprint,
            String inputsIdentity,
            int sampleCount,
            Instant generatedAt,
            Map<String, BaselineStatistics> statisticsByCriterionName,
            CovariateProfile covariateProfile,
            LatencyIndicator latencyIndicator) {
        this(serviceContractId, methodName, factorsFingerprint, inputsIdentity,
                sampleCount, generatedAt, statisticsByCriterionName,
                covariateProfile, latencyIndicator, 0, List.of());
    }

    /**
     * Backward-compatible 8-arg constructor that defaults
     * {@link #latencyIndicator()} to {@link LatencyIndicator#empty()}
     * and {@link #expiresInDays()} to {@code 0}.
     */
    public BaselineRecord(
            String serviceContractId,
            String methodName,
            String factorsFingerprint,
            String inputsIdentity,
            int sampleCount,
            Instant generatedAt,
            Map<String, BaselineStatistics> statisticsByCriterionName,
            CovariateProfile covariateProfile) {
        this(serviceContractId, methodName, factorsFingerprint, inputsIdentity,
                sampleCount, generatedAt, statisticsByCriterionName,
                covariateProfile, LatencyIndicator.empty(), 0);
    }

    /**
     * Convenience constructor for callers that don't carry a covariate
     * profile (covariate-insensitive baselines, tests that don't
     * exercise covariate resolution). Equivalent to the canonical
     * constructor with {@link CovariateProfile#empty()},
     * {@link LatencyIndicator#empty()}, and no expiration.
     */
    public BaselineRecord(
            String serviceContractId,
            String methodName,
            String factorsFingerprint,
            String inputsIdentity,
            int sampleCount,
            Instant generatedAt,
            Map<String, BaselineStatistics> statisticsByCriterionName) {
        this(serviceContractId, methodName, factorsFingerprint, inputsIdentity,
                sampleCount, generatedAt, statisticsByCriterionName,
                CovariateProfile.empty(), LatencyIndicator.empty(), 0);
    }

    /**
     * @return the canonical filename for this baseline.
     *
     * <p>Empty covariate profile (the default — covariate-insensitive
     * baselines):
     * <pre>{@code
     * {serviceContractId}.{methodName}-{factorsFingerprint}.yaml
     * }</pre>
     *
     * <p>Non-empty covariate profile — one 4-char hash per covariate,
     * in declaration order, separated by hyphens after the factors
     * fingerprint:
     * <pre>{@code
     * {serviceContractId}.{methodName}-{factorsFingerprint}-{cov1}-{cov2}...-{covN}.yaml
     * }</pre>
     *
     * <p>The empty-profile form is byte-identical to the
     * covariate-insensitive filename, so older baselines on disk
     * continue to match.
     */
    public String filename() {
        StringBuilder name = new StringBuilder()
                .append(serviceContractId).append('.').append(methodName)
                .append('-').append(factorsFingerprint);
        for (String hash : CovariateHashing.hashesFor(covariateProfile)) {
            name.append('-').append(hash);
        }
        return name.append(".yaml").toString();
    }
}
