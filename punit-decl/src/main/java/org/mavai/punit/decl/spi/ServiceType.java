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
}
