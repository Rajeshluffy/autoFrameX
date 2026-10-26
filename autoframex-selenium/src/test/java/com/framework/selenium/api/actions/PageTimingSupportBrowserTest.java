package com.framework.selenium.api.actions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import design.patterns.factory.browser.BrowserRegistry;
import design.patterns.object.pool.PoolConfig;

/**
 * Real Chrome, no network: the Navigation Timing scripts evaluated while a page is still loading and after it has.
 * While loading, the browser has not set {@code loadEventEnd} (it is 0), and {@code loadEventEnd - navigationStart}
 * is about -1.8 trillion, which these scripts used to return although their Javadoc promised 0. A browser is the
 * only way to check JavaScript, so this runs in the real-Chrome smoke suite ({@code testng-parallel-smoke.xml}),
 * not in the browser-free unit suite.
 */
public class PageTimingSupportBrowserTest {

	private static RemoteWebDriver chrome() {
		return BrowserRegistry.resolve("CHROME", new PoolConfig.Builder().headless(true).build()).launchBrowser();
	}

	private static String dataUrl(String html) {
		return "data:text/html," + URLEncoder.encode(html, StandardCharsets.UTF_8).replace("+", "%20");
	}

	@Test
	public void timingsNotReachedYetAreZeroNotHugeNegativeNumbers() {
		RemoteWebDriver driver = chrome();
		try {
			// An inline script runs while the document is parsing, before DOMContentLoaded and load.
			driver.get(dataUrl("<script>window.__early = (function () { " + PageTimingSupport.FULL_TIMING_SCRIPT
					+ " })(); window.__earlyLoad = (function () { " + PageTimingSupport.LOAD_TIME_SCRIPT + " })();</script><p>x</p>"));

			@SuppressWarnings("unchecked")
			Map<String, Object> early = (Map<String, Object>) driver.executeScript("return window.__early");
			Assert.assertEquals(((Number) driver.executeScript("return window.__earlyLoad")).longValue(), 0L,
					"load time while the page is still loading");
			Assert.assertEquals(((Number) early.get("loadEventEnd")).longValue(), 0L, "loadEventEnd before the load event: " + early);
			Assert.assertEquals(((Number) early.get("domContentLoaded")).longValue(), 0L, "domContentLoaded before it fired: " + early);
			for (Map.Entry<String, Object> entry : early.entrySet()) {
				Assert.assertTrue(((Number) entry.getValue()).longValue() >= 0, entry.getKey() + " must never be negative: " + early);
			}
		} finally {
			driver.quit();
		}
	}

	@Test
	public void afterTheLoadThePublicMethodsReportRealTimes() {
		RemoteWebDriver driver = chrome();
		try {
			driver.get(dataUrl("<p>loaded</p>"));

			long load = PageTimingSupport.getPageLoadTimeMs(driver);
			Map<String, Long> metrics = PageTimingSupport.getFullTimingMetrics(driver);

			Assert.assertTrue(load > 0 && load < 60_000, "page load time in ms: " + load);
			Assert.assertEquals(metrics.get("loadEventEnd").longValue(), load);
			Assert.assertTrue(metrics.get("domContentLoaded") > 0 && metrics.get("domContentLoaded") <= load, metrics.toString());
			metrics.forEach((key, value) -> Assert.assertTrue(value >= 0, key + " must not be negative: " + metrics));
		} finally {
			driver.quit();
		}
	}
}
