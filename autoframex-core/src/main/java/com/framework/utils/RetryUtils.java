package com.framework.utils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Typed retry utility with exponential backoff and a lightweight circuit breaker.
 *
 * <h3>Usage</h3>
 * <pre>
 * // Retry a Supplier (returns a value)
 * String result = RetryUtils.retry(() -> apiClient.getUser(id), 3);
 *
 * // Retry a Runnable (fire-and-forget)
 * RetryUtils.retryVoid(() -> driver.click(locator), 3);
 *
 * // Circuit breaker — stops retrying after threshold consecutive failures
 * RetryUtils.CircuitBreaker cb = new RetryUtils.CircuitBreaker("LoginService", 5);
 * cb.execute(() -> loginPage.submit());
 * </pre>
 */
public final class RetryUtils {

    private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

    // Backoff schedule in ms: 500 → 1000 → 2000 → 4000 → 8000 (cap)
    private static final long[] BACKOFF_MS = {500, 1000, 2000, 4000, 8000};
    private static final long BACKOFF_CAP_MS = 8_000;

    private RetryUtils() {}

    // =========================================================================
    // SUPPLIER RETRY (returns a value)
    // =========================================================================

    /**
     * Retries {@code action} up to {@code maxAttempts} times using exponential
     * backoff. Returns the first successful result.
     *
     * @param <T>         return type of the action
     * @param action      the operation to retry
     * @param maxAttempts maximum number of attempts (must be &ge; 1)
     * @return result of the first successful invocation
     * @throws RetryExhaustedException if all attempts fail
     */
    public static <T> T retry(Supplier<T> action, int maxAttempts) {
        return retry(action, maxAttempts, null);
    }

