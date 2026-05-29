package org.mavai.punit.internal.engine.spec.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;

/**
 * Registry for loading and resolving execution specifications.
 *
 * <p>Specifications are loaded from the filesystem and cached.
 *
 * <h2>Spec ID Format</h2>
 * <p>Spec IDs are simply the service contract ID (e.g., "ShoppingServiceContract").
 * Dimension-qualified IDs are also supported (e.g., "ShoppingServiceContract.latency").
 * Specs are stored as flat files: {@code punit/specs/{serviceContractId}.yaml}
 *
 * <h2>Dimension-Qualified Resolution</h2>
 * <p>When a spec ID ends with ".latency", the registry looks for a dedicated
 * latency spec file ({@code {serviceContractId}.latency.yaml}). If not found, it falls
 * back to the older single-file spec format and extracts latency data from it.
 */
public class SpecificationRegistry implements SpecRepository {

	private final Path specsRoot;
	private final Map<String, ExecutionSpecification> cache = new ConcurrentHashMap<>();

	/**
	 * Creates a registry with the specified specs root directory.
	 *
	 * @param specsRoot the root directory for specifications
	 */
	public SpecificationRegistry(Path specsRoot) {
		this.specsRoot = Objects.requireNonNull(specsRoot, "specsRoot must not be null");
	}

	/**
	 * Creates a registry with the default specs root.
	 *
	 * <p>Default: {@code src/test/resources/punit/specs}
	 */
	public SpecificationRegistry() {
		this(detectDefaultSpecsRoot());
	}

	private static Path detectDefaultSpecsRoot() {
		// Try common locations
		Path[] candidates = {
				Paths.get("src", "test", "resources", "punit", "specs"),
				Paths.get("punit", "specs"),
				Paths.get("specs")
		};

		for (Path candidate : candidates) {
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
		}

		// Return default even if it doesn't exist yet
		return candidates[0];
	}

	/**
	 * Resolves a specification by its ID, returning it as an Optional.
	 *
	 * <p>This is the {@link SpecRepository} interface method. It returns empty
	 * instead of throwing when a spec is not found.
	 *
	 * @param specId the specification ID
	 * @return the specification if found, or empty
	 */
	@Override
	public Optional<ExecutionSpecification> resolve(String specId) {
		if (specId == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(resolveOrThrow(specId));
		} catch (SpecificationNotFoundException e) {
			return Optional.empty();
		}
	}

	/**
	 * Resolves a specification by its ID, throwing if not found.
	 *
	 * @param specId the specification ID (the service contract ID, e.g., "ShoppingServiceContract")
	 * @return the loaded specification
	 * @throws SpecificationNotFoundException if not found
	 * @throws org.mavai.punit.internal.engine.spec.model.SpecificationValidationException if invalid
	 */
	public ExecutionSpecification resolveOrThrow(String specId) {
		Objects.requireNonNull(specId, "specId must not be null");
		// Strip any older version suffix (e.g., ":v1") for backwards compatibility
		String normalizedId = stripVersionSuffix(specId);
		return cache.computeIfAbsent(normalizedId, this::loadSpec);
	}

	private String stripVersionSuffix(String specId) {
		int colonIndex = specId.lastIndexOf(':');
		if (colonIndex > 0 && specId.substring(colonIndex + 1).startsWith("v")) {
			return specId.substring(0, colonIndex);
		}
		return specId;
	}

	private ExecutionSpecification loadSpec(String specId) {
		Optional<Path> specPathOpt = resolveSpecPath(specId);

		if (specPathOpt.isEmpty()) {
			throw new SpecificationNotFoundException(
					"Specification not found: " + specId +
							" (tried " + specId + ".yaml/.yml in " + specsRoot + ")");
		}

		try {
			ExecutionSpecification spec = SpecificationLoader.load(specPathOpt.get());
			spec.validate();
			return spec;
		} catch (IOException e) {
			throw new SpecificationLoadException(
					"Failed to load specification: " + specId, e);
		}
	}

	private Optional<Path> resolveSpecPath(String specId) {
		// Flat structure: specs/{specId}.yaml
		Path yamlPath = specsRoot.resolve(specId + ".yaml");
		if (Files.exists(yamlPath)) return Optional.of(yamlPath);

		Path ymlPath = specsRoot.resolve(specId + ".yml");
		if (Files.exists(ymlPath)) return Optional.of(ymlPath);

		return Optional.empty();
	}

	/**
	 * Returns true if a specification with the given ID exists.
	 *
	 * @param specId the specification ID
	 * @return true if the spec exists
	 */
	public boolean exists(String specId) {
		if (specId == null) return false;

		String normalizedId = stripVersionSuffix(specId);
		if (cache.containsKey(normalizedId)) return true;

		return resolveSpecPath(normalizedId).isPresent();
	}

	/**
	 * Clears the specification cache.
	 */
	public void clearCache() {
		cache.clear();
	}

	/**
	 * Returns the specs root directory.
	 */
	public Path getSpecsRoot() {
		return specsRoot;
	}

}
