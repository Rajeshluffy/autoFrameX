package design.patterns.object.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import design.patterns.factory.browser.Browser;
import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserRegistry;

/**
 * Regression coverage for two counter-accounting bugs found and fixed during
 * the framework-3.1 architecture review (see the companion "WebDriver Pool
 * Architecture Review" doc, findings F1 and F3):
 *
 * <ul>
 *   <li><b>F1</b> — {@code acquire()} destroyed a driver on any post-creation
 *       failure (a bad navigation) without decrementing {@code poolSizeCounters},
 *       leaking a phantom slot. Reproduced live with zero concurrency: six
 *       sequential navigation failures against an unreachable URL permanently
 *       exhausted a 5-slot pool.</li>
 *   <li><b>F3</b> — the capacity check in {@code acquire()}'s Step 2 read
 *       {@code poolSizeCounters} and only incremented it later, inside
 *       {@code createNewDriver()} — a classic time-of-check/time-of-use gap
 *       that let concurrent threads all pass the check before any of them
 *       incremented, transiently exceeding {@code maxPoolSize}.</li>
 * </ul>
 *
 * <p>Both tests use the same marker-{@link Browser}-provider pattern as
 * {@link MultiCustomBrowserRegistryTest} — no real browser process, so they
 * run in milliseconds and are safe to gate CI on.
 */
public class PoolCounterAccountingTest {

	// =========================================================================
	// F1 — failed acquire() must release its reservation, not leak it
	// =========================================================================

