package org.mavai.punit.api.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.covariate.CovariateAlignment;

/**
 * The result of running a {@link ProbabilisticTest} — the composed
 * verdict plus the ordered list of per-criterion results that produced
 * it.
 *
 * <p>The list carries both {@link CriterionRole#REQUIRED REQUIRED} and
 * {@link CriterionRole#REPORT_ONLY REPORT_ONLY} entries in the order the
 * spec builder registered them, so reporters can surface every criterion
 * regardless of its contribution to composition. The combined
 * {@link #verdict()} is {@link Verdict#compose(List) Verdict.compose}'d
 * from the REQUIRED subset.
 *
 * <p>{@link #intent()} carries the test's declared intent through to
 * reporting so that sentinel-grade smoke verdicts can be flagged with
 * an explicit caveat ("not sized for verification") and so that the
 * future feasibility-check machinery can decide whether a verification
 * claim is statistically supportable for the chosen sample size.
 *
 * <p>{@link #covariates()} carries the alignment between the run's
 * resolved covariate profile and the matched baseline's covariate
 * profile (when an empirical criterion matched one). The structured
 * value flows through to verdict-text renderers, HTML report
 * emitters, and JSON sinks.
 *
 * <p>{@link #contractRef()} carries an author-supplied human-readable
 * pointer to the external document the test's threshold derives from
 * — an SLA paragraph, an SLO contract, an internal policy reference.
 * The value is opaque to the framework and surfaces in the verdict
 * text and the verdict XML as audit-grade traceability. Truly
 * optional fields like this one travel as {@link Optional} on the
 * result so consumers don't have to discriminate {@code null} from
 * value at every read site.
 *
 * @param verdict          the composed PASS / FAIL / INCONCLUSIVE
 * @param factors          the factor bundle observed
 * @param criterionResults the full ordered list of evaluated criteria
 * @param intent           the test's declared intent (VERIFICATION / SMOKE)
 * @param warnings         free-form warnings (e.g. "statistics wiring pending")
 * @param covariates       observed-vs-baseline covariate alignment
 * @param contractRef      author-supplied audit pointer to the
 *                         contract or policy document the threshold
 *                         derives from; {@link Optional#empty()} when
 *                         not declared
 */
public record ProbabilisticTestResult(
        Verdict verdict,
        FactorBundle factors,
        List<EvaluatedCriterion> criterionResults,
        TestIntent intent,
        List<String> warnings,
        CovariateAlignment covariates,
        Optional<String> contractRef,
        Map<String, FailureCount> failuresByPostcondition,
        EngineRunSummary engineSummary,
        PerCriterionEvaluation perCriterionEvaluation) implements EngineResult {

    public ProbabilisticTestResult {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(criterionResults, "criterionResults");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(warnings, "warnings");
        Objects.requireNonNull(covariates, "covariates");
        Objects.requireNonNull(contractRef, "contractRef");
        Objects.requireNonNull(failuresByPostcondition, "failuresByPostcondition");
        Objects.requireNonNull(engineSummary, "engineSummary");
        Objects.requireNonNull(perCriterionEvaluation, "perCriterionEvaluation");
        criterionResults = List.copyOf(criterionResults);
        warnings = List.copyOf(warnings);
        failuresByPostcondition = Map.copyOf(failuresByPostcondition);
    }

    /**
     * @return a copy of this result with the given covariate
     *         alignment substituted. Used by the JUnit pipeline to
     *         post-stamp the observed profile (resolved at the
     *         test-extension boundary) onto the result coming back
     *         from {@code Spec.conclude}.
     */
    public ProbabilisticTestResult withCovariates(CovariateAlignment covariates) {
        return new ProbabilisticTestResult(
                verdict, factors, criterionResults, intent, warnings,
                covariates, contractRef, failuresByPostcondition,
                engineSummary, perCriterionEvaluation);
    }

    /**
     * @param contractRef the author-supplied audit pointer; {@code null}
     *                    or blank values map to {@link Optional#empty()}
     *                    so a test author calling {@code .contractRef(null)}
     *                    or never calling it at all yield identical
     *                    results.
     * @return a copy of this result with the given contract reference
     *         substituted. Used by the JUnit pipeline to post-stamp
     *         the reference declared on the typed test builder onto
     *         the result coming back from {@code Spec.conclude}.
     */
    public ProbabilisticTestResult withContractRef(String contractRef) {
        Optional<String> wrapped = (contractRef == null || contractRef.isBlank())
                ? Optional.empty()
                : Optional.of(contractRef);
        return new ProbabilisticTestResult(
                verdict, factors, criterionResults, intent, warnings,
                covariates, wrapped, failuresByPostcondition,
                engineSummary, perCriterionEvaluation);
    }
}
