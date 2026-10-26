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

	/*
	 * A timing the browser has not reached yet is reported as 0, never as (0 - navigationStart): that expression
	 * is about -1.8 trillion while a page is still loading, which is what these scripts used to return, against
	 * a Javadoc that promised 0. A value of 0 means "not measured" to every caller.
	 */
	static final String LOAD_TIME_SCRIPT =
		"var t = window.performance.timing;" +
		"return t.loadEventEnd > 0 ? t.loadEventEnd - t.navigationStart : 0;";

	static final String FULL_TIMING_SCRIPT =
		"var t = window.performance.timing;" +
		"function since(x) { return x > 0 ? x - t.navigationStart : 0; }" +
		"return {" +
		"'navigationStart': t.navigationStart," +
		"'domContentLoaded': since(t.domContentLoadedEventEnd)," +
		"'loadEventEnd': since(t.loadEventEnd)," +
		"'domInteractive': since(t.domInteractive)," +
		"'responseEnd': since(t.responseEnd)," +
		"'connectEnd': (t.connectEnd > 0 && t.connectStart > 0) ? t.connectEnd - t.connectStart : 0" +
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
