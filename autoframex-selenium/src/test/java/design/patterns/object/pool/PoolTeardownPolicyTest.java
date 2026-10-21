package design.patterns.object.pool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import design.patterns.object.pool.DriverPoolManager.Teardown;

/**
 * Locks in what {@code closeAfterEach} means. Until it was fixed, the two settings
 * produced identical teardown (both branches called {@code release(driver, false)}),
 * while the config interface, the properties file and the builder all documented
 * {@code true} as "fully quit the browser after every test".
 *
 * <ul>
 *   <li>{@code true}  - a passed test's browser is quit, its slot is freed and the next
 *       test gets a new browser.</li>
 *   <li>{@code false} - a passed test's browser goes back to the pool and is reused.</li>
 *   <li>a failed test always discards its browser, whatever the setting.</li>
 * </ul>
 *
 * Uses session-less fake drivers, so it runs in milliseconds and needs no browser.
 */
public class PoolTeardownPolicyTest {

	// -------------------------------------------------------------------------
	// The rule itself
	// -------------------------------------------------------------------------

	@Test
	public void teardownDecisionTable() {
		Assert.assertEquals(DriverPoolManager.teardownFor(true, true), Teardown.QUIT);
		Assert.assertEquals(DriverPoolManager.teardownFor(true, false), Teardown.REUSE);
		Assert.assertEquals(DriverPoolManager.teardownFor(false, true), Teardown.DISCARD_FAILED);
		Assert.assertEquals(DriverPoolManager.teardownFor(false, false), Teardown.DISCARD_FAILED,
				"a failed test never returns its browser to the pool, whatever closeAfterEach says");
	}

	@Test
	public void closeAfterEachDefaultsToTrueEverywhere() {
		Assert.assertTrue(new PoolConfig.Builder().build().isCloseAfterEach(),
				"the builder default must match the documented default (true)");
	}

	// -------------------------------------------------------------------------
	// retire(): what QUIT does to the pool
	// -------------------------------------------------------------------------

