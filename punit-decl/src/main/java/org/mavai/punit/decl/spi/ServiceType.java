package org.mavai.punit.decl.spi;

import java.util.Map;

/**
 * A built-in service type: the deliberate extension seam through which
 * a module registers what a {@code mavai-services/1} definition's
 * {@code type:} key can name — discovered via
 * {@link java.util.ServiceLoader} (punit-lm provides
 * {@code language-model}). User types register through the bindings
 * class instead; a built-in's name cannot be shadowed.
 *
 * <p>This SPI and {@link ConfiguredService} are the whole of the
 * extension surface: everything else in punit-decl is internal
 * enablement, not API.
 */
public interface ServiceType {

    /** The type name a definition's {@code type:} key resolves to. */
    String name();

    /**
     * Validates a definition's complete {@code configuration:} record
     * against this type's schema and returns the configured, invocable
     * service. Runs at contract-load time — a misfit throws (an
     * {@link IllegalStateException} subtype) before any sample.
     *
     * @param serviceName the definition's name (for refusal messages)
     * @param configuration the resolved configuration record
     */
    ConfiguredService configure(String serviceName, Map<String, Object> configuration);

    /**
     * Configures one exploration grid point — the lenient tier of the
     * family's two-tier capability rule: where {@link #configure} refuses
     * a configuration the resolved adapter cannot honour (a test or
     * measurement must never silently measure something else), an
     * explore grid may span providers with differing support, so the
     * type drops what a point's provider cannot honour and states it in
     * the returned note, which the run announces before the point's
     * samples. The default is the strict path with no note — a type
     * with uniform support need not distinguish the tiers.
     *
     * @param serviceName the definition's name (for refusal messages)
     * @param configuration the resolved grid-point record
     */
    default ExplorePoint explorePoint(String serviceName, Map<String, Object> configuration) {
        return new ExplorePoint(configure(serviceName, configuration), null);
    }

    /**
     * One configured exploration grid point: the service that actually
     * runs — carrying only what its provider honoured — and the
     * degradation note to announce, {@code null} when nothing was
     * dropped.
     */
    record ExplorePoint(ConfiguredService service, String note) {}
}
