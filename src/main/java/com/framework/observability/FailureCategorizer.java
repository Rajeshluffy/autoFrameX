package com.framework.observability;

import java.net.ConnectException;
import java.net.SocketException;

import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;

import com.framework.selenium.exception.ElementNotFoundException;

/**
 * Stateless utility that maps a Throwable to a human-readable failure category.
 * Categories align with the observability schema's {@code failureCategory} field.
 */
public final class FailureCategorizer {

    private FailureCategorizer() {}

    /**
     * Categorizes the given throwable.
     *
     * @param t the exception from a failed test (may be null)
     * @return one of: ASSERTION, TIMEOUT, ELEMENT_NOT_FOUND, NETWORK, UNKNOWN
     */
    public static String categorize(Throwable t) {
        if (t == null)                          return "UNKNOWN";
        if (t instanceof AssertionError)        return "ASSERTION";
        if (t instanceof TimeoutException)      return "TIMEOUT";
        if (t instanceof ElementNotFoundException) return "ELEMENT_NOT_FOUND";
        if (t instanceof NoSuchElementException)   return "ELEMENT_NOT_FOUND";
        if (t instanceof SocketException
         || t instanceof ConnectException)      return "NETWORK";

        // Check cause chain for wrapped exceptions
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return categorize(cause);
        }
        return "UNKNOWN";
    }
}