	@Test
	public void navigationFailureAfterCreationReleasesTheReservedSlot() {
		AtomicInteger creationAttempts = new AtomicInteger(0);

		BrowserRegistry.register("F1_LEAK_TEST_BROWSER", cfg -> new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				creationAttempts.incrementAndGet();
				return new NavigationFailingDriver();
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				return launchBrowser();
			}
		});

		PoolConfig config = new PoolConfig.Builder()
				.minPoolSize(0) // no pre-warm — keep this test's driver count fully explicit
				.maxPoolSize(1) // one slot: the second acquire() only succeeds if the first's leaked
				.borrowTimeoutSeconds(1) // fail fast if the leak has regressed, instead of hanging 30s
				.clearSupportedBrowsers()
				.addSupportedBrowser("F1_LEAK_TEST_BROWSER")
				.build();

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), config);
		try {
			// Attempt #1: driver is created successfully, then navigateToUrl()
			// throws (NavigationFailingDriver.get() always fails). Before the
			// F1 fix, the reserved slot was never released.
			Assert.expectThrows(WebDriverPoolFactory.DriverAcquisitionException.class,
					() -> pool.acquire("F1_LEAK_TEST_BROWSER", "https://example.invalid/"));

			// Attempt #2: with maxPoolSize=1, this can only reach a *fresh*
			// createNewDriver() call — proving the reservation was released —
			// if the leak is fixed. A regression would instead exhaust the
			// pool (0 free slots) and throw a borrow-timeout, and
			// creationAttempts would still read 1, not 2.
			Assert.expectThrows(WebDriverPoolFactory.DriverAcquisitionException.class,
					() -> pool.acquire("F1_LEAK_TEST_BROWSER", "https://example.invalid/"));

			Assert.assertEquals(creationAttempts.get(), 2,
					"Second acquire() should have created a fresh driver — if it instead "
					+ "blocked/exhausted, the F1 pool-size-counter leak has regressed. "
					+ "Statistics: " + pool.getStatistics());

			Assert.assertEquals(currentTotal(pool, "F1_LEAK_TEST_BROWSER"), 0,
					"Pool's live TOTAL for this browser type should be back to 0 after both "
					+ "failed acquires destroyed their drivers — a non-zero value here is "
					+ "exactly the leaked phantom slot F1 describes.");
		} finally {
			pool.close();
		}
	}

	// =========================================================================
	// F3 — concurrent acquire() must never push TOTAL past maxPoolSize
	// =========================================================================

	@Test
	public void concurrentAcquireNeverExceedsMaxPoolSize() throws InterruptedException {
		final int maxPoolSize = 3;
		final int threadCount = 24;

		BrowserRegistry.register("F3_RACE_TEST_BROWSER", cfg -> new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				// A short, realistic launch delay — real Chrome takes hundreds of ms
				// to a few seconds to start, which is exactly the window F3's TOCTOU
				// race needs to manifest. An instant-return fake driver would close
				// that window and make the race unrealistically hard to hit.
				sleepMs(40);
				return new InstantReturnDriver();
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				return launchBrowser();
			}
		});

		PoolConfig config = new PoolConfig.Builder()
				.minPoolSize(0)
				.maxPoolSize(maxPoolSize)
				.borrowTimeoutSeconds(5)
				// InstantReturnDriver is session-less (no real command executor), so it
				// can't answer getCurrentUrl() — health-check-on-borrow would treat every
				// reused driver as unhealthy and destroy it, turning this into a churn
				// test instead of a focused reuse/capacity-race test. Off by design here.
				.healthCheckEnabled(false)
				.clearSupportedBrowsers()
				.addSupportedBrowser("F3_RACE_TEST_BROWSER")
				.build();

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), config);
		AtomicInteger maxObservedTotal = new AtomicInteger(0);
		AtomicInteger failures = new AtomicInteger(0);

		// Background sampler: polls the pool's own reported TOTAL while the
		// racing threads run, so we observe the counter's real-time behavior
		// under contention instead of only its value after everything settles.
		ExecutorService samplerExec = Executors.newSingleThreadExecutor();
		java.util.concurrent.atomic.AtomicBoolean sampling = new java.util.concurrent.atomic.AtomicBoolean(true);
		samplerExec.submit(() -> {
			while (sampling.get()) {
				maxObservedTotal.updateAndGet(prev -> Math.max(prev, currentTotal(pool, "F3_RACE_TEST_BROWSER")));
				sleepMs(2);
			}
		});

		try {
			CountDownLatch startLine = new CountDownLatch(1);
			ExecutorService workers = Executors.newFixedThreadPool(threadCount);
			CountDownLatch done = new CountDownLatch(threadCount);

			for (int i = 0; i < threadCount; i++) {
				workers.submit(() -> {
					try {
						startLine.await();
						RemoteWebDriver driver = pool.acquire("F3_RACE_TEST_BROWSER", null);
						sleepMs(10); // hold the driver briefly, like a real test would
						pool.release(driver, false);
					} catch (Exception e) {
						failures.incrementAndGet();
					} finally {
						done.countDown();
					}
				});
			}

			startLine.countDown(); // release all threads at once — maximizes contention at the capacity boundary
			boolean finished = done.await(30, TimeUnit.SECONDS);
			workers.shutdownNow();

			Assert.assertTrue(finished, "Worker threads did not complete within 30s");
			// Give the sampler a couple more ticks to catch a peak right at the tail.
			sleepMs(20);
			maxObservedTotal.updateAndGet(prev -> Math.max(prev, currentTotal(pool, "F3_RACE_TEST_BROWSER")));

			Assert.assertTrue(maxObservedTotal.get() <= maxPoolSize,
					"Observed TOTAL=" + maxObservedTotal.get() + " exceeded maxPoolSize=" + maxPoolSize
					+ " — the F3 time-of-check/time-of-use race on the capacity guard has regressed. "
					+ "Statistics: " + pool.getStatistics());
		} finally {
			sampling.set(false);
			samplerExec.shutdownNow();
			pool.close();
		}
	}

	// =========================================================================
	// HELPERS
	// =========================================================================

	/**
	 * {@link BrowserFactory} whose {@code createDriver} skips
	 * {@code configureTimeouts()} and delegates straight to
	 * {@link BrowserRegistry} — the real implementation calls
	 * {@code driver.manage().timeouts()...} on the freshly-created driver,
	 * which would NPE against the session-less fake drivers this test uses
	 * ({@link NavigationFailingDriver}/{@link InstantReturnDriver} both use
	 * {@link RemoteWebDriver}'s protected no-arg constructor, so they carry no
	 * real command executor).
	 */
	private static BrowserFactory unwrappingFactory() {
		return new BrowserFactory() {
			@Override
			public RemoteWebDriver createDriver(String browserId, PoolConfig cfg) {
				return BrowserRegistry.resolve(browserId, cfg).launchBrowser();
			}
		};
	}

	private static final Pattern TOTAL_PATTERN = Pattern.compile("TOTAL=(\\d+)");

	/** Parses the live TOTAL (IDLE+IN_USE) for {@code browserType} out of {@link WebDriverPoolFactory#getStatistics()}. */
	private static int currentTotal(WebDriverPoolFactory pool, String browserType) {
		String stats = pool.getStatistics();
		for (String line : stats.split("\n")) {
			if (line.trim().startsWith(browserType)) {
				Matcher m = TOTAL_PATTERN.matcher(line);
				if (m.find()) return Integer.parseInt(m.group(1));
			}
		}
		return -1; // browser type not found — test will fail loudly on the assertion
	}

	private static void sleepMs(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Session-less fake driver whose {@code get()} always fails — drives the F1 test. */
	private static class NavigationFailingDriver extends RemoteWebDriver {
		protected NavigationFailingDriver() {
			super();
		}

		@Override
		public void get(String url) {
			throw new org.openqa.selenium.WebDriverException("simulated navigation failure: " + url);
		}

		@Override
		public void quit() {
			// no-op — no real session to tear down
		}
	}

	/** Session-less fake driver that accepts every call as a no-op — drives the F3 test. */
	private static class InstantReturnDriver extends RemoteWebDriver {
		protected InstantReturnDriver() {
			super();
		}

		@Override
		public void get(String url) {
			// no-op
		}

		@Override
		public void quit() {
			// no-op — no real session to tear down
		}
	}
}
