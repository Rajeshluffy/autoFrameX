package com.framework.observability;

import java.net.ConnectException;
import java.net.SocketException;

import com.framework.exception.Categorized;

/**
 * Stateless utility that maps a Throwable to a human-readable failure category.
 * Categories align with the observability schema's {@code failureCategory} field.
 *
 * <p>Deliberately has no compile-time dependency on Selenium or any other
 * test-technology library (TD-20's multi-module split — this class lives in
 * {@code autoframex-core}, which must stay usable by API-only/non-UI consumers).
 * Selenium-specific exceptions are matched by fully-qualified class name string
 * instead of {@code instanceof}, so they're still correctly categorized at
 * runtime (when {@code selenium-java} actually is on the classpath) without
 * {@code core} needing that dependency itself. Framework-thrown exceptions
 * that know their own category implement {@link Categorized} instead (see
 * {@code com.framework.selenium.exception.ElementNotFoundException}).
 */
public final class FailureCategorizer {

    private static final String SELENIUM_TIMEOUT_EXCEPTION = "org.openqa.selenium.TimeoutException";
    private static final String SELENIUM_NO_SUCH_ELEMENT_EXCEPTION = "org.openqa.selenium.NoSuchElementException";

    private FailureCategorizer() {}

    /**
     * Categorizes the given throwable.
     *
     * @param t the exception from a failed test (may be null)
     * @return one of: ASSERTION, TIMEOUT, ELEMENT_NOT_FOUND, NETWORK, UNKNOWN
     */
    public static String categorize(Throwable t) {
        if (t == null)                    return "UNKNOWN";
        if (t instanceof AssertionError)  return "ASSERTION";
        if (t instanceof Categorized categorized) return categorized.category();

        String className = t.getClass().getName();
        if (className.equals(SELENIUM_TIMEOUT_EXCEPTION))         return "TIMEOUT";
        if (className.equals(SELENIUM_NO_SUCH_ELEMENT_EXCEPTION)) return "ELEMENT_NOT_FOUND";

        if (t instanceof SocketException
         || t instanceof ConnectException) return "NETWORK";

        // Check cause chain for wrapped exceptions
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return categorize(cause);
        }
        return "UNKNOWN";
    }
}
