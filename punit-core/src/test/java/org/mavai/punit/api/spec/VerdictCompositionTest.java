package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Verdict.compose")
class VerdictCompositionTest {

    private static EvaluatedCriterion required(Verdict verdict) {
        return new EvaluatedCriterion(
                new CriterionResult("some-criterion", verdict, "msg", Map.of()),
                CriterionRole.REQUIRED);
    }

    private static EvaluatedCriterion reportOnly(Verdict verdict) {
        return new EvaluatedCriterion(
                new CriterionResult("some-criterion", verdict, "msg", Map.of()),
                CriterionRole.REPORT_ONLY);
    }

    @Test
    @DisplayName("empty list composes to PASS")
    void emptyListPasses() {
        assertThat(Verdict.compose(List.of())).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("list of all-REPORT_ONLY composes to PASS")
    void allReportOnlyPasses() {
        assertThat(Verdict.compose(List.of(reportOnly(Verdict.FAIL), reportOnly(Verdict.INCONCLUSIVE))))
                .isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("single required PASS → PASS")
    void singlePass() {
        assertThat(Verdict.compose(List.of(required(Verdict.PASS)))).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("single required FAIL → FAIL")
    void singleFail() {
        assertThat(Verdict.compose(List.of(required(Verdict.FAIL)))).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("any required INCONCLUSIVE wins over required FAIL or PASS")
    void inconclusiveWins() {
        assertThat(Verdict.compose(List.of(
                required(Verdict.PASS),
                required(Verdict.FAIL),
                required(Verdict.INCONCLUSIVE))))
                .isEqualTo(Verdict.INCONCLUSIVE);
    }

    @Test
    @DisplayName("required FAIL wins over required PASS when no INCONCLUSIVE present")
    void failBeatsPass() {
        assertThat(Verdict.compose(List.of(required(Verdict.PASS), required(Verdict.FAIL))))
                .isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("all required PASS → PASS")
    void allPass() {
        assertThat(Verdict.compose(List.of(required(Verdict.PASS), required(Verdict.PASS))))
                .isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("REPORT_ONLY entries are excluded from composition")
    void reportOnlyFiltered() {
        assertThat(Verdict.compose(List.of(required(Verdict.PASS), reportOnly(Verdict.FAIL))))
                .isEqualTo(Verdict.PASS);
        assertThat(Verdict.compose(List.of(required(Verdict.PASS), reportOnly(Verdict.INCONCLUSIVE))))
                .isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("order-independent: permuting the inputs yields the same verdict")
    void orderIndependent() {
        List<EvaluatedCriterion> items = new java.util.ArrayList<>(List.of(
                required(Verdict.PASS),
                required(Verdict.FAIL),
                required(Verdict.INCONCLUSIVE),
                reportOnly(Verdict.FAIL)));
        Verdict first = Verdict.compose(items);
        Collections.reverse(items);
        Verdict reversed = Verdict.compose(items);
        assertThat(reversed).isEqualTo(first).isEqualTo(Verdict.INCONCLUSIVE);
    }

    @Test
    @DisplayName("rejects null list")
    void rejectsNullList() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Verdict.compose(null));
    }

    @Test
    @DisplayName("rejects null entry")
    void rejectsNullEntry() {
        List<EvaluatedCriterion> items = new java.util.ArrayList<>();
        items.add(required(Verdict.PASS));
        items.add(null);
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Verdict.compose(items));
    }
}
