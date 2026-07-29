package org.mavai.punit.lm;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.runtime.PUnit;

/**
 * The whole point, end to end: two YAML files in this package, a
 * one-line body, the punit-lm dependency on the classpath — and a
 * green declarative test against a language-model service (the stub
 * standing in for the endpoint; the environment tier carries where it
 * runs).
 */
@DisplayName("a declarative language-model test")
class DeclarativeLmTest {

    private StubLlm stub;

    @BeforeEach
    void start() {
        stub = StubLlm.start();
        System.setProperty("mavai.llm.endpoint", stub.endpoint());
    }

    @AfterEach
    void stop() {
        stub.close();
        System.clearProperty("mavai.llm.endpoint");
    }

    @Test
    @DisplayName("two YAML files and a one-line body make a green language-model test")
    void stubBasketServiceAnswers() {
        stub.completeWith("{\"basket\": [{\"name\": \"eggs\", \"quantity\": 12}]}");
        assertThatCode(() -> PUnit.declared()
                .bindings(EmptyBindings.class)
                .samples(10)
                .assertPasses())
                .doesNotThrowAnyException();
    }

    static class EmptyBindings {
    }
}
