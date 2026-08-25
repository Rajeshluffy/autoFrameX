package com.framework.selenium.api.actions;

import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Navigation Timing API access. {@code WaitActions#waitForPageAndApiReady}
 * (same package) uses this to enrich its own step with page-load time
 * automatically.
 *
 * <p><b>Public</b>, not package-private like {@link ElementSupport} — unlike
 * that class, this one also has a cross-module caller:
 * {@code com.framework.performance.PagePerformanceUtils} (autoframex-performance)
 * delegates here rather than duplicating the JS. The actual logic lives in
 * autoframex-selenium rather than autoframex-performance because the module
 * dependency graph is one-directional (core &larr; selenium &larr; performance) —
 * {@code WaitActions} cannot depend on autoframex-performance without a cycle,
 * so the dependency runs the other way: performance depends on selenium, which
 * it already does.
 */
public final class PageTimingSupport {

	private static final String LOAD_TIME_SCRIPT =
		"return window.performance.timing.loadEventEnd - window.performance.timing.navigationStart;";

	private static final String FULL_TIMING_SCRIPT =
		"var t = window.performance.timing; return {" +
		"'navigationStart': t.navigationStart," +
		"'domContentLoaded': t.domContentLoadedEventEnd - t.navigationStart," +
		"'loadEventEnd': t.loadEventEnd - t.navigationStart," +
		"'domInteractive': t.domInteractive - t.navigationStart," +
		"'responseEnd': t.responseEnd - t.navigationStart," +
		"'connectEnd': t.connectEnd - t.connectStart" +
		"};";

	private PageTimingSupport() {}

	/**
	 * Returns the full page load time in milliseconds using the Navigation Timing API.
	 * Returns 0 if the page has not finished loading ({@code loadEventEnd == 0}).
	 */
	public static long getPageLoadTimeMs(RemoteWebDriver driver) {
		Object result = ((JavascriptExecutor) driver).executeScript(LOAD_TIME_SCRIPT);
		return result instanceof Number ? ((Number) result).longValue() : 0L;
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
		return metrics;
	}
}
