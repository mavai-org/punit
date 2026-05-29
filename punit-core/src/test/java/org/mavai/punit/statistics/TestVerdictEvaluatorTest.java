package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TestVerdictEvaluator}.
 * 
 * <h2>Purpose</h2>
 * <p>Verifies that the evaluator produces qualified verdicts with:
 * <ul>
 *   <li>Correct pass/fail determination</li>
 *   <li>Accurate false positive probability reporting</li>
 *   <li>Human-readable interpretations</li>
 * </ul>
 */
@DisplayName("TestVerdictEvaluator")
class TestVerdictEvaluatorTest {
    
    private TestVerdictEvaluator evaluator;
    private ThresholdDeriver deriver;
    
    @BeforeEach
    void setUp() {
        evaluator = new TestVerdictEvaluator();
        deriver = new ThresholdDeriver();
    }
    
    /**
     * Creates a sample threshold for testing.
     * Baseline: 951/1000 (95.1%), Test: 100 samples, 95% confidence
     */
    private DerivedThreshold sampleThreshold() {
        return deriver.deriveSampleSizeFirst(1000, 951, 100, 0.95);
    }
    
    @Nested
    @DisplayName("Pass/Fail Determination")
    class PassFailDetermination {
        
        @Test
        @DisplayName("passes when observed rate equals threshold")
        void passesWhenObservedRateEqualsThreshold() {
            DerivedThreshold threshold = sampleThreshold();
            int thresholdCount = (int) Math.ceil(threshold.value() * 100);
            
            VerdictWithConfidence verdict = evaluator.evaluate(thresholdCount, 100, threshold);
            
            assertThat(verdict.passed()).isTrue();
        }
        
        @Test
        @DisplayName("passes when observed rate exceeds threshold")
        void passesWhenObservedRateExceedsThreshold() {
            DerivedThreshold threshold = sampleThreshold();
            
            // 95/100 = 95% should pass threshold ≈ 93.6%
            VerdictWithConfidence verdict = evaluator.evaluate(95, 100, threshold);
            
            assertThat(verdict.passed()).isTrue();
            assertThat(verdict.observedRate()).isCloseTo(0.95, within(0.001));
        }
        
        @Test
        @DisplayName("fails when observed rate below threshold")
        void failsWhenObservedRateBelowThreshold() {
            DerivedThreshold threshold = sampleThreshold();
            
            // 90/100 = 90% should fail threshold ≈ 93.6%
            VerdictWithConfidence verdict = evaluator.evaluate(90, 100, threshold);
            
            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.observedRate()).isCloseTo(0.90, within(0.001));
        }
        
