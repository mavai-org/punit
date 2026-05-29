package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sample-loop policy enums")
class PolicyEnumsTest {

    @Test
    @DisplayName("BudgetExhaustionPolicy carries exactly PASS_INCOMPLETE and FAIL")
    void budgetPolicyShape() {
        assertThat(BudgetExhaustionPolicy.values())
                .containsExactly(BudgetExhaustionPolicy.PASS_INCOMPLETE, BudgetExhaustionPolicy.FAIL);
    }

    @Test
    @DisplayName("ExceptionPolicy carries exactly ABORT_TEST and FAIL_SAMPLE")
    void exceptionPolicyShape() {
        assertThat(ExceptionPolicy.values())
                .containsExactly(ExceptionPolicy.ABORT_TEST, ExceptionPolicy.FAIL_SAMPLE);
    }

    @Test
    @DisplayName("CriterionRole carries exactly REQUIRED and REPORT_ONLY")
    void criterionRoleShape() {
        assertThat(CriterionRole.values())
                .containsExactly(CriterionRole.REQUIRED, CriterionRole.REPORT_ONLY);
    }
}
