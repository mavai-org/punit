package org.mavai.punit.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.lm.api.LanguageModel;
import org.mavai.punit.lm.api.LanguageModels;
import org.mavai.punit.lm.api.LmReply;

@DisplayName("The programmatic surface — LanguageModels.configure")
class LanguageModelsApiTest {

    @Test
    @DisplayName("the config map is the services-file block — identical refusals, by construction")
    void validationParityWithTheDeclarativeRoute() {
        // The same misfit configurations through both doors; the factory
        // must speak the declarative route's exact refusal. Parity is by
        // construction (one code path), pinned here against regression.
        Map<String, Object>[] misfits = new Map[] {
                Map.of("model", "m"),                                     // no system-prompt
                Map.of("system-prompt", "job", "flavour", "mint"),        // unknown key
                Map.of("system-prompt", "job", "provider", "acme"),       // unknown provider
                Map.of("system-prompt", "job", "top-p", 1.5),             // top-p range
                Map.of("system-prompt", "job", "thinking", "deep"),       // thinking vocabulary
        };
        for (Map<String, Object> misfit : misfits) {
            Throwable declarative = catchThrowable(() ->
                    new LanguageModelServiceType().configure("language-model", misfit));
            Throwable programmatic = catchThrowable(() -> LanguageModels.configure(misfit));
            assertThat(programmatic)
                    .as("programmatic refusal for " + misfit)
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessage(declarative.getMessage());
        }
    }

    @Test
    @DisplayName("a configured model invokes and carries the reported usage")
    void invokeCarriesUsage() throws Exception {
        try (StubLlm stub = StubLlm.start()) {
            System.setProperty("mavai.llm.endpoint", stub.endpoint());
            stub.respond(200, """
                    {"choices": [{"message": {"content": "forty-two"}}],
                     "usage": {"prompt_tokens": 12, "completion_tokens": 3}}
                    """);
            LanguageModel model = LanguageModels.configure(Map.of(
                    "system-prompt", "You answer briefly.",
                    "model", "conformance-model"));
            Outcome<LmReply> outcome;
            try {
                outcome = model.invoke("what is six times seven?");
            } finally {
                System.clearProperty("mavai.llm.endpoint");
            }
            assertThat(outcome).isInstanceOf(Outcome.Ok.class);
            LmReply reply = ((Outcome.Ok<LmReply>) outcome).value();
            assertThat(reply.text()).isEqualTo("forty-two");
            assertThat(reply.usage()).hasValueSatisfying(usage -> {
                assertThat(usage.inputTokens()).isEqualTo(12);
                assertThat(usage.outputTokens()).isEqualTo(3);
                assertThat(usage.totalTokens()).isEqualTo(15);
            });
        }
    }

    @Test
    @DisplayName("absent usage is tolerated — the reply simply carries none")
    void absentUsageTolerated() throws Exception {
        try (StubLlm stub = StubLlm.start()) {
            System.setProperty("mavai.llm.endpoint", stub.endpoint());
            stub.respond(200, """
                    {"choices": [{"message": {"content": "forty-two"}}]}
                    """);
            LanguageModel model = LanguageModels.configure(Map.of(
                    "system-prompt", "You answer briefly.",
                    "model", "conformance-model"));
            Outcome<LmReply> outcome;
            try {
                outcome = model.invoke("what is six times seven?");
            } finally {
                System.clearProperty("mavai.llm.endpoint");
            }
            LmReply reply = ((Outcome.Ok<LmReply>) outcome).value();
            assertThat(reply.usage()).isEmpty();
        }
    }

    @Test
    @DisplayName("the declarative seam reports tokens through the sink")
    void declarativeSeamReportsTokens() throws Exception {
        try (StubLlm stub = StubLlm.start()) {
            System.setProperty("mavai.llm.endpoint", stub.endpoint());
            stub.respond(200, """
                    {"choices": [{"message": {"content": "forty-two"}}],
                     "usage": {"prompt_tokens": 12, "completion_tokens": 3}}
                    """);
            var configured = new LanguageModelServiceType().configure("language-model", Map.of(
                    "system-prompt", "You answer briefly.",
                    "model", "conformance-model"));
            java.util.concurrent.atomic.AtomicLong sunk = new java.util.concurrent.atomic.AtomicLong();
            try {
                Outcome<String> outcome = configured.invoke("six times seven?", sunk::addAndGet);
                assertThat(outcome).isInstanceOf(Outcome.Ok.class);
            } finally {
                System.clearProperty("mavai.llm.endpoint");
            }
            // The exchange's total — what punit's cost accounting sums.
            assertThat(sunk.get()).isEqualTo(15);
        }
    }

    @Test
    @DisplayName("a failed delivery is an Outcome failure, never a throw")
    void failedDeliveryIsAnOutcome() throws Exception {
        try (StubLlm stub = StubLlm.start()) {
            System.setProperty("mavai.llm.endpoint", stub.endpoint());
            stub.respond(503, "overloaded");
            LanguageModel model = LanguageModels.configure(Map.of(
                    "system-prompt", "You answer briefly.",
                    "model", "conformance-model"));
            Outcome<LmReply> outcome;
            try {
                outcome = model.invoke("hello?");
            } finally {
                System.clearProperty("mavai.llm.endpoint");
            }
            assertThat(outcome).isInstanceOf(Outcome.Fail.class);
        }
    }
}