        @Test
        @DisplayName("handles edge case of 0% observed rate")
        void handlesZeroObservedRate() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(0, 100, threshold);
            
            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.observedRate()).isEqualTo(0.0);
            assertThat(verdict.shortfall()).isGreaterThan(0.9);
        }
        
        @Test
        @DisplayName("handles edge case of 100% observed rate")
        void handlesPerfectObservedRate() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(100, 100, threshold);
            
            assertThat(verdict.passed()).isTrue();
            assertThat(verdict.observedRate()).isEqualTo(1.0);
        }
    }
    
    @Nested
    @DisplayName("False Positive Probability")
    class FalsePositiveProbability {
        
        @Test
        @DisplayName("reports 0% false positive probability for passing tests")
        void zeroFalsePositiveForPassingTests() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(95, 100, threshold);
            
            assertThat(verdict.passed()).isTrue();
            assertThat(verdict.falsePositiveProbability()).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("reports α as false positive probability for failing tests")
        void alphaAsFalsePositiveForFailingTests() {
            // 95% confidence → α = 5% false positive probability
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(90, 100, threshold);
            
            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.falsePositiveProbability()).isCloseTo(0.05, within(0.001));
        }
        
        @Test
        @DisplayName("confidence is complement of false positive probability")
        void confidenceIsComplementOfFalsePositive() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(90, 100, threshold);
            
            assertThat(verdict.confidence())
                .as("Confidence should be 1 - falsePositiveProbability")
                .isCloseTo(0.95, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Shortfall Calculation")
    class ShortfallCalculation {
        
        @Test
        @DisplayName("shortfall is 0 for passing tests")
        void shortfallIsZeroForPassingTests() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(95, 100, threshold);
            
            assertThat(verdict.passed()).isTrue();
            assertThat(verdict.shortfall()).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("shortfall is threshold - observed for failing tests")
        void shortfallIsThresholdMinusObserved() {
            DerivedThreshold threshold = sampleThreshold();
            double thresholdValue = threshold.value();
            
            // Observe 90/100 = 90%
            VerdictWithConfidence verdict = evaluator.evaluate(90, 100, threshold);
            
            double expectedShortfall = thresholdValue - 0.90;
            assertThat(verdict.shortfall()).isCloseTo(expectedShortfall, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Interpretation Generation")
    class InterpretationGeneration {
        
        @Test
        @DisplayName("passing test interpretation indicates no degradation")
        void passingInterpretationIndicatesNoDegradation() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(95, 100, threshold);
            
            assertThat(verdict.interpretation())
                .contains("No evidence of degradation");
        }
        
        @Test
        @DisplayName("failing test interpretation includes confidence level")
        void failingInterpretationIncludesConfidence() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(90, 100, threshold);
            
            assertThat(verdict.interpretation())
                .contains("DEGRADATION")
                .contains("95%")
                .contains("confidence");
        }
        
        @Test
        @DisplayName("failing test interpretation includes false positive warning")
        void failingInterpretationIncludesFalsePositiveWarning() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence verdict = evaluator.evaluate(90, 100, threshold);
            
            assertThat(verdict.interpretation())
                .contains("5.0% probability")
                .contains("sampling variance");
        }
    }
    
    @Nested
    @DisplayName("Multiple Run Summary")
    class MultipleRunSummary {
        
        @Test
        @DisplayName("all passing runs indicate no degradation")
        void allPassingRunsIndicateNoDegradation() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence v1 = evaluator.evaluate(95, 100, threshold);
            VerdictWithConfidence v2 = evaluator.evaluate(96, 100, threshold);
            VerdictWithConfidence v3 = evaluator.evaluate(94, 100, threshold);
            
            String summary = evaluator.summarizeMultipleRuns(v1, v2, v3);
            
            assertThat(summary)
                .contains("All 3 runs passed")
                .contains("No evidence of degradation");
        }
        
        @Test
        @DisplayName("single failure may be false positive")
        void singleFailureMayBeFalsePositive() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence v1 = evaluator.evaluate(95, 100, threshold);
            VerdictWithConfidence v2 = evaluator.evaluate(90, 100, threshold); // failure
            VerdictWithConfidence v3 = evaluator.evaluate(94, 100, threshold);
            
            String summary = evaluator.summarizeMultipleRuns(v1, v2, v3);
            
            assertThat(summary)
                .contains("1 of 3 runs failed")
                .contains("Single failure")
                .contains("false positive");
        }
        
        @Test
        @DisplayName("multiple failures indicate strong evidence of degradation")
        void multipleFailuresIndicateStrongEvidence() {
            DerivedThreshold threshold = sampleThreshold();
            
            VerdictWithConfidence v1 = evaluator.evaluate(88, 100, threshold); // failure
            VerdictWithConfidence v2 = evaluator.evaluate(90, 100, threshold); // failure
            VerdictWithConfidence v3 = evaluator.evaluate(89, 100, threshold); // failure
            
            String summary = evaluator.summarizeMultipleRuns(v1, v2, v3);
            
            assertThat(summary)
                .contains("3 of 3 runs failed")
                .contains("Strong evidence of actual degradation");
        }
        
        @Test
        @DisplayName("handles empty verdict array")
        void handlesEmptyVerdictArray() {
            String summary = evaluator.summarizeMultipleRuns();
            
            assertThat(summary).contains("No test runs to summarize");
        }
    }
    
    @Nested
    @DisplayName("Input Validation")
    class InputValidation {
        
        @Test
        @DisplayName("rejects non-positive test samples")
        void rejectsNonPositiveTestSamples() {
            assertThatThrownBy(() -> evaluator.evaluate(0, 0, sampleThreshold()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test samples must be positive");
        }
        
        @Test
        @DisplayName("rejects negative test successes")
        void rejectsNegativeTestSuccesses() {
            assertThatThrownBy(() -> evaluator.evaluate(-1, 100, sampleThreshold()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test successes must be non-negative");
        }
        
        @Test
        @DisplayName("rejects test successes exceeding samples")
        void rejectsTestSuccessesExceedingSamples() {
            assertThatThrownBy(() -> evaluator.evaluate(150, 100, sampleThreshold()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed samples");
        }
        
        @Test
        @DisplayName("rejects null threshold")
        void rejectsNullThreshold() {
            assertThatThrownBy(() -> evaluator.evaluate(90, 100, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Threshold must not be null");
        }
    }
    
    @Nested
    @DisplayName("Worked Example from STATISTICAL-COMPANION")
    class WorkedExample {
        
        @Test
        @DisplayName("test failure with qualified interpretation")
        void testFailureWithQualifiedInterpretation() {
            // STATISTICAL-COMPANION §3.4: threshold is Wilson lower at n_test
            // applied to the baseline rate. Baseline 951/1000 → threshold
            // ≈ 0.902 at n_test=100, 95% confidence. Test result 80/100.
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);

            VerdictWithConfidence verdict = evaluator.evaluate(80, 100, threshold);

            // Decision: FAIL (0.80 < ~0.902)
            assertThat(verdict.passed()).isFalse();

            // False positive probability: 5%
            assertThat(verdict.falsePositiveProbability()).isCloseTo(0.05, within(0.001));

            // Interpretation should be qualified
            assertThat(verdict.interpretation())
                .as("Interpretation should include qualification")
                .containsPattern("0\\.8000 < 0\\.90\\d+")     // observed < threshold
                .contains("95%")                              // confidence level
                .contains("5.0%")                             // false positive probability
                .contains("sampling variance");               // statistical caveat
        }
    }
}

