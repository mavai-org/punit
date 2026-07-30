package org.mavai.punit.decl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("The check verb — zero-sample load validation")
class ContractCheckTest {

    private static final String BINDINGS =
            "org.mavai.punit.decl.internal.run.MavaiBindings";

    private Path writeContract(Path directory, String service) throws IOException {
        Path contract = directory.resolve("contract.yaml");
        Files.writeString(contract, """
                format: mavai-contract/1
                contract: checked-contract
                service: %s
                criteria:
                  - threshold: 0.9
                    contains: "hello"
                inputs: ["Alice", "Bob"]
                """.formatted(service));
        return contract;
    }

    @Test
    @DisplayName("a bare code binding resolves and the facts state the joins")
    void bareBindingResolves(@TempDir Path directory) throws IOException {
        List<String> facts = ContractCheck.run(
                writeContract(directory, "greeting-service"), BINDINGS,
                directory.resolve("explorations"));
        assertThat(facts).contains(
                "contract 'checked-contract': 1 criteria, 2 inputs",
                "service 'greeting-service': bare code binding resolved");
    }

    @Test
    @DisplayName("a services definition configures strictly and admits every input")
    void definitionConfigures(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("mavai-services.yaml"), """
                format: mavai-services/1
                services:
                  echoed:
                    type: echo-model
                    configuration:
                      prefix: "hello"
                    explorations:
                      - prefix: "howdy"
                      - prefix: "hey"
                """);
        List<String> facts = ContractCheck.run(
                writeContract(directory, "echoed"), BINDINGS,
                directory.resolve("explorations"));
        assertThat(facts).contains(
                "service 'echoed': definition configured strictly, every input admitted",
                "exploration grid: 2 entries constructed and joined");
    }

    @Test
    @DisplayName("an unresolvable service is refused with the run's own vocabulary")
    void unresolvableServiceRefused(@TempDir Path directory) throws IOException {
        Path contract = writeContract(directory, "nowhere-service");
        assertThatThrownBy(() -> ContractCheck.run(contract, BINDINGS,
                directory.resolve("explorations")))
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining("resolves to nothing");
    }

    @Test
    @DisplayName("a named-but-absent bindings class is noted, never refused")
    void absentBindingsClassNoted(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("mavai-services.yaml"), """
                format: mavai-services/1
                services:
                  echoed:
                    type: echo-model
                    configuration:
                      prefix: "hello"
                """);
        List<String> facts = ContractCheck.run(
                writeContract(directory, "echoed"), "com.example.NoSuchBindings",
                directory.resolve("explorations"));
        assertThat(facts).anySatisfy(fact ->
                assertThat(fact).contains("no bindings class 'com.example.NoSuchBindings'"));
    }

    @Test
    @DisplayName("the stale-artefact advisory names strays and vintages, deletes nothing")
    void staleArtefactAdvisory(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("mavai-services.yaml"), """
                format: mavai-services/1
                services:
                  echoed:
                    type: echo-model
                    configuration:
                      prefix: "hello"
                    explorations:
                      - prefix: "howdy"
                """);
        // The active experiment directory: <contract-id>/<swept-keys>/.
        // The current grid writes prefix-hello / prefix-howdy; a stray
        // from a re-gridded sweep sits beside them.
        Path active = directory.resolve("explorations")
                .resolve("checked-contract").resolve("prefix");
        Files.createDirectories(active);
        Files.writeString(active.resolve("prefix-hello.yaml"),
                "generatedAt: \"2026-07-28T10:00:00Z\"\n");
        Files.writeString(active.resolve("prefix-bonjour.yaml"),
                "generatedAt: \"2026-07-30T10:00:00Z\"\n");
        List<String> facts = ContractCheck.run(
                writeContract(directory, "echoed"), BINDINGS,
                directory.resolve("explorations"));
        assertThat(facts).contains(
                "stale: prefix-bonjour.yaml — no current grid point resolves to this "
                        + "configuration",
                "note: artefacts span 2 run vintages (2026-07-28, 2026-07-30)");
        // Advisory only: both artefacts are still on disk.
        assertThat(active.resolve("prefix-bonjour.yaml")).exists();
    }
}
