package com.framework.performance;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * A page measurement through the base class in a real (headless) Chrome with no network: the test asks for a
 * browser with {@code openBrowser}, measures, and the base class returns it. Before, a {@code PerformanceTestBase}
 * subclass had no way to get a browser at all, so {@code measurePageLoad} could never have run. Needs Chrome, so it
 * is in {@code testng-smoke.xml} (run by ci.yml), not in the browser-free default suite.
 */
public class PerformanceBrowserSmokeTest extends PerformanceTestBase {

    private static final String PAGE = "data:text/html,<title>perf</title><p>measured</p>";

    @Test
    public void aPageIsMeasuredAfterTheTestOpensABrowser() {
        openBrowser(PAGE);

        long loadMs = measurePageLoad("data page", 60_000);
        Map<String, Long> metrics = getPageTimingMetrics();

        Assert.assertTrue(loadMs > 0, "a loaded page has a load time: " + loadMs);
        Assert.assertEquals(metrics.get("loadEventEnd").longValue(), loadMs);
        Assert.assertEquals(getDriver().getTitle(), "perf");
    }

    @Test
    public void aBreachedPageSlaFailsTheTest() {
        openBrowser(PAGE);

        Assert.expectThrows(AssertionError.class, () -> measurePageLoad("data page", 0));
    }
}
