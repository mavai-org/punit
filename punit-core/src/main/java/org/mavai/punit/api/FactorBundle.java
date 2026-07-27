package org.mavai.punit.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A content-addressable projection of a factor value into the
 * canonical form consumed by the baseline spec YAML and by the
 * {@code factorBundleHash} segment of the baseline filename.
 *
 * <p>The bundle is produced from one of three shapes:
 *
 * <ul>
 *   <li>A Java {@code record} — the multi-component shape. The record's
 *       components are traversed in declaration order; each component
 *       value is lifted via {@link FactorValue#of(Object)} and the
 *       resulting pairs make up {@link #entries()}.</li>
 *   <li>A Java {@code enum} — the single-dimension shape. The enum's
 *       declaring-class simple name supplies the entry key (e.g.
 *       {@code LlmModel.GPT_4O} → {@code {"LlmModel":"GPT_4O"}}), and
 *       the constant is lifted as an {@link EnumValue}. This is the
 *       sugar for service contracts parameterised over a single named, finite
 *       set of choices, where defining a one-component wrapper record
 *       would add ceremony without information.</li>
 *   <li>A string-keyed {@code Map} — the named-entries shape, for
 *       callers whose factor components exist only at run time (the
 *       declarative reader's resolved configurations). Entries are
 *       taken in the map's iteration order and each value is lifted
 *       via {@link FactorValue#of(Object)}.</li>
 * </ul>
 *
 * <p>{@link #canonicalJson()} serialises the bundle with keys sorted
 * alphabetically, no whitespace, booleans as the literals {@code true}
 * / {@code false}, numbers in minimal decimal form, and strings /
 * temporals / URIs / enums as JSON-quoted strings. {@link #bundleHash()}
 * is the first four hex characters of {@code SHA-256(canonicalJson())}.
 *
 * <p>The empty bundle — produced by a record with no components — has
 * {@link #canonicalJson()} = {@code "{}"} and
 * {@link #bundleHash()} = the four-hex-char truncation of
 * {@code SHA-256("{}")}. The baseline-filename convention treats an
 * empty factor bundle by omitting the {@code factorBundleHash}
 * segment, not by emitting the hash of {@code "{}"}; that
 * filename-layer rule belongs to the serialiser, not here.
 */
public final class FactorBundle {

    private static final FactorBundle EMPTY = new FactorBundle(List.of());

    private final List<Entry> entries;

    private FactorBundle(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /** Name / raw-value pair. Exposed for inspection and YAML emission. */
    public record Entry(String name, FactorValue value) {}

    /**
     * The empty bundle — no factor components.
     *
     * @return the empty bundle
     */
    public static FactorBundle empty() {
        return EMPTY;
    }

    /**
     * Constructs a bundle from a factor value.
     *
     * <p>Two shapes are accepted:
     * <ul>
     *   <li>A Java record — its components are read in declaration
     *       order and each is lifted via {@link FactorValue#of(Object)}.</li>
     *   <li>A Java enum — produces a single-entry bundle keyed by the
     *       enum's declaring-class simple name, valued by the constant.</li>
     * </ul>
     *
     * @param factors a Java record or enum instance, non-null
     * @return the bundle
     * @throws NullPointerException if {@code factors} is null
     * @throws IllegalArgumentException if {@code factors} is neither a
     *         record nor an enum, or if a record component value is
     *         rejected by {@link FactorValue#of(Object)}.
     */
    public static <FT> FactorBundle of(FT factors) {
        Objects.requireNonNull(factors, "factors");
        if (factors instanceof java.util.Map<?, ?> named) {
            return fromMap(named);
        }
        if (!(factors instanceof Enum<?>) && !factors.getClass().isRecord()) {
            throw new IllegalArgumentException(
                    "factor type must be a record, enum, or string-keyed map, got "
                            + factors.getClass().getName());
        }
        if (factors instanceof Enum<?> e) {
            return fromEnum(e);
        }
        return fromRecord(factors);
    }

    /**
     * The named-entries shape: a string-keyed map of admissible scalar
     * values, in the map's iteration order — the form a declaratively
     * resolved configuration takes, where no record type exists to
     * carry the components. Canonicalisation and hashing are identical
     * to the record shape's.
     */
    private static FactorBundle fromMap(java.util.Map<?, ?> named) {
        if (named.isEmpty()) {
            return EMPTY;
        }
        List<Entry> out = new ArrayList<>(named.size());
        for (java.util.Map.Entry<?, ?> entry : named.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                throw new IllegalArgumentException(
                        "factor map keys must be strings, got "
                                + (entry.getKey() == null ? "null" : entry.getKey().getClass().getName()));
            }
            try {
                out.add(new Entry(name, FactorValue.of(entry.getValue())));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "factor map has inadmissible value for '" + name + "': " + e.getMessage(), e);
            }
        }
        return new FactorBundle(out);
    }

    private static FactorBundle fromEnum(Enum<?> e) {
        String key = e.getDeclaringClass().getSimpleName();
        return new FactorBundle(List.of(new Entry(key, FactorValue.of(e))));
    }

    private static FactorBundle fromRecord(Object factorRecord) {
        Class<?> type = factorRecord.getClass();
        RecordComponent[] components = type.getRecordComponents();
        if (components.length == 0) {
            return EMPTY;
        }
        List<Entry> out = new ArrayList<>(components.length);
        for (RecordComponent comp : components) {
            out.add(new Entry(comp.getName(), liftComponent(factorRecord, comp)));
        }
        return new FactorBundle(out);
    }

    private static FactorValue liftComponent(Object factorRecord, RecordComponent comp) {
        Object raw = readComponent(factorRecord, comp);
        try {
            return FactorValue.of(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "factor record " + factorRecord.getClass().getName()
                            + " has inadmissible component '"
                            + comp.getName() + "': " + e.getMessage(), e);
        }
    }

    private static Object readComponent(Object factorRecord, RecordComponent comp) {
        try {
            var accessor = comp.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(factorRecord);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    "failed to read record component " + comp.getName()
                            + " of " + factorRecord.getClass().getName(), e);
        }
    }

    /**
     * @return the entries in record-component declaration order
     */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * @return {@code true} when the bundle has no entries
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * The canonical JSON form — keys sorted alphabetically, no
     * whitespace, minimal numeric representation, strings /
     * temporals / URIs / enums as JSON-quoted strings.
     *
     * @return the canonical JSON string
     */
    public String canonicalJson() {
        if (entries.isEmpty()) {
            return "{}";
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Entry e : entries) {
            sorted.put(e.name(), e.value().canonical());
        }
        StringBuilder out = new StringBuilder();
        out.append('{');
        boolean first = true;
        for (var kv : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(StringValue.jsonQuote(kv.getKey()));
            out.append(':');
            out.append(kv.getValue());
        }
        out.append('}');
        return out.toString();
    }

    /**
     * @return the first four hex characters of {@code SHA-256(canonicalJson())}
     */
    public String bundleHash() {
        byte[] digest = sha256(canonicalJson());
        char[] hex = new char[4];
        for (int i = 0; i < 2; i++) {
            int b = digest[i] & 0xff;
            hex[2 * i] = HEX[b >>> 4];
            hex[2 * i + 1] = HEX[b & 0x0f];
        }
        return new String(hex);
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 must be supported by every JRE", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FactorBundle other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "FactorBundle" + canonicalJson();
    }
}
