package com.framework.config.data;

/**
 * Controls how test data is sourced during a suite run.
 *
 * <p>Set via the TestNG XML parameter {@code execution.mode}:
 * <pre>
 *   &lt;parameter name="execution.mode" value="data_provider"/&gt;
 *   &lt;parameter name="execution.mode" value="targeted"/&gt;
 * </pre>
 *
 * <ul>
 *   <li>{@link #DATA_PROVIDER} — the {@code fetchData} DataProvider reads every
 *       account row from the configured Excel file. All test classes in the XML
 *       execute once per account.</li>
 *   <li>{@link #TARGETED} — a single account is resolved from TestNG XML
 *       parameters ({@code accountId}, {@code username}, {@code password},
 *       {@code url}). Intended for smoke tests, debugging, and environment-specific
 *       validation where only one account should run.</li>
 * </ul>
 */
public enum ExecutionMode {

    DATA_PROVIDER,
    TARGETED;

    /**
     * Parses a string value from the TestNG XML or properties file.
     * Case-insensitive; treats {@code null} / unknown values as {@link #TARGETED}.
     *
     * @param value raw string (e.g. {@code "data_provider"}, {@code "DATA_PROVIDER"})
     * @return matching enum constant, defaulting to {@code TARGETED}
     */
    public static ExecutionMode from(String value) {
        if (value == null || value.trim().isEmpty()) return TARGETED;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TARGETED;
        }
    }
}
