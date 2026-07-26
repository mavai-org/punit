package org.mavai.punit.decl.internal.model;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A parsed, structurally validated {@code mavai-services/1} file: the
 * declarative service definitions a contract's {@code service:} key
 * resolves against, one definition per named service.
 *
 * @param services the definitions, keyed by service name, in
 *     declaration order
 * @param sourcePath the file the definitions were loaded from, or
 *     {@code null} when parsed from text
 */
public record ServicesDeclaration(Map<String, ServiceEntry> services, Path sourcePath) {

    /** The format identifier this declaration parses from. */
    public static final String FORMAT_IDENTIFIER = "mavai-services/1";

    /** The conventional filename discovery looks for. */
    public static final String CONVENTIONAL_FILENAME = "mavai-services.yaml";

    public ServicesDeclaration {
        services = new LinkedHashMap<>(services);
    }

    @Override
    public Map<String, ServiceEntry> services() {
        return Map.copyOf(services);
    }
}
