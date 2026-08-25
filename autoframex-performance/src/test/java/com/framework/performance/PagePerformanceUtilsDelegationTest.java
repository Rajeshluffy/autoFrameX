package com.framework.performance;

import java.util.Map;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.selenium.api.actions.PageTimingSupport;

/**
 * Confirms {@link PagePerformanceUtils#getPageLoadTimeMs} and
 * {@link PagePerformanceUtils#getFullTimingMetrics} genuinely delegate to
 * {@link PageTimingSupport} (autoframex-selenium) rather than duplicating the
 * JS — the refactor behind the framework-3.1 automatic performance-capture
 * design (moved down so {@code WaitActions.waitForPageAndApiReady()} could use
 * the same logic without a circular module dependency).
 *
 * <p>Uses a session-less fake driver (same pattern as
 * {@code design.patterns.object.pool.PoolCounterAccountingTest} in
 * autoframex-selenium) so this runs in milliseconds, no real browser.
 */
public class PagePerformanceUtilsDelegationTest {

	@Test
	public void getPageLoadTimeMsMatchesPageTimingSupportDirectly() {
		FakeTimingDriver driver = new FakeTimingDriver();
		Assert.assertEquals(PagePerformanceUtils.getPageLoadTimeMs(driver), PageTimingSupport.getPageLoadTimeMs(driver));
		Assert.assertEquals(PagePerformanceUtils.getPageLoadTimeMs(driver), 314L);
	}

	@Test
	public void getFullTimingMetricsMatchesPageTimingSupportDirectly() {
		FakeTimingDriver driver = new FakeTimingDriver();
		Map<String, Long> viaPerformanceUtils = PagePerformanceUtils.getFullTimingMetrics(driver);
		Map<String, Long> viaSupportDirectly = PageTimingSupport.getFullTimingMetrics(driver);
		Assert.assertEquals(viaPerformanceUtils, viaSupportDirectly);
		Assert.assertEquals(viaPerformanceUtils.get("loadEventEnd"), Long.valueOf(314L));
		Assert.assertEquals(viaPerformanceUtils.get("connectEnd"), Long.valueOf(0L));
	}

	/**
	 * Session-less fake driver whose {@code executeScript} distinguishes the two
	 * Navigation Timing scripts by a substring only the full-metrics script
	 * contains ({@code "connectEnd"} — the single-value load-time script never
	 * mentions it), since the actual script constants are private to
	 * {@link PageTimingSupport}.
	 */
	private static class FakeTimingDriver extends RemoteWebDriver {
		protected FakeTimingDriver() {
			super();
		}

		@Override
		public Object executeScript(String script, Object... args) {
			if (script.contains("connectEnd")) {
				return Map.of(
						"navigationStart", 1_700_000_000_000L,
						"domContentLoaded", 163L,
						"loadEventEnd", 314L,
						"domInteractive", 163L,
						"responseEnd", 147L,
						"connectEnd", 0L);
			}
			return 314L;
		}

		@Override
		public <X> X getScreenshotAs(OutputType<X> target) {
			throw new UnsupportedOperationException("not needed by this test");
		}
	}
}
