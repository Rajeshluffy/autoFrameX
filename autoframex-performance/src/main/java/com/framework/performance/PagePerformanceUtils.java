package com.framework.performance;

import java.util.Map;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.selenium.api.actions.PageTimingSupport;
import com.framework.utils.ValidationUtils;

/**
 * Stateless utility that extracts browser Navigation Timing metrics via JavaScript.
 * All methods require a live WebDriver instance with a fully loaded page.
 *
 * <p>{@link #getPageLoadTimeMs} and {@link #getFullTimingMetrics} delegate to
 * {@link PageTimingSupport} (autoframex-selenium) — the JS logic lives there
 * so {@code WaitActions.waitForPageAndApiReady()} can use it too (module
 * dependency graph is one-directional: this module depends on selenium, not
 * the reverse). Public signatures here are unchanged for existing callers
 * ({@link PerformanceTestBase}, consumer-project tests).
 */
public final class PagePerformanceUtils {

    private static final Logger logger = LoggerFactory.getLogger(PagePerformanceUtils.class);

    private static final String DOM_CONTENT_LOADED_SCRIPT =
        "return window.performance.timing.domContentLoadedEventEnd - window.performance.timing.navigationStart;";

    private PagePerformanceUtils() {}

    /**
     * Returns the full page load time in milliseconds using the Navigation Timing API.
     * Returns 0 if the page has not finished loading ({@code loadEventEnd == 0}).
     */
    public static long getPageLoadTimeMs(RemoteWebDriver driver) {
        long ms = PageTimingSupport.getPageLoadTimeMs(driver);
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
    public static Map<String, Long> getFullTimingMetrics(RemoteWebDriver driver) {
        Map<String, Long> metrics = PageTimingSupport.getFullTimingMetrics(driver);
        logger.debug("Navigation timing metrics: {}", metrics);
        return metrics;
    }
}
