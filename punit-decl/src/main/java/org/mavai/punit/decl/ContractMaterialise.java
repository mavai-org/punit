package org.mavai.punit.decl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Graduation — emit the equivalent contract as Java source the
 * developer owns: the {@code mavaiMaterialise} Gradle task's entry
 * point. The emitted class is the contract the contract file
 * instantiates — same criteria, same thresholds, same declaration
 * order — expressed against punit's authoring surface, with a stub
 * invocation (graduation transfers invocation ownership) and TODOs
 * where the reader's private machinery (selection engines, registered
 * predicates, views) becomes the developer's own code. One-shot
 * scaffolding: nothing round-trips.
 */
public final class ContractMaterialise {

    private ContractMaterialise() {}

    /** Renders the contract file as a standalone Java class's source. */
    public static String run(Path contractFile) {
        String text;
        try {
            text = Files.readString(contractFile);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return org.mavai.punit.decl.internal.run.Materialiser.materialise(
                org.mavai.punit.decl.internal.parser.ContractParser.parse(text, contractFile));
    }

    /** The emitted class's simple name for a contract id — names the file. */
    public static String className(String contractId) {
        return org.mavai.punit.decl.internal.run.Materialiser.typeName(contractId);
    }
}
