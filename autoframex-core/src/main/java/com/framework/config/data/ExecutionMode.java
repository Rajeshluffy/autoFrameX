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
     * Case-insensitive; a hyphen or space is read as an underscore ({@code "data-provider"} is
     * {@link #DATA_PROVIDER}). A {@code null} or blank value means "not configured" and gives {@link #TARGETED}.
     *
     * <p>Any other value is an error. It used to fall back to {@link #TARGETED} too, so a typo such as
     * {@code "dataprovider"} ran one account instead of every row of the data file, and the build stayed green.
     *
     * @param value raw string (e.g. {@code "data_provider"}, {@code "DATA_PROVIDER"})
     * @return matching enum constant, {@code TARGETED} when {@code value} is null or blank
     * @throws com.framework.exception.FrameworkException if {@code value} names no mode
     */
    public static ExecutionMode from(String value) {
        if (value == null || value.trim().isEmpty()) return TARGETED;
        String normalised = value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new com.framework.exception.FrameworkException("Unknown execution.mode '" + value.trim()
                    + "'. Use one of: " + java.util.Arrays.toString(values())
                    + " (case-insensitive). Leave it unset for TARGETED.");
        }
    }
}
