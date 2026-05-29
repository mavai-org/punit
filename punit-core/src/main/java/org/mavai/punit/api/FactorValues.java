package org.mavai.punit.api;

import java.util.List;

/**
 * Provides named access to factor values in EXPLORE mode experiments.
 *
 * <p>Used in two contexts:
 * <ol>
 *   <li>As a parameter in experiment methods for accessing factor values</li>
 *   <li>In {@code ServiceContractProvider.registerWithFactors()} callbacks for configuring service contracts</li>
 * </ol>
 *
 * <h2>In Experiment Methods</h2>
 * <pre>{@code
 * @FactorSource("configs")
 * void exploreConfigs(ShoppingServiceContract serviceContract, FactorValues factors) {
 *     String query = factors.getString("query");
 *     serviceContract.searchProducts(query);
 * }
 * }</pre>
 *
 * <h2>In Service Contract Factory</h2>
 * <pre>{@code
 * provider.registerWithFactors(ShoppingServiceContract.class, factors -> {
 *     String model = factors.getString("model");
 *     double temp = factors.getDouble("temp");
 *     return new ShoppingServiceContract(createAssistant(model, temp), model, temp);
 * });
 * }</pre>
 *
 * @see FactorSource
 */
public record FactorValues(Object[] values, List<String> names) {

    /**
     * Creates a new FactorValues instance.
     *
     * @param values the factor values
     * @param names the factor names (same order as values)
     */
    public FactorValues {
    }

    /**
     * Gets a factor value by name.
     *
     * @param name the factor name
     * @return the factor value
     * @throws IllegalArgumentException if the factor name doesn't exist
     */
    public Object get(String name) {
        int index = names.indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "Unknown factor: '" + name + "'. Available factors: " + names);
        }
        return values[index];
    }

    /**
     * Gets a factor value as a String.
     *
     * @param name the factor name
     * @return the factor value as String
     * @throws IllegalArgumentException if the factor name doesn't exist
     */
    public String getString(String name) {
        Object value = get(name);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a factor value as a double.
     *
     * @param name the factor name
     * @return the factor value as double
     * @throws IllegalArgumentException if the factor name doesn't exist
     * @throws NumberFormatException if the value cannot be converted to double
     */
    public double getDouble(String name) {
        Object value = get(name);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "Factor '" + name + "' is null, cannot convert to double");
        }
        return Double.parseDouble(value.toString());
    }

    /**
     * Gets a factor value as an int.
     *
     * @param name the factor name
     * @return the factor value as int
     * @throws IllegalArgumentException if the factor name doesn't exist
     * @throws NumberFormatException if the value cannot be converted to int
     */
    public int getInt(String name) {
        Object value = get(name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "Factor '" + name + "' is null, cannot convert to int");
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * Gets a factor value by index.
     *
     * @param index the factor index
     * @return the factor value
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public Object get(int index) {
        return values[index];
    }

    /**
     * Checks if a factor with the given name exists.
     *
     * @param name the factor name
     * @return true if the factor exists
     */
    public boolean has(String name) {
        return names.contains(name);
    }

    /**
     * Returns the number of factors.
     */
    public int size() {
        return values.length;
    }

    /**
     * Returns all factor names.
     */
    @Override
    public List<String> names() {
        return names;
    }

    /**
     * Returns all factor values.
     */
    @Override
    public Object[] values() {
        return values.clone();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FactorValues{");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i)).append("=").append(values[i]);
        }
        return sb.append("}").toString();
    }
}

