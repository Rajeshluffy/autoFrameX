package design.patterns.object.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import design.patterns.factory.browser.Browser;
import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserRegistry;

/**
 * Threads that reach a full pool wait for a driver. Waiting only on the idle queue meant they were never
 * woken when a slot was freed by quitting a driver (every test with {@code closeAfterEach=true}, and every
 * failed test), so they failed with "pool exhausted" after the borrow timeout although slots were free.
 */
public class PoolCapacityWaitTest {

	private static final String BROWSER = "CAPACITY_WAIT_TEST_BROWSER";

	/** A driver with no session: navigation is a no-op, so the pool can be exercised without a browser. */
	private static final class QuietDriver extends RemoteWebDriver {
		QuietDriver() {
			super();
		}

		@Override
		public void get(String url) {
			// no-op
		}

		@Override
		public void quit() {
			// no-op: no real session to tear down
		}
	}

	private static WebDriverPoolFactory poolOf(int maxSize, int borrowTimeoutSeconds, AtomicInteger created) {
		BrowserRegistry.register(BROWSER, cfg -> new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				created.incrementAndGet();
				return new QuietDriver();
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				return launchBrowser();
			}
		});

		PoolConfig config = new PoolConfig.Builder()
				.minPoolSize(0)
				.maxPoolSize(maxSize)
				.borrowTimeoutSeconds(borrowTimeoutSeconds)
				.healthCheckEnabled(false)       // the fake driver has no session to answer a health check
				.clearSupportedBrowsers()
				.addSupportedBrowser(BROWSER)
				.build();

		BrowserFactory factory = new BrowserFactory() {
			@Override
			public RemoteWebDriver createDriver(String browserId, PoolConfig cfg) {
				return BrowserRegistry.resolve(browserId, cfg).launchBrowser();
			}
		};
		return new WebDriverPoolFactory(factory, config);
	}

	@Test(timeOut = 60_000)
	public void waitersGetADriverWhenSlotsAreFreedByQuittingNotByRepooling() throws Exception {
		AtomicInteger created = new AtomicInteger();
		WebDriverPoolFactory pool = poolOf(2, 20, created);
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			RemoteWebDriver first = pool.acquire(BROWSER, "https://example.invalid/");
			RemoteWebDriver second = pool.acquire(BROWSER, "https://example.invalid/");

			// The pool is full: these two block in acquire().
			List<Future<RemoteWebDriver>> waiting = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				waiting.add(threads.submit(() -> pool.acquire(BROWSER, "https://example.invalid/")));
			}
			Thread.sleep(500);
			for (Future<RemoteWebDriver> f : waiting) {
				Assert.assertFalse(f.isDone(), "the pool is full, so the waiters must still be waiting");
			}

			// Free both slots the way closeAfterEach=true does: the driver is quit and nothing goes back to
			// the idle queue (a poisoned release takes the same quit-and-free-the-slot path).
			pool.release(first, true);
			pool.release(second, true);

			// 20s borrow timeout: before the fix these failed only after 20s, never earlier.
			long startedAt = System.nanoTime();
			for (Future<RemoteWebDriver> f : waiting) {
				Assert.assertNotNull(f.get(10, TimeUnit.SECONDS), "a waiter should get a driver once a slot is free");
			}
			long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
			Assert.assertTrue(waitedMs < 10_000, "waiters should be served within moments, took " + waitedMs + "ms");
			Assert.assertEquals(created.get(), 4, "two original drivers plus two created for the waiters");
		} finally {
			threads.shutdownNow();
			pool.close();
		}
	}

	@Test(timeOut = 60_000)
	public void aWaiterStillTimesOutWhenNothingIsEverFreed() {
		WebDriverPoolFactory pool = poolOf(1, 1, new AtomicInteger());
		try {
			pool.acquire(BROWSER, "https://example.invalid/");

			long startedAt = System.nanoTime();
			WebDriverPoolFactory.DriverAcquisitionException e = Assert.expectThrows(
					WebDriverPoolFactory.DriverAcquisitionException.class,
					() -> pool.acquire(BROWSER, "https://example.invalid/"));
			long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

			Assert.assertTrue(e.getMessage().contains("pool exhausted"), e.getMessage());
			Assert.assertTrue(waitedMs >= 900 && waitedMs < 5_000,
					"should wait about the 1s borrow timeout, waited " + waitedMs + "ms");
		} finally {
			pool.close();
		}
	}
}
