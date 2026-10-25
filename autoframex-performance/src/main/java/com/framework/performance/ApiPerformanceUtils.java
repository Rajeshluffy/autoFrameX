package com.framework.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aventstack.extentreports.ExtentTest;
import com.api.design.ResponseAPI;
import com.framework.utils.Reporter;
import com.framework.utils.ValidationUtils;

/**
 * Stateless utility for API response time measurement and concurrent load testing.
 * Uses only Java stdlib concurrency and RestAssured (already in pom.xml) — no JMeter/Gatling.
 */
public final class ApiPerformanceUtils {

    private static final Logger logger = LoggerFactory.getLogger(ApiPerformanceUtils.class);

    /** How long after the deadline workers may take to finish their last call before they are interrupted. */
    static final long DEFAULT_GRACE_SECONDS = 30;

    private ApiPerformanceUtils() {}

    // =========================================================================
    // SINGLE-CALL MEASUREMENT
    // =========================================================================

    /**
     * Executes {@code apiCall} once and returns the wall-clock duration in milliseconds.
     * Uses {@code System.nanoTime()} for sub-millisecond precision.
     * Any exception thrown by the supplier is re-thrown after the duration is logged.
     */
    public static long measureResponseTime(Supplier<ResponseAPI> apiCall) {
        long startNs = System.nanoTime();
        try {
            apiCall.get();
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            logger.debug("API call completed in {} ms", elapsedMs);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }

    /**
     * Delegates to {@link ValidationUtils#assertResponseTime}: an SLA breach throws an {@link AssertionError}
     * (set {@code -Dperformance.sla.enforce=false} for a logged warning instead), and a negative value,
     * meaning nothing was measured, always throws.
     *
     * @return {@code true} if within SLA; {@code false} if breached while enforcement is off
     */
    public static boolean assertResponseTime(long actualMs, long maxMs, String operation) {
        return ValidationUtils.assertResponseTime(actualMs, maxMs, operation);
    }

    // =========================================================================
    // CONCURRENT LOAD TEST
    // =========================================================================

    /**
     * Runs a concurrent load test by submitting {@code threads} workers that each
     * loop until {@code durationSeconds} elapses, calling {@code apiCall} on every iteration.
     *
     * <p>A call counts as a success when it returns a response with a status below 400. The latency
     * statistics (min, average, max, P95, P99) describe the successful calls only: a call that failed fast, such
     * as a refused connection answered in a millisecond, used to be counted as a latency, so a service that was
     * down produced a flattering P95. Failures are counted separately ({@link LoadTestResult#getFailureRate()}).
     *
     * <p>If the workers have not finished {@value #DEFAULT_GRACE_SECONDS} seconds after the deadline they are
     * interrupted, and the result reports {@link LoadTestResult#isComplete()} as {@code false}.
     *
     * @param apiCall         the API operation to load-test (must be thread-safe)
     * @param threads         number of concurrent workers
     * @param durationSeconds how long to run the test
     * @return aggregated {@link LoadTestResult}
     */
    public static LoadTestResult runLoadTest(
            Supplier<ResponseAPI> apiCall, int threads, int durationSeconds) {
        return runLoadTest(apiCall, threads, durationSeconds, DEFAULT_GRACE_SECONDS);
    }

    /** As {@link #runLoadTest(Supplier, int, int)}, with the post-deadline grace period given in seconds. */
    static LoadTestResult runLoadTest(
            Supplier<ResponseAPI> apiCall, int threads, int durationSeconds, long graceSeconds) {
        List<Long> successLatencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failureCount = new AtomicInteger(0);

        long startNs = System.nanoTime();
        long deadlineNs = startNs + TimeUnit.SECONDS.toNanos(Math.max(0, durationSeconds));
        // Daemon workers: a call that never returns must not keep the JVM alive after the test run.
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads), runnable -> {
            Thread worker = new Thread(runnable, "load-test-worker");
            worker.setDaemon(true);
            return worker;
        });

        // Reporter's ExtentTest node is a plain ThreadLocal — these worker
        // threads never inherit it, so automatic per-call API timing
        // (RestAssuredListener -> Reporter.reportApiStep) would silently no-op
        // for every call made here otherwise. Capture the calling thread's node
        // once and bind it into each worker for the duration of its loop.
        ExtentTest reportNode = Reporter.captureCurrentNode();

