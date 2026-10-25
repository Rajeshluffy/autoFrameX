package com.framework.performance;

import java.lang.reflect.Proxy;
import java.util.function.Supplier;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.api.design.ResponseAPI;

/**
 * Nothing in the performance module could fail a test: an SLA breach was a log line, an error answered in 5 ms
 * "met" the SLA, a page with no timing counted as 0 ms, and a load test against a service that was down reported
 * a flattering P95 because failed calls were counted as latencies. Each of those is pinned here.
 */
public class PerformanceAssertionsAreHonestTest {

	/** Exposes the protected helpers of {@link PerformanceTestBase} without any TestNG lifecycle. */
	private static final class Probe extends PerformanceTestBase {
		long measure(String name, Supplier<ResponseAPI> call, long slaMs) {
			return measureApi(name, call, slaMs);
		}

		boolean sla(long actual, long max, String name) {
			return assertSla(actual, max, name);
		}

		void loadTest(ApiPerformanceUtils.LoadTestResult result, long p95, double maxFailureRate) {
			assertLoadTest(result, p95, maxFailureRate, "probe");
		}
	}

	private static ResponseAPI response(int status) {
		return (ResponseAPI) Proxy.newProxyInstance(ResponseAPI.class.getClassLoader(), new Class<?>[] { ResponseAPI.class },
				(proxy, method, args) -> {
					if (method.getName().equals("getStatusCode")) {
						return status;
					}
					if (method.getName().equals("toString")) {
						return "response(" + status + ")";
					}
					throw new UnsupportedOperationException(method.getName());
				});
	}

	private static Supplier<ResponseAPI> sleeping(long millis, int status) {
		return () -> {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return response(status);
		};
	}

	@BeforeMethod
	public void requireDefaults() {
		String variable = System.getenv("PERFORMANCE_SLA_ENFORCE");
		if (variable != null && !variable.isBlank()) {
			throw new SkipException("PERFORMANCE_SLA_ENFORCE is set in this process");
		}
		System.clearProperty("performance.sla.enforce");
	}

	@AfterMethod(alwaysRun = true)
	public void clearProperty() {
		System.clearProperty("performance.sla.enforce");
	}

	// ----------------------------------------------------------------------------------- SLA

	@Test
	public void aBreachFailsTheTestByDefault() {
		AssertionError error = Assert.expectThrows(AssertionError.class, () -> new Probe().measure("slow", sleeping(120, 200), 10));
		Assert.assertTrue(error.getMessage().contains("SLA BREACH") && error.getMessage().contains("slow"), error.getMessage());
	}

	@Test
	public void aCallWithinItsSlaReturnsTheElapsedTime() {
		long elapsed = new Probe().measure("fast", sleeping(5, 200), 5_000);
		Assert.assertTrue(elapsed >= 1 && elapsed < 5_000, "elapsed " + elapsed);
	}

	@Test
	public void enforcementCanBeSwitchedOffToWarnInstead() {
		System.setProperty("performance.sla.enforce", "false");
		Probe probe = new Probe();
		Assert.assertTrue(probe.measure("slow-but-reported", sleeping(120, 200), 10) >= 100, "measured, not failed");
		Assert.assertFalse(probe.sla(500, 100, "reported"), "returns false for a breach when enforcement is off");
	}

	@Test
	public void nothingMeasuredNeverPassesEvenWithEnforcementOff() {
		System.setProperty("performance.sla.enforce", "false");
		AssertionError error = Assert.expectThrows(AssertionError.class, () -> new Probe().sla(-1, 1_000, "unmeasured"));
		Assert.assertTrue(error.getMessage().contains("No measurement"), error.getMessage());
	}

	// -------------------------------------------------------------------- error answers and pages

	@Test
	public void anErrorAnsweredQuicklyIsNotAFastResponse() {
		AssertionError error = Assert.expectThrows(AssertionError.class,
				() -> new Probe().measure("boom", sleeping(1, 500), 10_000));
		Assert.assertTrue(error.getMessage().contains("not measured") && error.getMessage().contains("HTTP 500"), error.getMessage());
	}

