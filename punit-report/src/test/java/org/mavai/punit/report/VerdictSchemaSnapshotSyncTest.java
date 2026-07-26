package org.mavai.punit.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The verdict XML schemas embedded in this module are vendored snapshots of
 * the published family schemas (mavai-R's {@code schema/verdict-*.xsd},
 * shipped in the {@code interchange-<tag>.zip} release asset), synced per
 * release. This test asserts each embedded copy is byte-identical to the
 * published one the build fetched — so a schema change authored anywhere
 * but the family's canonical channel, or a missed sync after a release,
 * fails the build instead of shipping a silently divergent schema.
 */
class VerdictSchemaSnapshotSyncTest {

    @ParameterizedTest
    @ValueSource(strings = {"verdict-1.0.xsd", "verdict-1.1.xsd", "verdict-1.2.xsd"})
    @DisplayName("embedded verdict schema is byte-identical to the published family schema")
    void embeddedSchemaMatchesPublished(String schemaFile) throws IOException {
        assertArrayEquals(
                published(schemaFile),
                embedded(schemaFile),
                schemaFile
                        + " differs from the published family schema — the embedded copy is a"
                        + " vendored snapshot; sync it from the mavai-R release rather than"
                        + " editing it in place");
    }

    @Test
    @DisplayName("the published schema set covers every embedded snapshot")
    void publishedSetIsPresent() throws IOException {
        // The fetch task puts the published set on the test classpath; if the
        // release asset ever stops carrying the XSDs, this fails loudly
        // rather than the parameterized test silently comparing nothing.
        for (String schemaFile : new String[] {"verdict-1.0.xsd", "verdict-1.1.xsd", "verdict-1.2.xsd"}) {
            assertNotNull(
                    getClass().getResource("/published-interchange/" + schemaFile),
                    "published copy of " + schemaFile + " missing from the fetched release asset");
        }
    }

    private byte[] embedded(String schemaFile) throws IOException {
        return bytes("/org/mavai/punit/report/" + schemaFile);
    }

    private byte[] published(String schemaFile) throws IOException {
        return bytes("/published-interchange/" + schemaFile);
    }

    private byte[] bytes(String resource) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource: " + resource);
            return in.readAllBytes();
        }
    }
}