        for (int i = 0; i < Math.max(1, threads); i++) {
            pool.submit(() -> Reporter.runWithNode(reportNode, () -> {
                while (System.nanoTime() - deadlineNs < 0) {
                    long start = System.nanoTime();
                    try {
                        ResponseAPI response = apiCall.get();
                        long ms = elapsedMsRoundedUp(start);
                        if (response != null && response.getStatusCode() < 400) {
                            successLatencies.add(ms);
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                }
            }));
        }

        pool.shutdown();
        boolean complete = true;
        try {
            if (!pool.awaitTermination(Math.max(0, durationSeconds) + graceSeconds, TimeUnit.SECONDS)) {
                complete = false;
                pool.shutdownNow();
                logger.warn("Load test workers still running {}s after the deadline; interrupted them. "
                        + "The figures cover only the calls that finished.", graceSeconds);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            complete = false;
            pool.shutdownNow();
            logger.warn("Load test interrupted before completion");
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        return buildResult(successLatencies, failureCount.get(), elapsedMs, complete);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Milliseconds since {@code startNs}, rounded up, so a sub-millisecond call is 1 ms and not 0. */
    private static long elapsedMsRoundedUp(long startNs) {
        return (System.nanoTime() - startNs + 999_999L) / 1_000_000L;
    }

    private static LoadTestResult buildResult(
            List<Long> successLatencies, int failure, long elapsedMs, boolean complete) {
        List<Long> sorted;
        synchronized (successLatencies) {
            sorted = new ArrayList<>(successLatencies);
        }
        int success = sorted.size();
        if (sorted.isEmpty()) {
            // -1, not 0: "no successful call to measure". Zero would pass any SLA.
            return new LoadTestResult(success + failure, success, failure, -1, -1, -1.0, -1, -1, elapsedMs, complete);
        }
        Collections.sort(sorted);
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95 = sorted.get(Math.max(0, (int) Math.ceil(0.95 * sorted.size()) - 1));
        long p99 = sorted.get(Math.max(0, (int) Math.ceil(0.99 * sorted.size()) - 1));
        return new LoadTestResult(success + failure, success, failure, min, max, avg, p95, p99, elapsedMs, complete);
    }

    // =========================================================================
    // RESULT VALUE OBJECT
    // =========================================================================

    /**
     * Aggregated statistics from a {@link #runLoadTest} execution. The latency figures describe successful calls
     * only and are {@code -1} when there was no successful call; {@link ValidationUtils#assertResponseTime}
     * rejects a negative figure, so "nothing measured" can never pass an SLA.
     */
    public static final class LoadTestResult {
        private final int    totalRequests;
        private final int    successCount;
        private final int    failureCount;
        private final long   minMs;
        private final long   maxMs;
        private final double avgMs;
        private final long   p95Ms;
        private final long   p99Ms;
        private final long   elapsedMs;
        private final boolean complete;

        LoadTestResult(int total, int success, int failure,
                       long min, long max, double avg, long p95, long p99, long elapsedMs, boolean complete) {
            this.totalRequests = total;
            this.successCount  = success;
            this.failureCount  = failure;
            this.minMs         = min;
            this.maxMs         = max;
            this.avgMs         = avg;
            this.p95Ms         = p95;
            this.p99Ms         = p99;
            this.elapsedMs     = elapsedMs;
            this.complete      = complete;
        }

        public int    getTotalRequests() { return totalRequests; }
        public int    getSuccessCount()  { return successCount; }
        public int    getFailureCount()  { return failureCount; }
        public long   getMinMs()         { return minMs; }
        public long   getMaxMs()         { return maxMs; }
        public double getAvgMs()         { return avgMs; }
        public long   getP95Ms()         { return p95Ms; }
        public long   getP99Ms()         { return p99Ms; }
        public long   getElapsedMs()     { return elapsedMs; }

        /** Share of calls that failed, from 0.0 to 1.0; 0.0 when no call was made. */
        public double getFailureRate() {
            return totalRequests == 0 ? 0.0 : (double) failureCount / totalRequests;
        }

        /** Successful calls per second over the whole run. */
        public double getThroughputPerSecond() {
            return elapsedMs <= 0 ? 0.0 : successCount * 1000.0 / elapsedMs;
        }

        /** {@code false} if workers had to be interrupted: the figures then cover only the calls that finished. */
        public boolean isComplete() {
            return complete;
        }

        @Override
        public String toString() {
            return String.format(
                "LoadTestResult{total=%d, success=%d, failure=%d (%.1f%%), "
                + "min=%dms, avg=%.1fms, max=%dms, p95=%dms, p99=%dms, %.1f ok/s, complete=%s}",
                totalRequests, successCount, failureCount, getFailureRate() * 100,
                minMs, avgMs, maxMs, p95Ms, p99Ms, getThroughputPerSecond(), complete);
        }
    }
}
