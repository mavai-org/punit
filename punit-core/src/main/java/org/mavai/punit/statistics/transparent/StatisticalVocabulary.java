package org.mavai.punit.statistics.transparent;

/**
 * Statistical vocabulary constants for transparent mode output.
 *
 * <p>Provides proper mathematical symbols with ASCII fallbacks for terminals
 * that don't support Unicode.
 */
public final class StatisticalVocabulary {

    private StatisticalVocabulary() {
        // Constants only
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYMBOLS (Unicode)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Sample proportion symbol: p̂ */
    public static final String P_HAT = "p̂";

    /** Population proportion symbol: π */
    public static final String PI = "π";

    /** Null hypothesis symbol: H₀ */
    public static final String H0 = "H₀";

    /** Alternative hypothesis symbol: H₁ */
    public static final String H1 = "H₁";

    /** Less than or equal: ≤ */
    public static final String LEQ = "≤";

    /** Greater than or equal: ≥ */
    public static final String GEQ = "≥";

    /** Not equal: ≠ */
    public static final String NEQ = "≠";

    /** Square root: √ */
    public static final String SQRT = "√";

    /** Approximately: ≈ */
    public static final String APPROX = "≈";

    /** Alpha: α */
    public static final String ALPHA = "α";

    /** Multiplication: × */
    public static final String TIMES = "×";

    /** Sample size subscript zero: ₀ */
    public static final String SUB_ZERO = "₀";

    // ═══════════════════════════════════════════════════════════════════════════
    // SYMBOLS (ASCII fallbacks)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Sample proportion symbol (ASCII): p-hat */
    public static final String P_HAT_ASCII = "p-hat";

    /** Population proportion symbol (ASCII): pi */
    public static final String PI_ASCII = "pi";

    /** Null hypothesis symbol (ASCII): H0 */
    public static final String H0_ASCII = "H0";

    /** Alternative hypothesis symbol (ASCII): H1 */
    public static final String H1_ASCII = "H1";

    /** Less than or equal (ASCII): &le; */
    public static final String LEQ_ASCII = "<=";

    /** Greater than or equal (ASCII): &ge; */
    public static final String GEQ_ASCII = ">=";

    /** Not equal (ASCII): != */
    public static final String NEQ_ASCII = "!=";

    /** Square root (ASCII): sqrt */
    public static final String SQRT_ASCII = "sqrt";

    /** Approximately (ASCII): ~= */
    public static final String APPROX_ASCII = "~=";

    /** Alpha (ASCII): alpha */
    public static final String ALPHA_ASCII = "alpha";

    /** Multiplication (ASCII): * */
    public static final String TIMES_ASCII = "*";

    // ═══════════════════════════════════════════════════════════════════════════
    // BOX DRAWING (Unicode)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Double horizontal line: ═ */
    public static final String BOX_HORIZONTAL_DOUBLE = "═";

    /** Single horizontal line: ─ */
    public static final String BOX_HORIZONTAL = "─";

    /** Vertical line: │ */
    public static final String BOX_VERTICAL = "│";

    /** Top-left corner: ┌ */
    public static final String BOX_TOP_LEFT = "┌";

    /** Top-right corner: ┐ */
    public static final String BOX_TOP_RIGHT = "┐";

    /** Bottom-left corner: └ */
    public static final String BOX_BOTTOM_LEFT = "└";

    /** Bottom-right corner: ┘ */
    public static final String BOX_BOTTOM_RIGHT = "┘";

    /** T-junction down: ┬ */
    public static final String BOX_T_DOWN = "┬";

    /** T-junction up: ┴ */
    public static final String BOX_T_UP = "┴";

    /** T-junction right: ├ */
    public static final String BOX_T_RIGHT = "├";

    /** T-junction left: ┤ */
    public static final String BOX_T_LEFT = "┤";

    /** Cross junction: ┼ */
    public static final String BOX_CROSS = "┼";

    // ═══════════════════════════════════════════════════════════════════════════
    // BOX DRAWING (ASCII fallbacks)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Double horizontal line (ASCII): = */
    public static final String BOX_HORIZONTAL_DOUBLE_ASCII = "=";

    /** Single horizontal line (ASCII): - */
    public static final String BOX_HORIZONTAL_ASCII = "-";

    /** Vertical line (ASCII): | */
    public static final String BOX_VERTICAL_ASCII = "|";

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a symbols provider based on Unicode support.
     *
     * @param useUnicode true to use Unicode symbols, false for ASCII fallbacks
     * @return a symbols provider
     */
    public static Symbols symbols(boolean useUnicode) {
        return useUnicode ? new UnicodeSymbols() : new AsciiSymbols();
    }

    /**
     * Interface for accessing symbols based on terminal capabilities.
     */
    public interface Symbols {
        String pHat();
        String pi();
        String h0();
        String h1();
        String leq();
        String geq();
        String neq();
        String sqrt();
        String approx();
        String alpha();
        String times();
        String horizontalDouble();
        String horizontal();
        String vertical();

        /**
         * Creates a horizontal line of specified width using double-line characters.
         */
        default String doubleLine(int width) {
            return horizontalDouble().repeat(width);
        }

        /**
         * Creates a horizontal line of specified width using single-line characters.
         */
        default String singleLine(int width) {
            return horizontal().repeat(width);
        }
    }

    /**
     * Unicode symbol implementation.
     */
    private static class UnicodeSymbols implements Symbols {
        @Override public String pHat() { return P_HAT; }
        @Override public String pi() { return PI; }
        @Override public String h0() { return H0; }
        @Override public String h1() { return H1; }
        @Override public String leq() { return LEQ; }
        @Override public String geq() { return GEQ; }
        @Override public String neq() { return NEQ; }
        @Override public String sqrt() { return SQRT; }
        @Override public String approx() { return APPROX; }
        @Override public String alpha() { return ALPHA; }
        @Override public String times() { return TIMES; }
        @Override public String horizontalDouble() { return BOX_HORIZONTAL_DOUBLE; }
        @Override public String horizontal() { return BOX_HORIZONTAL; }
        @Override public String vertical() { return BOX_VERTICAL; }
    }

    /**
     * ASCII fallback symbol implementation.
     */
    private static class AsciiSymbols implements Symbols {
        @Override public String pHat() { return P_HAT_ASCII; }
        @Override public String pi() { return PI_ASCII; }
        @Override public String h0() { return H0_ASCII; }
        @Override public String h1() { return H1_ASCII; }
        @Override public String leq() { return LEQ_ASCII; }
        @Override public String geq() { return GEQ_ASCII; }
        @Override public String neq() { return NEQ_ASCII; }
        @Override public String sqrt() { return SQRT_ASCII; }
        @Override public String approx() { return APPROX_ASCII; }
        @Override public String alpha() { return ALPHA_ASCII; }
        @Override public String times() { return TIMES_ASCII; }
        @Override public String horizontalDouble() { return BOX_HORIZONTAL_DOUBLE_ASCII; }
        @Override public String horizontal() { return BOX_HORIZONTAL_ASCII; }
        @Override public String vertical() { return BOX_VERTICAL_ASCII; }
    }
}

