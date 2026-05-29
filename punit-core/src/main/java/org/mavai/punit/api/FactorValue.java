package org.mavai.punit.api;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * A scalar factor value admissible as a component of a factor record.
 *
 * <p>{@code FactorValue} is sealed: the set of permitted subtypes is
 * the canonical list of types a factor record component may take.
 * Non-scalar types (collections, maps, arbitrary objects) are rejected
 * when a factor record is lifted into a {@link FactorBundle}.
 *
 * <p>Two projections are exposed: {@link #canonical()} produces the
 * string form used to compute the factor-bundle hash in the baseline
 * spec filename; {@link #yamlValue()} produces the native
 * representation serialised into the baseline spec's {@code factors:}
 * block.
 *
 * <p>Lifting a raw value is done with {@link #of(Object)}, which
 * matches the runtime type against the permitted set and throws if
 * the type is inadmissible.
 */
public sealed interface FactorValue
        permits BooleanValue,
                IntegralValue,
                DecimalValue,
                StringValue,
                EnumValue,
                DurationValue,
                InstantValue,
                UriValue {

    /**
     * The canonical string form. Used as input to the
     * {@link FactorBundle#canonicalJson() canonical JSON} serialisation
     * that is then SHA-256'd to produce the
     * {@link FactorBundle#bundleHash() factor bundle hash}.
     *
     * <p>Numbers use minimal decimal representation (no trailing zeros,
     * no leading plus sign). Booleans are the JSON literals
     * {@code true} / {@code false}. Strings are JSON-quoted. Enums,
     * durations, instants, and URIs are rendered as JSON-quoted
     * strings in their obvious ISO / native form.
     *
     * @return the canonical form
     */
    String canonical();

    /**
     * The value as it should appear in the YAML factor block. Numbers
     * and booleans are the raw Java values (to keep their native YAML
     * type); enums, durations, instants, and URIs render as strings.
     *
     * @return the YAML value
     */
    Object yamlValue();

    /**
     * Lifts a raw value into its matching {@code FactorValue} subtype.
     *
     * <p>Admissible runtime types: {@code boolean}, {@code Boolean};
     * {@code byte}/{@code short}/{@code int}/{@code long} and boxed
     * equivalents; {@code float}/{@code double} and boxed equivalents;
     * {@link String}; any {@link Enum}; {@link Duration};
     * {@link Instant}; {@link URI}.
     *
     * @param raw the raw value
     * @return the wrapped factor value
     * @throws NullPointerException if {@code raw} is null
     * @throws IllegalArgumentException if the runtime type is not permitted
     */
    static FactorValue of(Object raw) {
        if (raw == null) {
            throw new NullPointerException("factor value must not be null");
        }
        return switch (raw) {
            case Boolean b -> new BooleanValue(b);
            case Byte v -> new IntegralValue(v.longValue());
            case Short v -> new IntegralValue(v.longValue());
            case Integer v -> new IntegralValue(v.longValue());
            case Long v -> new IntegralValue(v);
            case Float v -> new DecimalValue(v.doubleValue());
            case Double v -> new DecimalValue(v);
            case String v -> new StringValue(v);
            case Enum<?> v -> new EnumValue(v.getDeclaringClass().getName(), v.name());
            case Duration v -> new DurationValue(v);
            case Instant v -> new InstantValue(v);
            case URI v -> new UriValue(v);
            default -> new StringValue(raw.toString());
        };
    }
}
