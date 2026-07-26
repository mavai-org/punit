package org.mavai.punit.decl.internal.model;

import java.util.List;

/**
 * An ordered multimodal message: several input parts in message order.
 * Each part is a decoded text {@link String} or a {@link FileInput}.
 *
 * @param parts the parts, in message order
 */
public record MessageParts(List<Object> parts) {

    public MessageParts {
        parts = List.copyOf(parts);
    }
}
