package org.mavai.punit.decl.model;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed, structurally validated {@code mavai-contract/1} file — the
 * declarative source the reader instantiates as an ordinary service
 * contract. Posture-free (the run mode is the invocation's) and
 * budget-free (the file carries the claim; the invocation carries the
 * budget).
 *
 * @param contract the author-chosen contract name — the instantiated
 *     contract's identity
 * @param service the name of the service binding or definition the
 *     contract's invocation resolves to
 * @param transforms the declared views, in declaration order: view name
 *     to transformation name (a stock one — {@code json}/{@code xml}/
 *     {@code yaml} — or one registered in code)
 * @param inputs the fixed, finite input list, each entry carrying its
 *     value and its own expectations
 * @param criteria the criterion entries, in declaration order
 * @param intent the declared test intent
 * @param confidence the contract-level confidence for thresholded
 *     criteria
 * @param confidenceDeclared whether {@code confidence:} was written in
 *     the file (as opposed to defaulted)
 * @param latency the parsed {@code latency:} block, or {@code null}
 * @param sourcePath the file the contract was loaded from, or
 *     {@code null} when parsed from text
 */
public record ContractDeclaration(
        String contract,
        String service,
        Map<String, String> transforms,
        List<InputDeclaration> inputs,
        List<CriterionDeclaration> criteria,
        DeclaredIntent intent,
        double confidence,
        boolean confidenceDeclared,
        LatencyDeclaration latency,
        Path sourcePath) {

    /** The format identifier this declaration parses from. */
    public static final String FORMAT_IDENTIFIER = "mavai-contract/1";

    public ContractDeclaration {
        transforms = new LinkedHashMap<>(transforms);
        inputs = List.copyOf(inputs);
        criteria = List.copyOf(criteria);
    }

    /** Whether any input carries expectations of its own. */
    public boolean hasInputExpectations() {
        return inputs.stream().anyMatch(InputDeclaration::hasExpectations);
    }

    @Override
    public Map<String, String> transforms() {
        return Map.copyOf(transforms);
    }
}
