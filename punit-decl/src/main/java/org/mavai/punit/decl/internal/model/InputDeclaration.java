package org.mavai.punit.decl.internal.model;

import java.util.List;
import org.mavai.punit.decl.spi.FileInput;
import org.mavai.punit.decl.spi.MessageParts;

/**
 * One entry of the input list: the input's value together with its own
 * expectations — the shape the author wrote, whether as a bare value or
 * an {@code {input, expected}} entry. Keeping the pair as one record
 * makes the format's coupling structural: an expectation cannot name a
 * position that does not exist, and the input's position is simply its
 * place in the list.
 *
 * @param value the input value — a scalar, a {@code List} of scalars
 *     (an argument tuple), a {@link FileInput}, or a
 *     {@link MessageParts}
 * @param expected the input's own postcondition forms — the same
 *     conjunction, the same single Bernoulli stream, made
 *     input-dependent. Empty when the entry declared none: the format
 *     refuses an empty {@code expected:} list at parse, so an empty
 *     list here can only mean a bare input
 */
public record InputDeclaration(Object value, List<FormDeclaration> expected) {

    public InputDeclaration {
        expected = List.copyOf(expected);
    }

    /** A bare input entry, carrying no expectations of its own. */
    public static InputDeclaration bare(Object value) {
        return new InputDeclaration(value, List.of());
    }

    /** Whether this entry carries expectations of its own. */
    public boolean hasExpectations() {
        return !expected.isEmpty();
    }
}
