package com.framework.performance;

import java.lang.reflect.Proxy;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.design.ResponseAPI;

import design.patterns.object.pool.DriverPoolManager;

/**
 * {@code PerformanceTestBase} is the class a project's performance tests extend, and no test here ever extended
 * it. Two things were wrong that only a subclass running in the real TestNG lifecycle shows:
 * <ul>
 *   <li>the class "skips WebDriver acquisition" for API tests, but it inherits the {@code @BeforeTest} that
 *       pre-warms the browser pool, so every API performance suite started two real Chrome browsers it never used;</li>
 *   <li>{@code measurePageLoad} needs a browser, and the class gave a page test no way to get one: with the
 *       acquisition skipped, {@code getDriver()} failed with "Ensure @BeforeMethod executed", which it never would.</li>
 * </ul>
 */
public class PerformanceTestBaseLifecycleTest extends PerformanceTestBase {

    private static ResponseAPI answering(int status) {
        return (ResponseAPI) Proxy.newProxyInstance(ResponseAPI.class.getClassLoader(), new Class<?>[] { ResponseAPI.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getStatusCode")) {
                        return status;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    public void anApiCallIsMeasuredThroughTheBaseClass() {
        long elapsed = measureApi("fast call", () -> answering(200), 5_000);

        Assert.assertTrue(elapsed >= 0 && elapsed < 5_000, "elapsed " + elapsed);
    }

    @Test
    public void anErrorAnswerIsNotAMeasurement() {
        AssertionError error = Assert.expectThrows(AssertionError.class, () -> measureApi("broken call", () -> answering(503), 5_000));

        Assert.assertTrue(error.getMessage().contains("HTTP 503"), error.getMessage());
    }

    @Test
    public void noBrowserPoolIsStartedForAnApiPerformanceSuite() {
        DriverPoolManager.bindContext(null);

        Assert.assertFalse(DriverPoolManager.getInstance().isPoolInitialized(),
                "the inherited @BeforeTest pre-warmed real browsers for a suite that calls APIs");
    }

    @Test
    public void aPageMeasurementWithoutABrowserSaysHowToGetOne() {
        IllegalStateException error = Assert.expectThrows(IllegalStateException.class, () -> measurePageLoad("Home", 5_000));

        Assert.assertTrue(error.getMessage().contains("openBrowser"),
                "the message must tell the test author what to call: " + error.getMessage());
    }
}