	@Test
	public void retiredDriverIsQuitItsSlotFreedAndNeverReused() throws InterruptedException {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String browser = register("RETIRE_TEST_BROWSER", created, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), config(browser));
		try {
			RemoteWebDriver first = pool.acquire(browser, null);
			Assert.assertEquals(currentTotal(pool, browser), 1);

			pool.retire(first);

			awaitQuits(quits, 1);
			Assert.assertEquals(quits.get(), 1, "retire() must actually quit the browser");
			Assert.assertEquals(currentTotal(pool, browser), 0, "its capacity slot must be freed");

			RemoteWebDriver second = pool.acquire(browser, null);
			Assert.assertNotSame(second, first, "the next test must not receive the retired browser");
			Assert.assertEquals(created.get(), 2, "a new browser was created for the next test");
		} finally {
			pool.close();
		}
	}

	@Test
	public void releasedDriverStaysAliveAndIsReused() {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String browser = register("REUSE_TEST_BROWSER", created, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), config(browser));
		try {
			RemoteWebDriver first = pool.acquire(browser, null);
			pool.release(first, false);

			RemoteWebDriver second = pool.acquire(browser, null);
			Assert.assertSame(second, first, "closeAfterEach=false: the same browser serves the next test");
			Assert.assertEquals(created.get(), 1);
			Assert.assertEquals(quits.get(), 0, "nothing was quit");
		} finally {
			pool.close();
		}
	}

	@Test
	public void retiringAnUnknownDriverStillQuitsItAndLeavesCountersAlone() throws InterruptedException {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String browser = register("RETIRE_UNKNOWN_TEST_BROWSER", created, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), config(browser));
		try {
			pool.retire(new QuitCountingDriver(quits));
			awaitQuits(quits, 1);
			Assert.assertEquals(quits.get(), 1, "a driver the pool does not track is still quit, not leaked");
			Assert.assertEquals(currentTotal(pool, browser), 0);
		} finally {
			pool.close();
		}
	}

	// -------------------------------------------------------------------------
	// before-navigation hook: lets API capture see the very first page load
	// -------------------------------------------------------------------------

	@Test
	public void beforeNavigationHookRunsAfterTheDriverExistsAndBeforeTheStartPageOpens() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		String browser = registerRecording("HOOK_ORDER_TEST_BROWSER", events);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), noStateResetConfig(browser));
		try {
			pool.acquire(browser, "https://start.example/", driver -> events.add("hook"));

			Assert.assertEquals(events, List.of("hook", "get:https://start.example/"),
					"the hook must run first, or the first page load is already over when capture attaches");
		} finally {
			pool.close();
		}
	}

	@Test
	public void aHookThatFailsNeverFailsTheAcquire() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		String browser = registerRecording("HOOK_FAILURE_TEST_BROWSER", events);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), noStateResetConfig(browser));
		try {
			RemoteWebDriver driver = pool.acquire(browser, "https://start.example/", d -> {
				throw new IllegalStateException("capture could not attach");
			});

			Assert.assertNotNull(driver, "the test still gets its browser");
			Assert.assertEquals(events, List.of("get:https://start.example/"), "and the start page still opens");
		} finally {
			pool.close();
		}
	}

	@Test
	public void theHookAlsoRunsWhenAPooledDriverIsReused() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		String browser = registerRecording("HOOK_REUSE_TEST_BROWSER", events);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), noStateResetConfig(browser));
		try {
			RemoteWebDriver first = pool.acquire(browser, "https://one.example/", d -> events.add("hook-1"));
			pool.release(first, false);
			RemoteWebDriver second = pool.acquire(browser, "https://two.example/", d -> events.add("hook-2"));

			Assert.assertSame(second, first, "the driver was reused");
			Assert.assertEquals(events, List.of("hook-1", "get:https://one.example/", "hook-2", "get:https://two.example/"));
		} finally {
			pool.close();
		}
	}

	@Test
	public void theTwoArgumentAcquireStillWorksWithoutAHook() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		String browser = registerRecording("HOOK_NONE_TEST_BROWSER", events);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), noStateResetConfig(browser));
		try {
			Assert.assertNotNull(pool.acquire(browser, "https://start.example/"));
			Assert.assertEquals(events, List.of("get:https://start.example/"));
		} finally {
			pool.close();
		}
	}

	// -------------------------------------------------------------------------
	// helpers
	// -------------------------------------------------------------------------

	private static String register(String id, AtomicInteger created, AtomicInteger quits) {
		BrowserRegistry.register(id, cfg -> new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				created.incrementAndGet();
				return new QuitCountingDriver(quits);
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				return launchBrowser();
			}
		});
		return id;
	}

	private static String registerRecording(String id, List<String> events) {
		BrowserRegistry.register(id, cfg -> new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				return new RecordingDriver(events);
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				return launchBrowser();
			}
		});
		return id;
	}

	/** Reuse with state reset would call cookie/storage APIs a session-less fake cannot answer. */
	private static PoolConfig noStateResetConfig(String browser) {
		return new PoolConfig.Builder()
				.minPoolSize(0)
				.maxPoolSize(2)
				.borrowTimeoutSeconds(2)
				.healthCheckEnabled(false)
				.stateResetEnabled(false)
				.clearSupportedBrowsers()
				.addSupportedBrowser(browser)
				.build();
	}

	private static PoolConfig config(String browser) {
		return new PoolConfig.Builder()
				.minPoolSize(0)
				.maxPoolSize(2)
				.borrowTimeoutSeconds(2)
				.healthCheckEnabled(false) // session-less fakes cannot answer getCurrentUrl()
				.clearSupportedBrowsers()
				.addSupportedBrowser(browser)
				.build();
	}

	private static BrowserFactory unwrappingFactory() {
		return new BrowserFactory() {
			@Override
			public RemoteWebDriver createDriver(String browserId, PoolConfig cfg) {
				return BrowserRegistry.resolve(browserId, cfg).launchBrowser();
			}
		};
	}

	/** The quit runs on the pool's own executor, so poll briefly instead of asserting immediately. */
	private static void awaitQuits(AtomicInteger quits, int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (quits.get() < expected && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
	}

	private static final Pattern TOTAL_PATTERN = Pattern.compile("TOTAL=(\\d+)");

	private static int currentTotal(WebDriverPoolFactory pool, String browserType) {
		for (String line : pool.getStatistics().split("\n")) {
			if (line.trim().startsWith(browserType)) {
				Matcher m = TOTAL_PATTERN.matcher(line);
				if (m.find()) {
					return Integer.parseInt(m.group(1));
				}
			}
		}
		return -1;
	}

	/** Session-less fake that records every page it is asked to open. */
	private static class RecordingDriver extends RemoteWebDriver {
		private final List<String> events;

		RecordingDriver(List<String> events) {
			super();
			this.events = events;
		}

		@Override
		public void get(String url) {
			events.add("get:" + url);
		}

		@Override
		public void quit() {
			// no-op
		}
	}

	/** Session-less fake that counts how many times it has been quit. */
	private static class QuitCountingDriver extends RemoteWebDriver {
		private final AtomicInteger quits;

		QuitCountingDriver(AtomicInteger quits) {
			super();
			this.quits = quits;
		}

		@Override
		public void get(String url) {
			// no-op
		}

		@Override
		public void quit() {
			quits.incrementAndGet();
		}
	}
}
