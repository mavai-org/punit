package org.mavai.punit.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.PUnitVerdict;

/**
 * Reads verdict XML files and determines whether all probabilistic tests passed.
 *
 * <p>This is the enforcement point for CI pipelines. It reads the structured
 * verdict XMLs produced during test execution and fails if any verdict is
 * {@link PUnitVerdict#FAIL}.
 *
 * <p>Designed to be invoked by the {@code punitVerify} Gradle task, mirroring
 * the JaCoCo pattern: test execution produces data, a separate verification
 * task interprets that data against policy.
 */
public final class VerdictVerifier {

    private final VerdictXmlReader reader = new VerdictXmlReader();

    /**
     * Result of verification.
     *
     * @param passed     true if all verdicts passed (or no verdicts found)
     * @param total      total number of verdicts examined
     * @param failures   list of failed verdict summaries
     */
    public record VerificationResult(boolean passed, int total, List<FailedVerdict> failures) {

        public static VerificationResult success(int total) {
            return new VerificationResult(true, total, List.of());
        }

        public static VerificationResult failure(int total, List<FailedVerdict> failures) {
            return new VerificationResult(false, total, List.copyOf(failures));
        }
    }

    /**
     * Summary of a failed verdict.
     *
     * @param testName        fully qualified test name
     * @param observedPassRate the observed pass rate
     * @param minPassRate      the required pass rate
     * @param verdict          the PUnit verdict
     */
    public record FailedVerdict(String testName, double observedPassRate,
                                double minPassRate, PUnitVerdict verdict) {}

    /**
     * Verifies all verdict XMLs in the given directory.
     *
     * @param xmlDir directory containing verdict XML files
     * @return the verification result
     */
    public VerificationResult verify(Path xmlDir) {
        List<ProbabilisticTestVerdict> verdicts = readVerdicts(xmlDir);

        if (verdicts.isEmpty()) {
            return VerificationResult.success(0);
        }

        List<FailedVerdict> failures = new ArrayList<>();
        for (ProbabilisticTestVerdict verdict : verdicts) {
            if (verdict.punitVerdict() == PUnitVerdict.FAIL) {
                String testName = verdict.identity().className() + "."
                        + verdict.identity().methodName();
                failures.add(new FailedVerdict(
                        testName,
                        verdict.execution().observedPassRate(),
                        verdict.execution().minPassRate(),
                        verdict.punitVerdict()));
            }
        }

        if (failures.isEmpty()) {
            return VerificationResult.success(verdicts.size());
        }
        return VerificationResult.failure(verdicts.size(), failures);
    }

    /**
     * Formats a verification failure as a human-readable message.
     */
    public String formatFailureMessage(VerificationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("PUnit verification failed: ")
                .append(result.failures().size())
                .append(" of ")
                .append(result.total())
                .append(" probabilistic test(s) failed.\n\n");

        for (FailedVerdict f : result.failures()) {
            sb.append("  FAIL: ").append(f.testName()).append("\n");
            sb.append("        observed: ")
                    .append(String.format("%.4f", f.observedPassRate()))
                    .append(" < required: ")
                    .append(String.format("%.4f", f.minPassRate()))
                    .append("\n\n");
        }

        return sb.toString().trim();
    }

    private List<ProbabilisticTestVerdict> readVerdicts(Path xmlDir) {
        if (!Files.isDirectory(xmlDir)) {
            return List.of();
        }
        List<ProbabilisticTestVerdict> verdicts = new ArrayList<>();
        try (Stream<Path> files = Files.list(xmlDir)) {
            files.filter(p -> p.toString().endsWith(".xml"))
                    .sorted()
                    .forEach(path -> {
                        try (InputStream in = Files.newInputStream(path)) {
                            verdicts.add(reader.read(in));
                        } catch (IOException e) {
                            throw new XmlReadException("Failed to read: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new XmlReadException("Failed to scan XML directory: " + xmlDir, e);
        }
        return verdicts;
    }
}
