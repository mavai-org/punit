package org.mavai.punit.internal.engine.spec.registry;

import java.util.Optional;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;

/**
 * Abstraction for resolving execution specifications by ID.
 *
 * <p>Implementations may resolve specs from the filesystem, classpath,
 * environment-local directories, or layered combinations thereof.
 *
 * @see SpecificationRegistry
 * @see LayeredSpecRepository
 */
public interface SpecRepository {

	/**
	 * Resolves a specification by its ID.
	 *
	 * @param specId the specification ID (e.g., service contract ID, or dimension-qualified ID like "MyServiceContract.latency")
	 * @return the specification if found, or empty if not available in this repository
	 */
	Optional<ExecutionSpecification> resolve(String specId);
}
