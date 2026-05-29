package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Verdict.aggregate worst-case rule")
class VerdictAggregateTest {

    @Test
    @DisplayName("empty list aggregates to PASS")
    void emptyListPasses() {
        assertThat(Verdict.aggregate(List.of())).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("all PASS aggregates to PASS")
    void allPass() {
        assertThat(Verdict.aggregate(List.of(Verdict.PASS, Verdict.PASS)))
                .isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("any FAIL dominates — FAIL among PASSes aggregates to FAIL")
    void failDominatesPasses() {
        assertThat(Verdict.aggregate(List.of(Verdict.PASS, Verdict.FAIL, Verdict.PASS)))
                .isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("FAIL beats INCONCLUSIVE — disagrees with compose which is INCONCLUSIVE-first")
    void failDominatesInconclusive() {
        assertThat(Verdict.aggregate(List.of(Verdict.INCONCLUSIVE, Verdict.FAIL)))
                .isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("INCONCLUSIVE among PASSes aggregates to INCONCLUSIVE")
    void inconclusiveAmongPasses() {
        assertThat(Verdict.aggregate(List.of(Verdict.PASS, Verdict.INCONCLUSIVE, Verdict.PASS)))
                .isEqualTo(Verdict.INCONCLUSIVE);
    }

    @Test
    @DisplayName("order-independent — same multiset same answer")
    void orderIndependent() {
        assertThat(Verdict.aggregate(List.of(Verdict.PASS, Verdict.FAIL, Verdict.INCONCLUSIVE)))
                .isEqualTo(Verdict.aggregate(
                        List.of(Verdict.INCONCLUSIVE, Verdict.FAIL, Verdict.PASS)));
    }
}
