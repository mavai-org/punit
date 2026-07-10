package org.mavai.punit.statistics.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.fail;

/**
 * Classpath access to the mavai-R conformance fixtures fetched by the
 * {@code fetchConformanceData} Gradle task into {@code /conformance/}.
 *
 * <p>Suites added in newer oracle releases may be absent when the fetched
 * release predates them; {@link #load(String)} converts that absence into
 * a clear test failure naming the required release rather than an
 * initialisation crash.
 */
final class ConformanceFixtures {

    static final String CONFORMANCE_DIR = "/conformance/";

    /**
     * The oracle release that introduced {@code manifest.json} and
     * {@code regression_decision.json}. Named in the missing-fixture
     * diagnostic so the corrective action is obvious.
     */
    static final String MINIMUM_RELEASE = "v0.8.4";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConformanceFixtures() { }

    static Optional<JsonNode> tryLoad(String filename) {
        try (InputStream is = ConformanceFixtures.class.getResourceAsStream(CONFORMANCE_DIR + filename)) {
            if (is == null) {
                return Optional.empty();
            }
            return Optional.of(MAPPER.readTree(is));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read conformance suite: " + filename, e);
        }
    }

    /**
     * Loads a fixture file, failing (as a test assertion, not a crash)
     * with a corrective diagnostic when the fetched release does not
     * carry it.
     */
    static JsonNode load(String filename) {
        return tryLoad(filename).orElseGet(() -> fail(missingMessage(filename)));
    }

    static String missingMessage(String filename) {
        return "Conformance fixture " + CONFORMANCE_DIR + filename + " is absent from the fetched "
                + "mavai-R release. This suite requires mavai-R " + MINIMUM_RELEASE + " or later; "
                + "the fetchConformanceData task pulls the latest tagged release. Until that release "
                + "is tagged, run against a local checkout: "
                + "./gradlew test -PconformanceCasesDir=/path/to/mavai-R/inst/cases";
    }

    /** MD5 of the fixture file's bytes as fetched — for manifest drift checks. */
    static String md5(String filename) {
        try (InputStream is = ConformanceFixtures.class.getResourceAsStream(CONFORMANCE_DIR + filename)) {
            if (is == null) {
                return fail(missingMessage(filename));
            }
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return String.format("%032x", new BigInteger(1, digest.digest()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to hash conformance suite: " + filename, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    static double[] toDoubleArray(JsonNode node) {
        if (node.isArray()) {
            double[] values = new double[node.size()];
            for (int i = 0; i < node.size(); i++) {
                values[i] = node.get(i).asDouble();
            }
            return values;
        }
        // The oracle's serialiser unboxes single-element vectors to scalars.
        return new double[] { node.asDouble() };
    }

    static long[] toLongArray(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("expected a JSON array");
        }
        long[] out = new long[node.size()];
        for (int i = 0; i < node.size(); i++) {
            out[i] = node.get(i).asLong();
        }
        return out;
    }
}
