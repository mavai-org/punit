package org.mavai.punit.verdict;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SMOKE caveats on a declared requirement")
class SmokeCaveatsTest {

    @Test
    @DisplayName("an undersized design says a PASS is not evidence of compliance and names the feasibility minimum")
    void undersized() {
        assertThat(SmokeCaveats.forNormative(100, 0.99, 0.95))
                .singleElement()
                .satisfies(c -> assertThat(c)
                        .contains("N = 100, need at least 299")
                        .contains("not evidence of compliance")
                        .contains("no evidence of falling short"));
    }

    @Test
    @DisplayName("a feasible design suggests VERIFICATION")
    void feasible() {
        assertThat(SmokeCaveats.forNormative(300, 0.99, 0.95))
                .singleElement()
                .satisfies(c -> assertThat(c).contains("PASS is possible at this size"));
    }

    @Test
    @DisplayName("a size at which no count can FAIL says the result carries no evidence")
    void failNotPossible() {
        assertThat(SmokeCaveats.forNormative(1, 0.5, 0.95))
                .contains("FAIL not possible at this size; this result carries no evidence "
                        + "about the service.");
    }

    @Test
    @DisplayName("no caveat without a requirement in (0, 1)")
    void outsideRange() {
        assertThat(SmokeCaveats.forNormative(10, 1.0, 0.95)).isEmpty();
        assertThat(SmokeCaveats.forNormative(10, Double.NaN, 0.95)).isEmpty();
    }
}
