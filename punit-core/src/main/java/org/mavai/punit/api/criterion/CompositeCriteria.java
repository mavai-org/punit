package org.mavai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The §1.4.6 composite verdict-producing strategy: a list of named
 * {@link Decl}s, evaluated independently and aggregated FAIL-dominantly
 * to resolve the contract verdict.
 *
 * <p>Constructed via {@link Criteria#of(Decl[]) Criteria.of(...)}.
 * Authors do not construct this type directly. Composition order is
 * preserved — the verdict path renders per-criterion rows in
 * declaration order.
 *
 * <p>Each {@link Decl} carries its own {@code .name(String)}; names
 * are read off the decls at lowering time and used as the runtime
 * criterion ids.
 *
 * @param <O> the contract's per-sample output value type
 */
public final class CompositeCriteria<O> implements Criteria<O> {

    private final List<Decl<O>> decls;

    CompositeCriteria(List<Decl<O>> decls) {
        Objects.requireNonNull(decls, "decls");
        if (decls.isEmpty()) {
            throw new IllegalArgumentException(
                    "CompositeCriteria requires at least one decl");
        }
        for (Decl<O> d : decls) {
            Objects.requireNonNull(d, "decl");
        }
        this.decls = List.copyOf(decls);
    }

    /** Decls in declaration order. */
    public List<Decl<O>> decls() {
        return decls;
    }

    @Override
    public List<Criterion<O>> asList() {
        List<Criterion<O>> out = new ArrayList<>(decls.size());
        for (Decl<O> d : decls) {
            String id = d.name().orElseThrow(() -> new IllegalStateException(
                    "decl reached CompositeCriteria.asList() without a name; "
                            + "Criteria.of(...) should have rejected this"));
            out.add(d.toRuntime(id));
        }
        return List.copyOf(out);
    }
}
