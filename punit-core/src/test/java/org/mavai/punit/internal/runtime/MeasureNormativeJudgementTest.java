package org.mavai.punit.internal.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.internal.engine.Engine;
import org.mavai.punit.internal.engine.baseline.BaselineReader;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.api.spec.NormativeJudgement;
import org.mavai.punit.statistics.NormativeJudgementEvaluator.State;
import org.yaml.snakeyaml.Yaml;

/**
 * Normative judgement at experiment time, at the baseline-artefact
 * seam: the measure path judges each normative (meeting) criterion
 * against its stipulated threshold using the run's own evidence, and
 * records an additive, documentary marker per judged criterion in the
 * baseline YAML. Empirical criteria are never judged. Resolvers and
 * threshold derivation ignore the marker; files without it parse
 * exactly as before.
 */
@DisplayName("Normative judgement at experiment time — baseline artefact marker")
class MeasureNormativeJudgementTest {

    /** Postcondition passes for even inputs, fails for odd ones. */
    private static ServiceContract<NoFactors, Integer, String> contractWith(
            Criteria<String> criteria, String id) {
        return new ServiceContract<>() {
            @Override public String id() { return id; }
            @Override public Criteria<String> criteria() { return criteria; }
            @Override public Outcome<String> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok("value-" + input);
            }
        };
    }

    private static Outcome<?> even(String value) {
        int n = Integer.parseInt(value.substring("value-".length()));
        return n % 2 == 0 ? Outcome.ok(null) : Outcome.fail("odd", "odd input " + n);
    }

    private static BaselineRecord measure(
            ServiceContract<NoFactors, Integer, String> contract,
            Map<String, String> yamlSink,
            Integer... inputs) {
        Sampling<NoFactors, Integer, String> sampling = Sampling
                .<NoFactors, Integer, String>builder()
                .serviceContractFactory(f -> contract)
                .inputs(inputs)
                .samples(8)
                .build();
        Experiment measure = Experiment.measuring(sampling, new NoFactors()).build();
        new Engine().run(measure);
        return BaselineEmitter.emit(measure, yamlSink::put);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> criterionRow(String yaml, String criterionId) {
        Map<String, Object> root = new Yaml().load(yaml);
        Map<String, Object> stats = (Map<String, Object>) root.get("statistics");
        Map<String, Object> passRate = (Map<String, Object>) stats.get("bernoulli-pass-rate");
        Map<String, Object> criteria = (Map<String, Object>) passRate.get("criteria");
        return (Map<String, Object>) criteria.get(criterionId);
    }

    @Test
    @DisplayName("records a met judgement with the stipulated threshold and confidence "
            + "when the run's evidence clears the stipulation")
    void recordsMetJudgement() {
        ServiceContract<NoFactors, Integer, String> contract = contractWith(
                meeting().<String>passRate(0.5)
                        .contractRef(ThresholdOrigin.SLA, "Payments SLA v2 §4.1")
                        .satisfies("value is even", MeasureNormativeJudgementTest::even),
                "AllEvensMeasure");
        Map<String, String> sink = new LinkedHashMap<>();
        BaselineRecord record = measure(contract, sink, 2, 4); // all pass

        assertThat(record.normativeJudgements()).hasSize(1);
        NormativeJudgement judgement = record.normativeJudgements().get(0);
        assertThat(judgement.judgement().state()).isEqualTo(State.MET);

        @SuppressWarnings("unchecked")
        Map<String, Object> marker = (Map<String, Object>)
                criterionRow(sink.values().iterator().next(), judgement.criterionId())
                        .get("normativeJudgement");
        assertThat(marker)
                .containsEntry("state", "met")
                .containsEntry("stipulatedThreshold", 0.5)
                .containsEntry("confidence", 0.95);
    }

    @Test
    @DisplayName("records a failed judgement — the marker is a durable record of a "
            + "stipulation in force at measure time, not a run failure")
    void recordsFailedJudgement() {
        ServiceContract<NoFactors, Integer, String> contract = contractWith(
                meeting().<String>passRate(0.5)
                        .satisfies("value is even", MeasureNormativeJudgementTest::even),
                "HalfEvensMeasure");
        Map<String, String> sink = new LinkedHashMap<>();
        BaselineRecord record = measure(contract, sink, 2, 3); // half pass

        assertThat(record.normativeJudgements()).hasSize(1);
        assertThat(record.normativeJudgements().get(0).judgement().state())
                .isEqualTo(State.FAILED);

        @SuppressWarnings("unchecked")
        Map<String, Object> marker = (Map<String, Object>)
                criterionRow(sink.values().iterator().next(),
                        record.normativeJudgements().get(0).criterionId())
                        .get("normativeJudgement");
        assertThat(marker).containsEntry("state", "failed");
    }

    @Test
    @DisplayName("records an unsupportable judgement when the run's sample count cannot "
            + "support the stipulated threshold at the criterion's confidence")
    void recordsUnsupportableJudgement() {
        ServiceContract<NoFactors, Integer, String> contract = contractWith(
                meeting().<String>passRate(0.99)
                        .satisfies("value is even", MeasureNormativeJudgementTest::even),
                "UndersizedMeasure");
        Map<String, String> sink = new LinkedHashMap<>();
        BaselineRecord record = measure(contract, sink, 2, 4); // 8 samples, all pass

        assertThat(record.normativeJudgements()).hasSize(1);
        NormativeJudgement judgement = record.normativeJudgements().get(0);
        assertThat(judgement.judgement().state()).isEqualTo(State.UNSUPPORTABLE);
        assertThat(judgement.judgement().feasibleMinimumSamples()).isGreaterThan(8);

        @SuppressWarnings("unchecked")
        Map<String, Object> marker = (Map<String, Object>)
                criterionRow(sink.values().iterator().next(), judgement.criterionId())
                        .get("normativeJudgement");
        assertThat(marker).containsEntry("state", "unsupportable");
    }

    @Test
    @DisplayName("judges only the normative criteria of a mixed contract — the "
            + "empirical criterion is characterised with no judgement marker")
    void mixedContractJudgesOnlyNormativeCriteria() {
        ServiceContract<NoFactors, Integer, String> contract = contractWith(
                Criteria.of(
                        meeting().<String>passRate(0.5)
                                .name("stipulated-evenness")
                                .satisfies("value is even", MeasureNormativeJudgementTest::even),
                        empirical().<String>passRate()
                                .name("observed-evenness")
                                .satisfies("value is even", MeasureNormativeJudgementTest::even)),
                "MixedPostureMeasure");
        Map<String, String> sink = new LinkedHashMap<>();
        BaselineRecord record = measure(contract, sink, 2, 4);

        assertThat(record.normativeJudgements())
                .extracting(NormativeJudgement::criterionId)
                .containsExactly("stipulated-evenness");

        String yaml = sink.values().iterator().next();
        assertThat(criterionRow(yaml, "stipulated-evenness"))
                .containsKey("normativeJudgement");
        assertThat(criterionRow(yaml, "observed-evenness"))
                .as("empirical criteria are never judged at experiment time — "
                        + "their bar does not exist until a baseline supplies it")
                .doesNotContainKey("normativeJudgement")
                .containsKeys("observedPassRate", "sampleCount");
    }

    @Test
    @DisplayName("emits no judgement vocabulary at all for a purely empirical contract")
    void purelyEmpiricalContractCarriesNoJudgementVocabulary() {
        ServiceContract<NoFactors, Integer, String> contract = contractWith(
                empirical().<String>passRate()
                        .satisfies("value is even", MeasureNormativeJudgementTest::even),
                "EmpiricalOnlyMeasure");
        Map<String, String> sink = new LinkedHashMap<>();
        BaselineRecord record = measure(contract, sink, 2, 4);

        assertThat(record.normativeJudgements()).isEmpty();
        assertThat(sink.values().iterator().next())
                .doesNotContain("normativeJudgement", "stipulatedThreshold");
    }

    @Test
    @DisplayName("the marker is additive: the file round-trips through BaselineReader, "
            + "the marker precedes the content fingerprint, and the parsed statistics "
            + "are identical to a marker-free file's")
    void markerIsAdditiveAndIgnoredByTheReader() {
        Map<String, String> judged = new LinkedHashMap<>();
        measure(contractWith(
                meeting().<String>passRate(0.5)
                        .satisfies("value is even", MeasureNormativeJudgementTest::even),
                "AdditivityMeasure"), judged, 2, 3);
        Map<String, String> unjudged = new LinkedHashMap<>();
        measure(contractWith(
                empirical().<String>passRate()
                        .satisfies("value is even", MeasureNormativeJudgementTest::even),
                "AdditivityMeasure"), unjudged, 2, 3);

        String judgedYaml = judged.values().iterator().next();
        String unjudgedYaml = unjudged.values().iterator().next();

        // The marker falls under the integrity hash like every other field.
        assertThat(judgedYaml.indexOf("normativeJudgement"))
                .isGreaterThan(-1)
                .isLessThan(judgedYaml.indexOf("contentFingerprint"));

        BaselineRecord fromJudged = new BaselineReader().parse(judgedYaml);
        BaselineRecord fromUnjudged = new BaselineReader().parse(unjudgedYaml);

        // The reader does not reconstitute the marker: it is documentary.
        assertThat(fromJudged.normativeJudgements()).isEmpty();

        // Derivation-relevant content is identical with or without the
        // marker — resolvers and threshold derivation see the same statistics.
        PassRateStatistics judgedStats =
                ((PerCriterionPassRateStatistics) fromJudged
                        .statisticsByCriterionName().get("bernoulli-pass-rate"))
                        .byCriterion().get("default");
        PassRateStatistics unjudgedStats =
                ((PerCriterionPassRateStatistics) fromUnjudged
                        .statisticsByCriterionName().get("bernoulli-pass-rate"))
                        .byCriterion().get("default");
        assertThat(judgedStats).isEqualTo(unjudgedStats);
    }
}
