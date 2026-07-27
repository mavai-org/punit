package org.mavai.punit.decl.internal.model;

/**
 * The postcondition form vocabulary of the contract format — a real
 * criterion declares a compatible subset, since all its forms must hold
 * together (a conjunction).
 *
 * <p>The vocabulary spans the string forms, {@code parses}/{@code
 * satisfies}, and the value-comparison forms (value comparison and
 * boolean amendments, 2026-07-27): the scalar forms — universal over a
 * multi-valued selection like the string forms, judging the selected
 * value itself, never a text projection — and the collective set forms,
 * which judge the whole selection at once and therefore require a
 * declared view and a {@code path:}.
 */
public enum PostconditionForm {
    EQUALS("equals", Category.STRING),
    ONE_OF("one-of", Category.STRING),
    CONTAINS("contains", Category.STRING),
    MATCHES("matches", Category.STRING),
    PARSES("parses", Category.STRUCTURAL),
    SATISFIES("satisfies", Category.STRUCTURAL),
    EQ("eq", Category.NUMERIC),
    NE("ne", Category.NUMERIC),
    LT("lt", Category.NUMERIC),
    LE("le", Category.NUMERIC),
    GT("gt", Category.NUMERIC),
    GE("ge", Category.NUMERIC),
    NOT_EQUALS("not-equals", Category.SCALAR_VALUE),
    EQUALS_CI("equals-ci", Category.SCALAR_VALUE),
    IS_NULL("is-null", Category.SCALAR_VALUE),
    IS("is", Category.SCALAR_VALUE),
    EQUALS_SET("equals-set", Category.COLLECTIVE),
    CONTAINS_SET("contains-set", Category.COLLECTIVE),
    COUNT_EQUALS("count-equals", Category.COLLECTIVE);

    private enum Category { STRING, STRUCTURAL, NUMERIC, SCALAR_VALUE, COLLECTIVE }

    private final String key;
    private final Category category;

    PostconditionForm(String key, Category category) {
        this.key = key;
        this.category = category;
    }

    /** The form's key as written in the contract file. */
    public String key() {
        return key;
    }

    /** A string form: judges a text projection of its subject. */
    public boolean stringForm() {
        return category == Category.STRING;
    }

    /** A numeric comparison: subject and operand interpreted as exact decimals. */
    public boolean numeric() {
        return category == Category.NUMERIC;
    }

    /** A scalar value form: universal over a selection, judging each value itself. */
    public boolean scalarValue() {
        return category == Category.NUMERIC || category == Category.SCALAR_VALUE;
    }

    /** A set form: judges the whole selection collectively — requires a view and a path. */
    public boolean collective() {
        return category == Category.COLLECTIVE;
    }

    /** Whether the form may carry a {@code path:} under a declared view. */
    public boolean pathCapable() {
        return stringForm() || scalarValue() || collective();
    }
}
