package org.mavai.punit.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Source of inputs for a {@link Sampling}, exposing a stable
 * <em>content-derived</em> identity used as the unit of
 * sampling-frame identity for empirical pairing.
 *
 * <p>The framework <strong>always</strong> derives identity from
 * the materialised list's content — no author-provided labels, no
 * author-provided hash functions. This is by design: an authoring
 * model where the author labels the source is too easy to break
 * (the same label can be used twice for different content; the
 * framework can't tell). Hashing the content removes the gap —
 * identity equality strictly implies sampling-frame equivalence.
 *
 * <p>The user guide will document the authoring patterns (TODO when
 * the user guide lands).
 *
 * <h2>Two authoring shapes</h2>
 *
 * <p><strong>Inline values.</strong> Authors writing
 * {@code .inputs("a", "b", "c")} or {@code .inputs(values)} on
 * the {@link Sampling.Builder} get an internally-wrapped supplier
 * whose identity is a content hash of the values. No author-side
 * ceremony.
 *
 * <p><strong>External source.</strong> Authors with file-backed
 * fixtures, computed input lists, or any source that lives behind
 * a {@code Supplier} use {@link #from(Supplier)}:
 *
 * <pre>{@code
 * .inputs(InputSupplier.from(() -> loadFixture(fixtureFile)))
 * }</pre>
 *
 * The supplier is invoked lazily on first call to {@link #all()}
 * or {@link #identity()} and the result memoised. The framework
 * computes the content hash at that moment and records it as the
 * identity.
 *
 * <h2>What "stable identity" requires</h2>
 *
 * <p>Identity is a SHA-256 of a canonical string encoding of the
 * values' {@code String.valueOf(...)} representations. The
 * encoding is stable for:
 *
 * <ul>
 *   <li>Primitive wrappers ({@code Integer}, {@code Long}, {@code Boolean}, …)</li>
 *   <li>{@code String}</li>
 *   <li>{@code enum} constants ({@code toString} returns the
 *       constant name)</li>
 *   <li>Java records of the above (record {@code toString} is
 *       specified)</li>
 *   <li>Lists / Maps of the above (their {@code toString} is
 *       deterministic given stable element {@code toString})</li>
 * </ul>
 *
 * <p>It is <strong>not</strong> stable for custom classes whose
 * {@code toString} includes object identity, mutable state, or
 * non-deterministic fields. For those, prefer wrapping the
 * relevant data in a record so {@code toString} becomes
 * deterministic. The framework does not provide an opt-out for
 * unstable types — the integrity check is the framework's whole
 * job, and an opt-out path would be the gap the all-content-hash
 * model is designed to close.
 *
 * @param <IT> the per-sample input type
 */
// mavai-ref: JVI-WXK2ZQP — do not remove (resolves in mavai-orchestrator)
public interface InputSupplier<IT> {

    /**
     * @return the inputs themselves, in canonical order. Non-empty.
     *         Implementations materialise lazily on first call.
     */
    List<IT> all();

    /**
     * @return a stable string identifying this source. The
     *         framework computes this as a SHA-256 content hash
     *         of the materialised list.
     */
    String identity();

    /**
     * Wraps a supplier. The framework computes a content hash of
     * the materialised list on first call to {@link #all()} or
     * {@link #identity()} and memoises the result. Identity is
     * the content hash.
     *
     * <p>Use this when the inputs come from outside the source
     * code — file-backed fixtures, computed datasets, lazily-
     * loaded resources. For inline literal values, the
     * {@link Sampling.Builder}'s {@code .inputs(values)} overloads
     * are sugar for the same machinery.
     *
     * @throws NullPointerException  if {@code supplier} is null,
     *                               or if the supplier returns null
     *                               at materialisation
     * @throws IllegalStateException if the supplier returns an
     *                               empty list at materialisation
     */
    static <IT> InputSupplier<IT> from(Supplier<List<IT>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new InputSupplier<IT>() {
            private List<IT> cachedAll;
            private String cachedIdentity;

            private synchronized void materialise() {
                if (cachedAll != null) {
                    return;
                }
                List<IT> result = Objects.requireNonNull(
                        supplier.get(), "supplier returned null");
                if (result.isEmpty()) {
                    throw new IllegalStateException(
                            "supplier returned an empty list");
                }
                List<IT> snapshot = List.copyOf(result);
                cachedAll = snapshot;
                cachedIdentity = "sha256:" + sha256(canonicalEncoding(snapshot));
            }

            @Override
            public List<IT> all() {
                materialise();
                return cachedAll;
            }

            @Override
            public String identity() {
                materialise();
                return cachedIdentity;
            }

            @Override
            public String toString() {
                return cachedIdentity == null
                        ? "InputSupplier.from(<unmaterialised>)"
                        : "InputSupplier.from(" + cachedIdentity + ")";
            }
        };
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Canonical string encoding of a list, used as the input to
     * {@link #sha256(String)} for identity computation.
     *
     * <p>Format: {@code [v0,v1,…]} where each {@code vi} is
     * {@code String.valueOf(values.get(i))}. Stable across runs
     * given stable {@code toString} on each element type.
     */
    private static String canonicalEncoding(List<?> values) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object v : values) {
            if (!first) {
                sb.append(",");
            }
            sb.append(String.valueOf(v));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK 8+; this branch is unreachable.
            throw new AssertionError("SHA-256 unavailable on this JVM", e);
        }
    }
}
