package org.mavai.punit.decl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Graduation — the source projection")
class ContractMaterialiseTest {

    private Path writeContract(Path directory) throws IOException {
        Path contract = directory.resolve("contract.yaml");
        Files.writeString(contract, """
                format: mavai-contract/1
                contract: basket-builder-returns-valid-baskets
                service: basket-builder
                transforms:
                  basket: json
                criteria:
                  - name: baskets-parse-and-mention-eggs
                    threshold: 0.9
                    optional-slack: 1
                    parses: basket
                    postconditions:
                      - contains: "egg"
                      - in: basket
                        path: "$.items[*].name"
                        equals-set: ["egg", "milk"]
                      - contains: "fresh"
                        optional: true
                  - name: never-rude
                    threshold: 0.99
                    not-equals: "go away"
                inputs: ["a dozen eggs", "two bottles of milk"]
                """);
        return contract;
    }

    @Test
    @DisplayName("the emitted source states the same claims in declaration order")
    void emissionStatesTheClaims(@TempDir Path directory) throws IOException {
        String source = ContractMaterialise.run(writeContract(directory));
        assertThat(source)
                .contains("public final class BasketBuilderReturnsValidBasketsServiceContract")
                .contains("return \"basket-builder-returns-valid-baskets\";")
                .contains("binding 'basket-builder'")
                .contains("meeting().<String>passRate(0.9)")
                .contains(".name(\"baskets-parse-and-mention-eggs\")")
                .contains(".optionalSlack(1)")
                .contains("meeting().<String>passRate(0.99)")
                .contains(".optional()")
                .contains("JSONPath/XPath");
        // Graduation transfers ownership: the reader's internals never leak.
        assertThat(source).doesNotContain("org.mavai.punit.decl");
    }

    @Test
    @DisplayName("the emitted source compiles cleanly against punit's authoring surface")
    void emissionCompiles(@TempDir Path directory) throws IOException {
        String source = ContractMaterialise.run(writeContract(directory));
        String className = ContractMaterialise.className("basket-builder-returns-valid-baskets");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject unit = new SimpleJavaFileObject(
                URI.create("string:///" + className + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        var diagnostics = new javax.tools.DiagnosticCollector<JavaFileObject>();
        boolean compiled = compiler.getTask(null, null, diagnostics,
                List.of("-d", directory.resolve("classes").toString(),
                        "-classpath", System.getProperty("java.class.path")),
                null, List.of(unit)).call();
        assertThat(compiled)
                .as("emitted source compiles: " + diagnostics.getDiagnostics())
                .isTrue();
    }
}
