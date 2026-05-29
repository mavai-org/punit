package org.mavai.punit.internal.engine.spec.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("LayeredSpecRepository")
class LayeredSpecRepositoryTest {

	@TempDir
	Path tempDir;

	private void createSpecFile(Path dir, String serviceContractId, double minPassRate) throws IOException {
		Files.createDirectories(dir);
		int successes = (int) (100 * minPassRate);
		int failures = 100 - successes;
		StringBuilder sb = new StringBuilder();
		sb.append("schemaVersion: punit-spec-2\n");
		sb.append("useCaseId: ").append(serviceContractId).append("\n");
		sb.append("generatedAt: 2026-03-07T10:00:00Z\n");
		sb.append("execution:\n");
		sb.append("  samplesPlanned: 100\n");
		sb.append("  samplesExecuted: 100\n");
		sb.append("  terminationReason: COMPLETED\n");
		sb.append("statistics:\n");
		sb.append("  successRate:\n");
		sb.append("    observed: ").append(String.format("%.4f", minPassRate)).append("\n");
		sb.append("    standardError: 0.0100\n");
		sb.append("    confidenceInterval95: [").append(String.format("%.4f", minPassRate - 0.02)).append(", ").append(String.format("%.4f", minPassRate + 0.02)).append("]\n");
		sb.append("  successes: ").append(successes).append("\n");
		sb.append("  failures: ").append(failures).append("\n");
		sb.append("empiricalBasis:\n");
		sb.append("  samples: 100\n");
		sb.append("  successes: ").append(successes).append("\n");
		sb.append("cost:\n");
		sb.append("  totalTimeMs: 1000\n");
		sb.append("  avgTimePerSampleMs: 10\n");
		sb.append("  totalTokens: 10000\n");
		sb.append("  avgTokensPerSample: 100\n");
		sb.append("requirements:\n");
		sb.append("  minPassRate: ").append(String.format("%.4f", minPassRate)).append("\n");
		String content = sb.toString();
		sb.append("contentFingerprint: ").append(SpecificationLoader.computeFingerprint(content)).append("\n");

		Files.writeString(dir.resolve(serviceContractId + ".yaml"), sb.toString());
	}

	@Nested
	@DisplayName("resolve")
	class Resolve {

		@Test
		@DisplayName("returns spec from first layer that has it")
		void returnsSpecFromFirstLayer() throws IOException {
			Path layer1 = tempDir.resolve("layer1");
			Path layer2 = tempDir.resolve("layer2");

			createSpecFile(layer1, "TestServiceContract", 0.90);
			createSpecFile(layer2, "TestServiceContract", 0.80);

			LayeredSpecRepository repo = new LayeredSpecRepository(List.of(
					new SpecificationRegistry(layer1),
					new SpecificationRegistry(layer2)));

			Optional<ExecutionSpecification> result = repo.resolve("TestServiceContract");

			assertThat(result).isPresent();
			assertThat(result.get().getMinPassRate()).isEqualTo(0.90);
		}

		@Test
		@DisplayName("falls back to second layer when first does not have spec")
		void fallsBackToSecondLayer() throws IOException {
			Path layer1 = tempDir.resolve("layer1");
			Path layer2 = tempDir.resolve("layer2");

			Files.createDirectories(layer1);
			createSpecFile(layer2, "TestServiceContract", 0.80);

			LayeredSpecRepository repo = new LayeredSpecRepository(List.of(
					new SpecificationRegistry(layer1),
					new SpecificationRegistry(layer2)));

			Optional<ExecutionSpecification> result = repo.resolve("TestServiceContract");

			assertThat(result).isPresent();
			assertThat(result.get().getMinPassRate()).isEqualTo(0.80);
		}

		@Test
		@DisplayName("returns empty when no layer has the spec")
		void returnsEmptyWhenNoLayerHasSpec() throws IOException {
			Path layer1 = tempDir.resolve("layer1");
			Path layer2 = tempDir.resolve("layer2");
			Files.createDirectories(layer1);
			Files.createDirectories(layer2);

			LayeredSpecRepository repo = new LayeredSpecRepository(List.of(
					new SpecificationRegistry(layer1),
					new SpecificationRegistry(layer2)));

			Optional<ExecutionSpecification> result = repo.resolve("NonExistent");

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("resolves dimension-qualified spec from correct layer")
		void resolvesDimensionQualifiedSpec() throws IOException {
			Path layer1 = tempDir.resolve("layer1");
			Path layer2 = tempDir.resolve("layer2");

			// Layer 1 has the latency spec
			createSpecFile(layer1, "TestServiceContract.latency", 0.95);
			// Layer 2 has the functional spec
			createSpecFile(layer2, "TestServiceContract", 0.85);

			LayeredSpecRepository repo = new LayeredSpecRepository(List.of(
					new SpecificationRegistry(layer1),
					new SpecificationRegistry(layer2)));

			Optional<ExecutionSpecification> functional = repo.resolve("TestServiceContract");
			Optional<ExecutionSpecification> latency = repo.resolve("TestServiceContract.latency");

			assertThat(functional).isPresent();
			assertThat(functional.get().getMinPassRate()).isEqualTo(0.85);
			assertThat(latency).isPresent();
			assertThat(latency.get().getMinPassRate()).isEqualTo(0.95);
		}
	}

	@Nested
	@DisplayName("construction")
	class Construction {

		@Test
		@DisplayName("throws on null layers")
		void throwsOnNullLayers() {
			assertThatThrownBy(() -> new LayeredSpecRepository(null))
					.isInstanceOf(NullPointerException.class);
		}

		@Test
		@DisplayName("reports layer count")
		void reportsLayerCount() {
			Path layer1 = tempDir.resolve("layer1");
			Path layer2 = tempDir.resolve("layer2");

			LayeredSpecRepository repo = new LayeredSpecRepository(List.of(
					new SpecificationRegistry(layer1),
					new SpecificationRegistry(layer2)));

			assertThat(repo.layerCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("empty layers returns empty for all resolutions")
		void emptyLayersReturnsEmpty() {
			LayeredSpecRepository repo = new LayeredSpecRepository(List.of());

			assertThat(repo.resolve("Anything")).isEmpty();
		}
	}
}
