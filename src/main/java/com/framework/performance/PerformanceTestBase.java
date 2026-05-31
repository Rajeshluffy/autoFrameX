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
 *   <li>All SLA breaches are logged as WARNINGs, not failures — a slow response
 *       never masks a functional failure.</li>
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
    public void preCondition(Method method, ITestContext context) {
        logger.info("PerformanceTestBase: skipping driver setup for {}", method.getName());
    }

    @Override
    @AfterMethod(alwaysRun = true)
    public void postCondition(Method method, ITestResult result) {
        FakerDataFactory.cleanup();
        tearDownTest(method, result);
    }

    // =========================================================================
    // API PERFORMANCE HELPERS
    // =========================================================================

    /**
     * Executes {@code call} once, measures wall-clock duration, checks the SLA,
     * and records metrics into {@link ResourceUsageAccumulator} for the observability
     * NDJSON output.
     *
     * <p>SLA breach is a WARNING, not a test failure.
     *
     * @param operationName human-readable label for logging and the event payload
     * @param call          the API operation to measure
     * @param slaMs         SLA threshold in milliseconds
     * @return elapsed duration in milliseconds
     */
    protected long measureApi(String operationName, Supplier<ResponseAPI> call, long slaMs) {
        LogUtils.startTimer(operationName);
        call.get();
        long elapsed = LogUtils.stopTimerWithSla(operationName, slaMs);
        boolean breached = elapsed > slaMs;

        ValidationUtils.assertResponseTime(elapsed, slaMs, operationName);

        ResourceUsageAccumulator.put("operationName", operationName);
        ResourceUsageAccumulator.put("durationMs",    elapsed);
        ResourceUsageAccumulator.put("slaMs",         slaMs);
        ResourceUsageAccumulator.put("slaBreached",   breached);

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
     * Asserts that {@code actualMs} is within {@code maxMs}.
     * Logs a WARNING on breach — does not throw, so a slow response never
     * masks a functional failure.
     *
     * @return {@code true} if within SLA, {@code false} if breached
     */
    protected boolean assertSla(long actualMs, long maxMs, String operation) {
        return ValidationUtils.assertResponseTime(actualMs, maxMs, operation);
    }
}
