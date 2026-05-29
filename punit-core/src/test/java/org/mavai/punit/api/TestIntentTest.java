package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TestIntent}.
 */
class TestIntentTest {

    @Test
    void publicContract_valuesAreStable() {
        assertThat(TestIntent.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("VERIFICATION", "SMOKE");
    }

}