    /**
     * Retries {@code action} up to {@code maxAttempts} times. Only retries when
     * the thrown exception is an instance of {@code retryOn} (or any exception
     * when {@code retryOn} is {@code null}).
     *
     * @param <T>         return type of the action
     * @param action      the operation to retry
     * @param maxAttempts maximum number of attempts
     * @param retryOn     exception type that triggers a retry; {@code null} = retry on any
     * @return result of the first successful invocation
     * @throws RetryExhaustedException if all attempts fail
     */
    public static <T> T retry(Supplier<T> action, int maxAttempts,
            Class<? extends Exception> retryOn) {
        if (action == null || maxAttempts < 1) throw new IllegalArgumentException("action must be non-null and maxAttempts >= 1");

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = action.get();
                if (attempt > 1) logger.info("Retry succeeded on attempt " + attempt);
                return result;
            } catch (Exception e) {
                if (retryOn != null && !retryOn.isInstance(e)) throw new RetryExhaustedException(
                        "Non-retryable exception on attempt " + attempt, e);
                lastException = e;
                logger.warn("Attempt " + attempt + "/" + maxAttempts + " failed: " + e.getMessage());
                if (attempt < maxAttempts) sleepBackoff(attempt);
            }
        }
        throw new RetryExhaustedException("All " + maxAttempts + " attempts failed", lastException);
    }

    // =========================================================================
    // RUNNABLE RETRY (fire-and-forget)
    // =========================================================================

    /**
     * Retries a {@link Runnable} up to {@code maxAttempts} times with exponential
     * backoff.
     *
     * @param action      the operation to retry
     * @param maxAttempts maximum number of attempts
     * @throws RetryExhaustedException if all attempts fail
     */
    public static void retryVoid(Runnable action, int maxAttempts) {
        retry(() -> { action.run(); return null; }, maxAttempts);
    }

    /**
     * Retries a {@link Runnable} and returns {@code true} on success, {@code false}
     * if all attempts fail (does not throw).
     *
     * @param action      the operation to retry
     * @param maxAttempts maximum number of attempts
     * @return {@code true} if any attempt succeeded
     */
    public static boolean tryRetry(Runnable action, int maxAttempts) {
        try {
            retryVoid(action, maxAttempts);
            return true;
        } catch (RetryExhaustedException e) {
            return false;
        }
    }

    // =========================================================================
    // CIRCUIT BREAKER
    // =========================================================================

    /**
     * Lightweight circuit breaker with three states: CLOSED → OPEN → HALF_OPEN.
     *
     * <ul>
     *   <li><b>CLOSED</b> — normal operation; failures are counted.</li>
     *   <li><b>OPEN</b> — calls are rejected immediately after {@code failureThreshold}
     *       consecutive failures. Transitions to HALF_OPEN after {@code resetTimeoutMs}.</li>
     *   <li><b>HALF_OPEN</b> — one trial call is allowed; success closes the breaker,
     *       failure re-opens it.</li>
     * </ul>
     */
    public static final class CircuitBreaker {

        public enum State { CLOSED, OPEN, HALF_OPEN }

        private final String name;
        private final int failureThreshold;
        private final long resetTimeoutMs;

        private volatile State state = State.CLOSED;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile long openedAt = 0L;

        private static final Logger cbLogger = LoggerFactory.getLogger(CircuitBreaker.class);

        /**
         * @param name             logical name for logging
         * @param failureThreshold consecutive failures before opening the circuit
         */
        public CircuitBreaker(String name, int failureThreshold) {
            this(name, failureThreshold, 30_000L);
        }

        /**
         * @param name             logical name for logging
         * @param failureThreshold consecutive failures before opening the circuit
         * @param resetTimeoutMs   milliseconds to wait before attempting a trial call
         */
        public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMs = resetTimeoutMs;
        }

        /**
         * Executes {@code action} through the circuit breaker.
         *
         * @param action the operation to execute
         * @throws CircuitOpenException if the circuit is OPEN and the reset timeout
         *                              has not elapsed
         */
        public void execute(Runnable action) {
            execute(() -> { action.run(); return null; });
        }

        /**
         * Executes {@code action} through the circuit breaker and returns its result.
         *
         * @param <T>    return type
         * @param action the operation to execute
         * @return result of the action
         * @throws CircuitOpenException if the circuit is OPEN
         */
        public <T> T execute(Supplier<T> action) {
            checkState();
            try {
                T result = action.get();
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                throw e;
            }
        }

        public State getState() { return state; }
        public int getFailureCount() { return consecutiveFailures.get(); }

        private void checkState() {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - openedAt >= resetTimeoutMs) {
                    state = State.HALF_OPEN;
                    cbLogger.info("CircuitBreaker [" + name + "] → HALF_OPEN (trial call allowed)");
                } else {
                    throw new CircuitOpenException("CircuitBreaker [" + name + "] is OPEN — call rejected");
                }
            }
        }

        private void onSuccess() {
            consecutiveFailures.set(0);
            if (state != State.CLOSED) {
                state = State.CLOSED;
                cbLogger.info("CircuitBreaker [" + name + "] → CLOSED");
            }
        }

        private void onFailure() {
            int failures = consecutiveFailures.incrementAndGet();
            cbLogger.warn("CircuitBreaker [" + name + "] failure " + failures + "/" + failureThreshold);
            if (failures >= failureThreshold && state != State.OPEN) {
                state = State.OPEN;
                openedAt = System.currentTimeMillis();
                cbLogger.error("CircuitBreaker [" + name + "] → OPEN after " + failures + " consecutive failures");
            }
        }
    }

    // =========================================================================
    // EXCEPTIONS
    // =========================================================================

    /** Thrown when all retry attempts are exhausted. */
    public static class RetryExhaustedException extends com.framework.exception.FrameworkException {
        public RetryExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thrown when a call is rejected because the circuit breaker is OPEN. */
    public static class CircuitOpenException extends com.framework.exception.FrameworkException {
        public CircuitOpenException(String message) {
            super(message);
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static void sleepBackoff(int attempt) {
        long delay = attempt <= BACKOFF_MS.length ? BACKOFF_MS[attempt - 1] : BACKOFF_CAP_MS;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
