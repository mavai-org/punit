package org.mavai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * References a static method that provides named service contract configurations for EXPLORE mode.
 *
 * <p>Each configuration is a fully-constructed, immutable service contract instance paired with
 * a name. The service contract instance <em>is</em> the factor specification — there is no need
 * for separate factor maps or factor annotations.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @ExploreExperiment(serviceContract = MyServiceContract.class, samplesPerConfig = 20)
 * @ConfigSource("modelConfigurations")
 * void compareModels(MyServiceContract serviceContract) {
 *     serviceContract.execute(input);
 * }
 *
 * static Stream<NamedConfig<MyServiceContract>> modelConfigurations() {
 *     return Stream.of(
 *         NamedConfig.of("gpt-4o-mini", new MyServiceContract(llm, "gpt-4o-mini", 0.1)),
 *         NamedConfig.of("gpt-4o", new MyServiceContract(llm, "gpt-4o", 0.1))
 *     );
 * }
 * }</pre>
 *
 * <p>The method must be static and return {@code Stream<NamedConfig<T>>} or
 * {@code Collection<NamedConfig<T>>}.
 *
 * @see NamedConfig
 * @see ExploreExperiment
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigSource {

    /**
     * Name of the static method providing configurations.
     *
     * <p>Resolution order:
     * <ul>
     *   <li>Simple name — search test class, then service contract class</li>
     *   <li>{@code ClassName#methodName} — search test class package, then service contract package</li>
     *   <li>Fully qualified — direct lookup</li>
     * </ul>
     */
    String value();
}
