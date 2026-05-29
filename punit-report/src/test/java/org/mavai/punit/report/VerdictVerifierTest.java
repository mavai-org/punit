package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.*;
import org.mavai.punit.verdict.PUnitVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("VerdictVerifier")
class VerdictVerifierTest {

    private final VerdictVerifier verifier = new VerdictVerifier();
    private final VerdictXmlWriter writer = new VerdictXmlWriter();

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("passes when all verdicts pass")
        void allPass(@TempDir Path dir) throws Exception {
            writeVerdict(dir, "test1.xml", createVerdict("Test1", "method1", true, PUnitVerdict.PASS, 0.95, 0.90));
            writeVerdict(dir, "test2.xml", createVerdict("Test2", "method2", true, PUnitVerdict.PASS, 1.0, 0.80));

            VerdictVerifier.VerificationResult result = verifier.verify(dir);

            assertThat(result.passed()).isTrue();
            assertThat(result.total()).isEqualTo(2);
            assertThat(result.failures()).isEmpty();
        }

        @Test
        @DisplayName("fails when any verdict fails")
        void anyFail(@TempDir Path dir) throws Exception {
            writeVerdict(dir, "test1.xml", createVerdict("Test1", "method1", true, PUnitVerdict.PASS, 0.95, 0.90));
            writeVerdict(dir, "test2.xml", createVerdict("Test2", "method2", false, PUnitVerdict.FAIL, 0.70, 0.90));

            VerdictVerifier.VerificationResult result = verifier.verify(dir);

            assertThat(result.passed()).isFalse();
            assertThat(result.total()).isEqualTo(2);
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).testName()).contains("Test2").contains("method2");
        }

        @Test
        @DisplayName("passes when directory is empty")
        void emptyDir(@TempDir Path dir) {
            VerdictVerifier.VerificationResult result = verifier.verify(dir);

            assertThat(result.passed()).isTrue();
            assertThat(result.total()).isEqualTo(0);
        }

        @Test
        @DisplayName("passes when directory does not exist")
        void nonExistentDir(@TempDir Path dir) {
            VerdictVerifier.VerificationResult result = verifier.verify(dir.resolve("nonexistent"));

            assertThat(result.passed()).isTrue();
            assertThat(result.total()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("formatFailureMessage")
    class FormatFailureMessage {

        @Test
        @DisplayName("includes all failed tests")
        void includesAllFailures(@TempDir Path dir) throws Exception {
            writeVerdict(dir, "test1.xml", createVerdict("Cls1", "m1", false, PUnitVerdict.FAIL, 0.70, 0.90));
            writeVerdict(dir, "test2.xml", createVerdict("Cls2", "m2", false, PUnitVerdict.FAIL, 0.50, 0.80));

            VerdictVerifier.VerificationResult result = verifier.verify(dir);
            String message = verifier.formatFailureMessage(result);

            assertThat(message).contains("2 of 2 probabilistic test(s) failed");
            assertThat(message).contains("Cls1").contains("m1");
            assertThat(message).contains("Cls2").contains("m2");
            assertThat(message).contains("FAIL");
        }
    }

    private void writeVerdict(Path dir, String filename, ProbabilisticTestVerdict verdict) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(verdict, baos);
        Files.write(dir.resolve(filename), baos.toByteArray());
    }

    private ProbabilisticTestVerdict createVerdict(
            String className, String methodName,
            boolean passed, PUnitVerdict punitVerdict,
            double observedPassRate, double minPassRate) {

        int successes = (int) (observedPassRate * 100);
        int failures = 100 - successes;
        return new ProbabilisticTestVerdict(
                "corr-" + methodName,
                Instant.now(),
                new TestIdentity(className, methodName, Optional.empty()),
                new ExecutionSummary(100, 100, successes, failures,
                        observedPassRate, minPassRate, 1000,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, ServiceContractAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(0.95, 0.02, 0.85,
                        Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                passed,
                punitVerdict,
                punitVerdict == PUnitVerdict.PASS
                        ? String.format("%.4f >= %.4f", observedPassRate, minPassRate)
                        : String.format("%.4f < %.4f", observedPassRate, minPassRate)
        );
    }
}
