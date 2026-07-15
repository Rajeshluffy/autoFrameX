package com.framework.performance;

import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.utils.ValidationUtils;

/**
 * Stateless utility that extracts browser Navigation Timing metrics via JavaScript.
 * All methods require a live WebDriver instance with a fully loaded page.
 */
public final class PagePerformanceUtils {

    private static final Logger logger = LoggerFactory.getLogger(PagePerformanceUtils.class);

    private static final String LOAD_TIME_SCRIPT =
        "return window.performance.timing.loadEventEnd - window.performance.timing.navigationStart;";

    private static final String DOM_CONTENT_LOADED_SCRIPT =
        "return window.performance.timing.domContentLoadedEventEnd - window.performance.timing.navigationStart;";

    private static final String FULL_TIMING_SCRIPT =
        "var t = window.performance.timing; return {" +
        "'navigationStart': t.navigationStart," +
        "'domContentLoaded': t.domContentLoadedEventEnd - t.navigationStart," +
        "'loadEventEnd': t.loadEventEnd - t.navigationStart," +
        "'domInteractive': t.domInteractive - t.navigationStart," +
        "'responseEnd': t.responseEnd - t.navigationStart," +
        "'connectEnd': t.connectEnd - t.connectStart" +
        "};";

    private PagePerformanceUtils() {}

    /**
     * Returns the full page load time in milliseconds using the Navigation Timing API.
     * Returns 0 if the page has not finished loading ({@code loadEventEnd == 0}).
     */
    public static long getPageLoadTimeMs(RemoteWebDriver driver) {
        Object result = ((JavascriptExecutor) driver).executeScript(LOAD_TIME_SCRIPT);
        long ms = result instanceof Number ? ((Number) result).longValue() : 0L;
        logger.debug("Page load time: {} ms", ms);
        return ms;
    }

    /**
     * Returns the time to DOMContentLoaded in milliseconds.
     */
    public static long getDomContentLoadedMs(RemoteWebDriver driver) {
        Object result = ((JavascriptExecutor) driver).executeScript(DOM_CONTENT_LOADED_SCRIPT);
        long ms = result instanceof Number ? ((Number) result).longValue() : 0L;
        logger.debug("DOMContentLoaded time: {} ms", ms);
        return ms;
    }

    /**
     * Checks page load time against {@code maxMs}. Logs a WARNING if the SLA is breached
     * but does not throw — consistent with the framework's SLA-as-warning contract.
     */
    public static void assertPageLoadTime(RemoteWebDriver driver, long maxMs, String pageName) {
        long actual = getPageLoadTimeMs(driver);
        ValidationUtils.assertResponseTime(actual, maxMs, pageName + " page load");
    }

    /**
     * Returns all available Navigation Timing entries as a {@code Map<String, Long>}.
     * Keys: {@code navigationStart}, {@code domContentLoaded}, {@code loadEventEnd},
     * {@code domInteractive}, {@code responseEnd}, {@code connectEnd}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Long> getFullTimingMetrics(RemoteWebDriver driver) {
        Object raw = ((JavascriptExecutor) driver).executeScript(FULL_TIMING_SCRIPT);
        Map<String, Long> metrics = new HashMap<>();
        if (raw instanceof Map) {
            ((Map<String, Object>) raw).forEach(
                (k, v) -> metrics.put(k, v instanceof Number ? ((Number) v).longValue() : 0L));
        }
        logger.debug("Navigation timing metrics: {}", metrics);
        return metrics;
    }
}
