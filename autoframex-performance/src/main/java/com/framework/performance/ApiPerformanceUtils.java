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
     * Delegates to {@link ValidationUtils#assertResponseTime} — logs a WARNING if the
     * SLA is breached but does not throw, so a slow response never masks a functional failure.
     *
     * @return {@code true} if within SLA, {@code false} if breached
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
     * <p>Latencies are collected in a thread-safe list and sorted for P95/P99 computation.
     * Success and failure counts are tracked with {@link AtomicInteger}.
     *
     * @param apiCall         the API operation to load-test (must be thread-safe)
     * @param threads         number of concurrent workers
     * @param durationSeconds how long to run the test
     * @return aggregated {@link LoadTestResult}
     */
    public static LoadTestResult runLoadTest(
            Supplier<ResponseAPI> apiCall, int threads, int durationSeconds) {

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long deadline = System.currentTimeMillis() + (long) durationSeconds * 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // Reporter's ExtentTest node is a plain ThreadLocal — these worker
        // threads never inherit it, so automatic per-call API timing
        // (RestAssuredListener -> Reporter.reportApiStep) would silently no-op
        // for every call made here otherwise. Capture the calling thread's node
        // once and bind it into each worker for the duration of its loop.
        ExtentTest reportNode = Reporter.captureCurrentNode();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> Reporter.runWithNode(reportNode, () -> {
                while (System.currentTimeMillis() < deadline) {
                    long start = System.nanoTime();
                    try {
                        ResponseAPI response = apiCall.get();
                        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                        latencies.add(ms);
                        if (response != null && response.getStatusCode() < 400) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                        latencies.add(ms);
                        failureCount.incrementAndGet();
                    }
                }
            }));
        }

        pool.shutdown();
        try {
            pool.awaitTermination(durationSeconds + 30L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Load test interrupted before completion");
        }

        return buildResult(latencies, successCount.get(), failureCount.get());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static LoadTestResult buildResult(
            List<Long> latencies, int success, int failure) {

        if (latencies.isEmpty()) {
            return new LoadTestResult(success + failure, success, failure, 0, 0, 0.0, 0, 0);
        }

        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95 = sorted.get(Math.max(0, (int) Math.ceil(0.95 * sorted.size()) - 1));
        long p99 = sorted.get(Math.max(0, (int) Math.ceil(0.99 * sorted.size()) - 1));

        return new LoadTestResult(success + failure, success, failure, min, max, avg, p95, p99);
    }

    // =========================================================================
    // RESULT VALUE OBJECT
    // =========================================================================

    /** Aggregated statistics from a {@link #runLoadTest} execution. */
    public static final class LoadTestResult {

        private final int    totalRequests;
        private final int    successCount;
        private final int    failureCount;
        private final long   minMs;
        private final long   maxMs;
        private final double avgMs;
        private final long   p95Ms;
        private final long   p99Ms;

        LoadTestResult(int total, int success, int failure,
                       long min, long max, double avg, long p95, long p99) {
            this.totalRequests = total;
            this.successCount  = success;
            this.failureCount  = failure;
            this.minMs         = min;
            this.maxMs         = max;
            this.avgMs         = avg;
            this.p95Ms         = p95;
            this.p99Ms         = p99;
        }

        public int    getTotalRequests() { return totalRequests; }
        public int    getSuccessCount()  { return successCount; }
        public int    getFailureCount()  { return failureCount; }
        public long   getMinMs()         { return minMs; }
        public long   getMaxMs()         { return maxMs; }
        public double getAvgMs()         { return avgMs; }
        public long   getP95Ms()         { return p95Ms; }
        public long   getP99Ms()         { return p99Ms; }

        @Override
        public String toString() {
            return String.format(
                "LoadTestResult{total=%d, success=%d, failure=%d, " +
                "min=%dms, avg=%.1fms, max=%dms, p95=%dms, p99=%dms}",
                totalRequests, successCount, failureCount,
                minMs, avgMs, maxMs, p95Ms, p99Ms);
        }
    }
}