	@Test
	public void aNullResponseIsNotMeasuredEither() {
		Assert.expectThrows(AssertionError.class, () -> new Probe().measure("none", () -> null, 10_000));
	}

	@Test
	public void aPageWithNoLoadTimeIsNotZeroMilliseconds() {
		RemoteWebDriver unloaded = new RemoteWebDriver() {
			@Override
			public Object executeScript(String script, Object... args) {
				return 0L;   // loadEventEnd is 0: the page has not finished loading
			}
		};
		AssertionError error = Assert.expectThrows(AssertionError.class,
				() -> PagePerformanceUtils.assertPageLoadTime(unloaded, 1_000, "home"));
		Assert.assertTrue(error.getMessage().contains("not measured"), error.getMessage());
	}

	// ------------------------------------------------------------------------------ load tests

	@Test
	public void aHealthyLoadTestMeasuresSuccessfulCalls() {
		ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(sleeping(10, 200), 2, 1);
		Assert.assertTrue(result.getSuccessCount() > 5, result.toString());
		Assert.assertEquals(result.getFailureCount(), 0);
		Assert.assertTrue(result.getP95Ms() >= 10, "p95 " + result.getP95Ms());
		Assert.assertTrue(result.isComplete());
		Assert.assertTrue(result.getThroughputPerSecond() > 1.0, result.toString());
		new Probe().loadTest(result, 5_000, 0.0);   // passes: measured, no failures, within SLA
	}

	@Test
	public void failedCallsAreCountedButNotMixedIntoTheLatencies() {
		// Every other call is a refused connection (an exception, about 0 ms); the successful ones take 30 ms.
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		Supplier<ResponseAPI> flaky = () -> {
			if (calls.incrementAndGet() % 2 == 0) {
				throw new IllegalStateException("connection refused");
			}
			return sleeping(30, 200).get();
		};
		ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(flaky, 1, 1);

		Assert.assertTrue(result.getFailureCount() >= 2, result.toString());
		Assert.assertTrue(result.getFailureRate() > 0.3 && result.getFailureRate() < 0.7, "about half failed: " + result);
		Assert.assertTrue(result.getMinMs() >= 30, "fast failures must not appear as latencies; min was " + result.getMinMs());
	}

	@Test
	public void aServiceThatIsDownDoesNotGetAGoodP95() {
		Supplier<ResponseAPI> down = () -> {
			throw new IllegalStateException("connection refused");
		};
		ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(down, 2, 1);

		Assert.assertEquals(result.getSuccessCount(), 0);
		Assert.assertEquals(result.getFailureRate(), 1.0, 0.0001);
		Assert.assertEquals(result.getP95Ms(), -1L, "no successful call, so nothing to take a percentile of");
		Assert.assertThrows(AssertionError.class, () -> new Probe().loadTest(result, 3_000, 1.0));
	}

	@Test
	public void aHighFailureRateFailsTheLoadTestEvenWhenTheSuccessfulCallsAreFast() {
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		Supplier<ResponseAPI> mostlyFailing = () -> {
			if (calls.incrementAndGet() % 4 != 0) {
				return response(503);
			}
			return sleeping(1, 200).get();
		};
		ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(mostlyFailing, 1, 1);

		AssertionError error = Assert.expectThrows(AssertionError.class, () -> new Probe().loadTest(result, 5_000, 0.10));
		Assert.assertTrue(error.getMessage().contains("failed"), error.getMessage());
	}

	@Test(timeOut = 20_000)
	public void aCallThatNeverReturnsIsInterruptedAndTheResultSaysItIsIncomplete() {
		long started = System.nanoTime();
		ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(sleeping(60_000, 200), 1, 1, 1);
		long tookMs = (System.nanoTime() - started) / 1_000_000;

		Assert.assertFalse(result.isComplete(), result.toString());
		Assert.assertTrue(tookMs < 10_000, "the test must not wait for the hung call: took " + tookMs + " ms");
		AssertionError error = Assert.expectThrows(AssertionError.class, () -> new Probe().loadTest(result, 5_000, 1.0));
		Assert.assertTrue(error.getMessage().contains("did not finish"), error.getMessage());
	}
}
