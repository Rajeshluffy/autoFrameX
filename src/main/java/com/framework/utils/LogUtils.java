package com.framework.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured, thread-safe logging utility for test step observability.
 *
 * <p>Wraps SLF4J with convenience methods for:
 * <ul>
 *   <li>Step-level INFO/WARN/ERROR logging with thread context</li>
 *   <li>Performance timers (start → stop → elapsed)</li>
 *   <li>Error context capture (exception + optional metadata)</li>
 * </ul>
 *
 * <p>Each log line is prefixed with {@code [ThreadId]} so parallel test output
 * remains traceable without a distributed tracing backend.
 */
public final class LogUtils {

    private static final Logger logger = LoggerFactory.getLogger("autoFrameX");

    /** Active performance timers keyed by {@code threadId:timerName}. */
    private static final ConcurrentHashMap<String, Long> timers = new ConcurrentHashMap<>();

    private LogUtils() {}

    // =========================================================================
    // STEP LOGGING
    // =========================================================================

    /** Logs a test step at INFO level. */
    public static void step(String message) {
        logger.info(prefix() + message);
    }

    /** Logs a warning. */
    public static void warn(String message) {
        logger.warn(prefix() + message);
    }

    /** Logs an error message. */
    public static void error(String message) {
        logger.error(prefix() + message);
    }

    /** Logs an error with its exception. */
    public static void error(String message, Throwable t) {
        logger.error(prefix() + message, t);
    }

    /** Logs a debug-level message. */
    public static void debug(String message) {
        logger.debug(prefix() + message);
    }

    // =========================================================================
    // PERFORMANCE TIMERS
    // =========================================================================

    /**
     * Starts a named performance timer for the current thread.
     *
     * @param timerName logical name (e.g. "LoginPage.load", "SearchAPI.call")
     */
    public static void startTimer(String timerName) {
        timers.put(timerKey(timerName), System.currentTimeMillis());
        logger.debug(prefix() + "Timer started: " + timerName);
    }

    /**
     * Stops the named timer and logs the elapsed time.
     *
     * @param timerName the name passed to {@link #startTimer}
     * @return elapsed milliseconds, or {@code -1} if the timer was never started
     */
    public static long stopTimer(String timerName) {
        Long start = timers.remove(timerKey(timerName));
        if (start == null) {
            logger.warn(prefix() + "Timer not found: " + timerName);
            return -1L;
        }
        long elapsed = System.currentTimeMillis() - start;
        logger.info(prefix() + "Timer [" + timerName + "] elapsed: " + elapsed + " ms");
        return elapsed;
    }

    /**
     * Stops the timer and asserts the elapsed time is within {@code maxMs}.
     * Logs a WARNING (does not throw) if the SLA is breached.
     *
     * @param timerName logical timer name
     * @param maxMs     maximum acceptable duration in milliseconds
     * @return elapsed milliseconds
     */
    public static long stopTimerWithSla(String timerName, long maxMs) {
        long elapsed = stopTimer(timerName);
        if (elapsed > maxMs) {
            logger.warn(prefix() + "SLA BREACH — [" + timerName + "] took " + elapsed
                    + " ms (limit: " + maxMs + " ms)");
        }
        return elapsed;
    }

    // =========================================================================
    // ERROR CONTEXT
    // =========================================================================

    /**
     * Logs a structured error context block — useful on test failure to capture
     * all relevant state in one log entry.
     *
     * @param testName  name of the failing test
     * @param throwable the exception that caused the failure
     * @param context   optional key-value pairs (e.g. URL, element description)
     */
    public static void logErrorContext(String testName, Throwable throwable,
            Map<String, String> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== ERROR CONTEXT ==========\n");
        sb.append("Test     : ").append(testName).append("\n");
        sb.append("Thread   : ").append(Thread.currentThread().getId()).append("\n");
        sb.append("Exception: ").append(throwable.getClass().getSimpleName())
          .append(" — ").append(throwable.getMessage()).append("\n");

        if (context != null && !context.isEmpty()) {
            sb.append("Context  :\n");
            context.forEach((k, v) -> sb.append("  ").append(k).append(" = ").append(v).append("\n"));
        }
        sb.append("===================================");
        logger.error(sb.toString());
    }

    /**
     * Convenience overload with no extra context map.
     */
    public static void logErrorContext(String testName, Throwable throwable) {
        logErrorContext(testName, throwable, null);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static String prefix() {
        return "[T-" + Thread.currentThread().getId() + "] ";
    }

    private static String timerKey(String name) {
        return Thread.currentThread().getId() + ":" + name;
    }
}
