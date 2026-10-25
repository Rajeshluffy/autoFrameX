package com.framework.performance;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.api.design.ResponseAPI;
import com.framework.observability.ResourceUsageAccumulator;
import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.FakerDataFactory;
import com.framework.utils.LogUtils;
import com.framework.utils.ValidationUtils;

/**
 * Abstract base class for performance-focused test classes.
 *
 * <h3>What this provides</h3>
 * <ul>
 *   <li>Skips WebDriver acquisition — performance tests call APIs directly.</li>
 *   <li>{@link #measureApi} — times a single API call and checks an SLA threshold.</li>
 *   <li>{@link #measurePageLoad} — reads Navigation Timing from a live browser page.</li>
 *   <li>{@link #runLoadTest} — concurrent load test with P95/P99 statistics.</li>
 *   <li>An SLA breach <b>fails the test</b> (an {@link AssertionError}); {@code -Dperformance.sla.enforce=false}
 *       makes it a logged warning instead. A call that answers with an HTTP error status, a page whose timing is
 *       unavailable, and a load test with no successful calls are never counted as meeting an SLA.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * {@literal @}TestMetadata(name = "Incident API Perf", category = "performance")
 * public class TC001_IncidentApiPerf extends PerformanceTestBase {
 *
 *     {@literal @}Test(groups = "performance")
 *     public void createIncidentSla() {
 *         long ms = measureApi("createIncident",
 *                 () -> incidentService.createIncidentRecord(payload), 2_000);
 *         reportStep("Create incident: " + ms + " ms", "info", false);
 *     }
 *
 *     {@literal @}Test(groups = "performance")
 *     public void loadTest() {
 *         ApiPerformanceUtils.LoadTestResult result =
 *                 runLoadTest(() -> incidentService.fetchIncidentRecords(), 10, 30);
 *         reportStep(result.toString(), "info", false);
 *         assertSla(result.getP95Ms(), 3_000, "P95 createIncident");
 *     }
 * }
 * </pre>
 *
 * Subclasses annotate test methods with {@code @Test(groups = "performance")}.
 */
