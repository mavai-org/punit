package org.mavai.punit.internal.engine.spec.registry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;

/**
 * A {@link SpecRepository} that resolves specifications by trying multiple
 * layers in order, returning the first match.
 *
 * <p>Default construction (via {@link #createDefault()}) provides two layers:
 * <ol>
 *   <li><b>Environment-local</b>: A directory specified by system property
 *       {@code punit.spec.dir} or environment variable {@code PUNIT_SPEC_DIR}.
 *       Skipped if neither is set.</li>
 *   <li><b>Classpath</b>: The existing default path ({@code punit/specs/} on the classpath).</li>
 * </ol>
 *
 * <p>This layered resolution enables environment-specific spec overrides
 * (e.g., latency baselines that differ between staging and production)
 * without modifying checked-in specs.
 */
public class LayeredSpecRepository implements SpecRepository {

	static final String PROP_SPEC_DIR = "punit.spec.dir";
	static final String ENV_SPEC_DIR = "PUNIT_SPEC_DIR";

	private final List<SpecRepository> layers;

	/**
	 * Creates a layered repository with the specified layers.
	 *
	 * @param layers the layers in priority order (first match wins)
	 */
	public LayeredSpecRepository(List<SpecRepository> layers) {
		Objects.requireNonNull(layers, "layers must not be null");
		this.layers = List.copyOf(layers);
	}

	/**
	 * Creates a default layered repository with environment-local and classpath layers.
	 *
	 * @return the default layered repository
	 */
	public static LayeredSpecRepository createDefault() {
		List<SpecRepository> layers = new ArrayList<>();

		// 1. Environment-local layer (highest priority)
		String specDir = resolveSpecDir();
		if (specDir != null && !specDir.isEmpty()) {
			Path localPath = Paths.get(specDir);
			if (Files.isDirectory(localPath)) {
				layers.add(new SpecificationRegistry(localPath));
			}
		}

		// 2. Classpath layer (default fallback)
		layers.add(new SpecificationRegistry());

		return new LayeredSpecRepository(layers);
	}

	@Override
	public Optional<ExecutionSpecification> resolve(String specId) {
		for (SpecRepository layer : layers) {
			Optional<ExecutionSpecification> result = layer.resolve(specId);
			if (result.isPresent()) {
				return result;
			}
		}
		return Optional.empty();
	}

	/**
	 * Returns the number of layers in this repository.
	 */
	public int layerCount() {
		return layers.size();
	}

	/**
	 * Returns an unmodifiable view of the layers.
	 */
	public List<SpecRepository> layers() {
		return Collections.unmodifiableList(layers);
	}

	private static String resolveSpecDir() {
		String sysProp = System.getProperty(PROP_SPEC_DIR);
		if (sysProp != null && !sysProp.isEmpty()) {
			return sysProp;
		}
		return System.getenv(ENV_SPEC_DIR);
	}
}
