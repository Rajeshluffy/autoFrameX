package com.framework.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.WebElement;
import org.testng.Assert;

/**
 * Centralised assertion and validation helpers for UI, API, and data checks.
 *
 * <h3>Hard vs Soft assertions</h3>
 * <ul>
 *   <li><b>Hard</b> — {@code assertXxx()} methods throw immediately on failure
 *       (delegates to TestNG {@link Assert}).</li>
 *   <li><b>Soft</b> — {@link SoftAssert} collects all failures and throws a
 *       single aggregated error when {@link SoftAssert#assertAll()} is called.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * All static helpers are stateless. {@link SoftAssert} instances are per-test
 * objects and must not be shared across threads.
 */
public final class ValidationUtils {

    private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);

    private ValidationUtils() {}

    // =========================================================================
    // TEXT ASSERTIONS
    // =========================================================================

    /**
     * Asserts that {@code actual} equals {@code expected} (case-sensitive).
     * Logs the comparison before asserting.
     */
    public static void assertTextEquals(String actual, String expected, String message) {
        logger.info("assertTextEquals — expected: [" + expected + "] actual: [" + actual + "]");
        Assert.assertEquals(actual, expected, message);
    }

    /**
     * Asserts that {@code actual} contains {@code substring} (case-sensitive).
     */
    public static void assertTextContains(String actual, String substring, String message) {
        logger.info("assertTextContains — looking for [" + substring + "] in [" + actual + "]");
        Assert.assertTrue(actual != null && actual.contains(substring),
                message + " | expected to contain: [" + substring + "] but was: [" + actual + "]");
    }

    /**
     * Asserts that {@code actual} matches the given regular expression.
     */
    public static void assertTextMatches(String actual, String regex, String message) {
        logger.info("assertTextMatches — pattern: [" + regex + "] actual: [" + actual + "]");
        Assert.assertTrue(actual != null && actual.matches(regex),
                message + " | [" + actual + "] did not match pattern [" + regex + "]");
    }

    // =========================================================================
    // ELEMENT STATE ASSERTIONS
    // =========================================================================

    /**
     * Asserts that {@code element} is displayed on the page.
     */
    public static void assertElementVisible(WebElement element, String message) {
        logger.info("assertElementVisible — " + message);
        Assert.assertTrue(element != null && element.isDisplayed(),
                message + " | element is not visible");
    }

    /**
     * Asserts that {@code element} is enabled (not disabled).
     */
    public static void assertElementEnabled(WebElement element, String message) {
        logger.info("assertElementEnabled — " + message);
        Assert.assertTrue(element != null && element.isEnabled(),
                message + " | element is not enabled");
    }

    /**
     * Asserts that {@code element} is selected (checkbox / radio).
     */
    public static void assertElementSelected(WebElement element, String message) {
        logger.info("assertElementSelected — " + message);
        Assert.assertTrue(element != null && element.isSelected(),
                message + " | element is not selected");
    }

    // =========================================================================
    // HTTP / API ASSERTIONS
    // =========================================================================

    /**
     * Asserts that {@code actualCode} equals {@code expectedCode}.
     */
    public static void assertResponseCode(int actualCode, int expectedCode, String message) {
        logger.info("assertResponseCode — expected: " + expectedCode + " actual: " + actualCode);
        Assert.assertEquals(actualCode, expectedCode,
                message + " | HTTP status mismatch");
    }

    /**
     * Asserts that {@code actualCode} is in the 2xx success range.
     */
    public static void assertSuccess(int actualCode, String message) {
        logger.info("assertSuccess — status: " + actualCode);
        Assert.assertTrue(actualCode >= 200 && actualCode < 300,
                message + " | expected 2xx but got " + actualCode);
    }

    // =========================================================================
    // PERFORMANCE / SLA ASSERTIONS
    // =========================================================================

    /**
     * Asserts that {@code actualMs} is within the SLA limit {@code maxMs}.
     * Does not throw — logs a WARNING instead, so a slow response never masks
     * a functional failure.
     *
     * @param actualMs  measured duration in milliseconds
     * @param maxMs     SLA threshold in milliseconds
     * @param operation human-readable operation name for the log message
     * @return {@code true} if within SLA, {@code false} if breached
     */
    public static boolean assertResponseTime(long actualMs, long maxMs, String operation) {
        boolean withinSla = actualMs <= maxMs;
        if (withinSla) {
            logger.info("SLA OK — [" + operation + "] " + actualMs + " ms (limit: " + maxMs + " ms)");
        } else {
            logger.warn("SLA BREACH — [" + operation + "] " + actualMs + " ms exceeded limit of " + maxMs + " ms");
        }
        return withinSla;
    }

    // =========================================================================
    // BOOLEAN / NULL ASSERTIONS
    // =========================================================================

    public static void assertTrue(boolean condition, String message) {
        logger.info("assertTrue — " + message);
        Assert.assertTrue(condition, message);
    }

    public static void assertFalse(boolean condition, String message) {
        logger.info("assertFalse — " + message);
        Assert.assertFalse(condition, message);
    }

    public static void assertNotNull(Object object, String message) {
        logger.info("assertNotNull — " + message);
        Assert.assertNotNull(object, message);
    }

    public static void assertNull(Object object, String message) {
        logger.info("assertNull — " + message);
        Assert.assertNull(object, message);
    }

    // =========================================================================
    // SOFT ASSERTION
    // =========================================================================

    /**
     * Per-test soft assertion collector. Accumulates failures and throws a
     * single aggregated {@link AssertionError} when {@link #assertAll()} is called.
     *
     * <p>Create one instance per test method and call {@code assertAll()} at the end:
     * <pre>
     * SoftAssert soft = new ValidationUtils.SoftAssert();
     * soft.assertEquals(actual, expected, "field check");
     * soft.assertTrue(flag, "flag check");
     * soft.assertAll(); // throws if any check failed
     * </pre>
     */
    public static final class SoftAssert {

        private final List<String> failures = new ArrayList<>();

        public void assertEquals(Object actual, Object expected, String message) {
            if (!objectsEqual(actual, expected)) {
                record(message + " | expected: [" + expected + "] but was: [" + actual + "]");
            }
        }

        public void assertTrue(boolean condition, String message) {
            if (!condition) record(message + " | expected true but was false");
        }

        public void assertFalse(boolean condition, String message) {
            if (condition) record(message + " | expected false but was true");
        }

        public void assertNotNull(Object object, String message) {
            if (object == null) record(message + " | expected non-null but was null");
        }

        public void assertNull(Object object, String message) {
            if (object != null) record(message + " | expected null but was: [" + object + "]");
        }

        public void assertTextContains(String actual, String substring, String message) {
            if (actual == null || !actual.contains(substring)) {
                record(message + " | [" + actual + "] does not contain [" + substring + "]");
            }
        }

        public void assertResponseCode(int actual, int expected, String message) {
            if (actual != expected) {
                record(message + " | expected HTTP " + expected + " but got " + actual);
            }
        }

        /** Returns an unmodifiable view of all recorded failures so far. */
        public List<String> getFailures() {
            return Collections.unmodifiableList(failures);
        }

        /** Returns {@code true} if no failures have been recorded. */
        public boolean isPassing() {
            return failures.isEmpty();
        }

        /**
         * Throws an {@link AssertionError} listing all failures if any were recorded.
         * Clears the failure list after throwing so the instance can be reused.
         */
        public void assertAll() {
            if (failures.isEmpty()) return;
            StringBuilder sb = new StringBuilder("Soft assertion failures (" + failures.size() + "):\n");
            for (int i = 0; i < failures.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(failures.get(i)).append("\n");
            }
            failures.clear();
            throw new AssertionError(sb.toString());
        }

        private void record(String message) {
            logger.warn("SoftAssert FAIL — " + message);
            failures.add(message);
        }

        private static boolean objectsEqual(Object a, Object b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            return a.equals(b);
        }
    }
}