public abstract class PerformanceTestBase extends ProjectSpecificMethods {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestBase.class);

    // =========================================================================
    // METHOD LIFECYCLE — skip WebDriver
    // =========================================================================

    @Override
    @BeforeMethod(alwaysRun = true)
    public void preCondition(Method method, ITestContext context, Object[] parameters) {
        logger.info("PerformanceTestBase: skipping driver setup for {}", method.getName());
    }

    @Override
    @AfterMethod(alwaysRun = true)
    public void postCondition(Method method, ITestResult result) {
        FakerDataFactory.cleanup();
        // Reporter.tearDownTest runs next as an inherited @AfterMethod; calling it here as well
        // published every test twice.
    }

    // =========================================================================
    // API PERFORMANCE HELPERS
    // =========================================================================

    /**
     * Executes {@code call} once, measures wall-clock duration, checks the SLA,
     * and records metrics into {@link ResourceUsageAccumulator} for the observability
     * NDJSON output.
     *
     * <p>The call must answer with a successful status (below 400). An error answered in 5 ms is not a fast
     * response, it is a failed call, and it used to meet any SLA. An SLA breach fails the test (see the class
     * documentation for the switch).
     *
     * @param operationName human-readable label for logging and the event payload
     * @param call          the API operation to measure
     * @param slaMs         SLA threshold in milliseconds
     * @return elapsed duration in milliseconds
     * @throws AssertionError if the call returned an HTTP error status, or the SLA was breached
     */
    protected long measureApi(String operationName, Supplier<ResponseAPI> call, long slaMs) {
        LogUtils.startTimer(operationName);
        ResponseAPI response = call.get();
        long elapsed = LogUtils.stopTimerWithSla(operationName, slaMs);
        boolean breached = elapsed > slaMs;

        ResourceUsageAccumulator.put("operationName", operationName);
        ResourceUsageAccumulator.put("durationMs",    elapsed);
        ResourceUsageAccumulator.put("slaMs",         slaMs);
        ResourceUsageAccumulator.put("slaBreached",   breached);

        if (response == null || response.getStatusCode() >= 400) {
            throw new AssertionError("[" + operationName + "] was not measured: the call "
                    + (response == null ? "returned no response" : "answered HTTP " + response.getStatusCode())
                    + " after " + elapsed + " ms, and an error response says nothing about the speed of the real operation");
        }

        ValidationUtils.assertResponseTime(elapsed, slaMs, operationName);
        return elapsed;
    }

    /**
     * Runs a concurrent load test — {@code threads} workers each loop for
     * {@code durationSeconds}, calling {@code call} on every iteration.
     *
     * @param call            the API operation to load-test (must be thread-safe)
     * @param threads         number of concurrent workers
     * @param durationSeconds how long to run the test
     * @return aggregated {@link ApiPerformanceUtils.LoadTestResult} with P95/P99
     */
    protected ApiPerformanceUtils.LoadTestResult runLoadTest(
            Supplier<ResponseAPI> call, int threads, int durationSeconds) {
        ApiPerformanceUtils.LoadTestResult result =
                ApiPerformanceUtils.runLoadTest(call, threads, durationSeconds);
        logger.info("Load test complete: {}", result);
        return result;
    }

    // =========================================================================
    // PAGE PERFORMANCE HELPERS
    // =========================================================================

    /**
     * Reads the full page load time from the browser's Navigation Timing API
     * and checks it against {@code slaMs}.
     *
     * <p>Requires a live WebDriver with a fully loaded page — call this after
     * navigating to the page under test.
     *
     * @param pageName human-readable page label for the log message
     * @param slaMs    SLA threshold in milliseconds
     * @return actual page load time in milliseconds
     */
    protected long measurePageLoad(String pageName, long slaMs) {
        long actual = PagePerformanceUtils.getPageLoadTimeMs(getDriver());
        if (actual <= 0) {
            throw new AssertionError("[" + pageName + " page load] was not measured: the browser reports no load "
                    + "time (loadEventEnd is 0, the page has not finished loading), which would otherwise "
                    + "count as 0 ms and meet any SLA");
        }
        ValidationUtils.assertResponseTime(actual, slaMs, pageName + " page load");
        return actual;
    }

    /**
     * Returns all Navigation Timing metrics for the current page.
     * Keys: {@code domContentLoaded}, {@code loadEventEnd}, {@code domInteractive},
     * {@code responseEnd}, {@code connectEnd}.
     */
    protected Map<String, Long> getPageTimingMetrics() {
        return PagePerformanceUtils.getFullTimingMetrics(getDriver());
    }

    // =========================================================================
    // SLA ASSERTION
    // =========================================================================

    /**
     * Asserts that {@code actualMs} is within {@code maxMs}. A breach throws an {@link AssertionError}
     * (unless {@code -Dperformance.sla.enforce=false}, which only logs it), and a negative value, which means
     * "nothing was measured", always throws.
     *
     * @return {@code true} if within SLA; {@code false} if breached while enforcement is off
     */
    protected boolean assertSla(long actualMs, long maxMs, String operation) {
        return ValidationUtils.assertResponseTime(actualMs, maxMs, operation);
    }

    /**
     * Checks a load-test result as a whole: it must have measured something, and the failure rate and the P95
     * latency of the successful calls must be within bounds.
     *
     * <p>Without this, a load test against a service that is down looked excellent: the failed calls came back
     * in a millisecond, were counted as latencies, and a P95 of 1 ms passed any SLA.
     *
     * @param result         the result of {@link #runLoadTest}
     * @param p95SlaMs       maximum P95 latency of the successful calls
     * @param maxFailureRate maximum share of calls that failed, from 0.0 to 1.0
     * @param operation      label for the messages
     */
    protected void assertLoadTest(ApiPerformanceUtils.LoadTestResult result, long p95SlaMs, double maxFailureRate,
            String operation) {
        if (!result.isComplete()) {
            throw new AssertionError("[" + operation + "] load test did not finish: workers were still running "
                    + "after the grace period, so the figures are partial. " + result);
        }
        if (result.getTotalRequests() == 0) {
            throw new AssertionError("[" + operation + "] load test made no calls. " + result);
        }
        if (result.getFailureRate() > maxFailureRate) {
            throw new AssertionError(String.format("[%s] %.1f%% of %d calls failed (limit %.1f%%). %s", operation,
                    result.getFailureRate() * 100, result.getTotalRequests(), maxFailureRate * 100, result));
        }
        ValidationUtils.assertResponseTime(result.getP95Ms(), p95SlaMs, "P95 " + operation);
    }
}
