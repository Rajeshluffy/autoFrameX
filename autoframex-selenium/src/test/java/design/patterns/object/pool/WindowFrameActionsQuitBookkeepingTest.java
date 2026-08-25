package design.patterns.object.pool;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.selenium.api.actions.LocatorActions;
import com.framework.selenium.api.actions.WaitActions;
import com.framework.selenium.api.actions.WindowFrameActions;
import com.framework.utils.Reporter;

import design.patterns.factory.browser.Browser;
import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserRegistry;

/**
 * Regression coverage for finding F4 in the framework-3.1 architecture
 * review: {@code WindowFrameActions.quit()} used to call {@code driver().quit()}
 * directly, killing the browser session without telling the pool. A driver
 * killed this way stayed registered as {@code IN_USE} in
 * {@code WebDriverPoolFactory.activeDrivers} forever — or, on a passed test,
 * got re-queued as healthy by {@code teardownDriver()} and handed to the next
 * borrower as a zombie session. {@code quit()} now routes through
 * {@link DriverPoolManager#destroy}, which removes the driver from the pool's
 * active set and clears this thread's driver context.
 *
 * <p>Goes through the real {@code initializePool()}/{@code setupDriver()}
 * path (the same one {@code ProjectSpecificMethods.preCondition()} uses) —
 * unlike {@link PoolCounterAccountingTest}, this finding is specifically
 * about the wiring between {@code WindowFrameActions} and
 * {@code DriverPoolManager}, so it needs the real integration, not just
 * {@code WebDriverPoolFactory} in isolation. All TestNG-parameter values are
 * supplied explicitly so config resolution never has to fall through to
 * {@code ConfigManager}'s file-backed defaults.
 */
public class WindowFrameActionsQuitBookkeepingTest {

	// Referenced reflectively below — stands in for a real @Test method.
	public void dummyTestMethod() {}

	@Test
	public void quitRoutedThroughPoolLeavesNoStaleBookkeeping() throws Exception {
		DriverPoolManager.bindContext("F4_QUIT_TEST_CONTEXT");

		BrowserRegistry.register("F4_QUIT_TEST_BROWSER", cfg -> new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				return new NoOpDriver();
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				return launchBrowser();
			}
		});

		ConcurrentMap<String, String> params = new ConcurrentHashMap<>();
		params.put("browser", "F4_QUIT_TEST_BROWSER");
		params.put("maxPoolSize", "2");
		params.put("minPoolSize", "0");
		params.put("borrowTimeoutSeconds", "2");
		params.put("healthCheck", "false"); // NoOpDriver is session-less; see PoolCounterAccountingTest's F3 test
		params.put("url", "https://example.invalid/"); // Priority-1 in determineUrl() — skips config entirely
		params.put("waitTimeout", "1");

		DriverPoolManager manager = DriverPoolManager.getInstance();
		manager.initializePool(params);
		try {
			Method dummyMethod = getClass().getMethod("dummyTestMethod");
			manager.setupDriver(dummyMethod, params);

			RemoteWebDriver activeDriver = manager.getDriver();
			Assert.assertNotNull(activeDriver, "setupDriver() should have made a driver available");

			// The regression under test: quit() via the same path
			// SeleniumBase/WindowFrameActions exposes to any test/page-object.
			Reporter reporter = new Reporter() {
				@Override
				public long takeSnap() {
					return 0L; // not exercised by this test
				}
			};
			WaitActions waitActions = new WaitActions(manager::getDriver, reporter);
			LocatorActions locatorActions = new LocatorActions(manager::getDriver, reporter);
			WindowFrameActions windowFrameActions =
					new WindowFrameActions(manager::getDriver, reporter, waitActions, locatorActions);
			windowFrameActions.quit();

			// 1. This thread's driver context must be cleared — proves destroy()
			//    ran, not a bare driver().quit() that leaves bookkeeping untouched.
			Assert.assertThrows(IllegalStateException.class, manager::getDriver);

			// 2. The pool's own live-count for this browser type must be back to 0 —
			//    proves the reservation was released, not left as a phantom IN_USE slot.
			String stats = manager.getPoolStatistics();
			Assert.assertTrue(stats.contains("F4_QUIT_TEST_BROWSER") && stats.contains("TOTAL=0"),
					"Expected TOTAL=0 for F4_QUIT_TEST_BROWSER after quit()-via-destroy(); got: " + stats);

			// 3. A subsequent teardownDriver() (what @AfterMethod calls) must find
			//    nothing to release rather than re-queuing the already-destroyed driver.
			manager.teardownDriver(dummyMethod, true);
			String statsAfterTeardown = manager.getPoolStatistics();
			Assert.assertTrue(statsAfterTeardown.contains("IDLE=0"),
					"teardownDriver() after quit() must not re-queue the destroyed driver as IDLE; got: "
					+ statsAfterTeardown);
		} finally {
			manager.shutdownPool();
		}
	}

	/**
	 * Session-less fake driver — no real Chrome process, matches
	 * {@link PoolCounterAccountingTest}'s pattern. Unlike that test's fakes,
	 * this one also overrides {@code manage()}: {@code DriverPoolManager}
	 * constructs its own internal {@code BrowserFactory} (not injectable), so
	 * {@code configureTimeouts()} always runs against every driver this test
	 * produces — a real {@code RemoteWebDriverOptions} would try to execute a
	 * remote command against a nonexistent session and NPE.
	 */
	private static class NoOpDriver extends RemoteWebDriver {
		protected NoOpDriver() {
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

		@Override
		public org.openqa.selenium.WebDriver.Options manage() {
			return new org.openqa.selenium.WebDriver.Options() {
				@Override
				public void addCookie(org.openqa.selenium.Cookie cookie) {}

				@Override
				public void deleteCookieNamed(String name) {}

				@Override
				public void deleteCookie(org.openqa.selenium.Cookie cookie) {}

				@Override
				public void deleteAllCookies() {}

				@Override
				public java.util.Set<org.openqa.selenium.Cookie> getCookies() {
					return java.util.Collections.emptySet();
				}

				@Override
				public org.openqa.selenium.Cookie getCookieNamed(String name) {
					return null;
				}

				@Override
				public org.openqa.selenium.WebDriver.Timeouts timeouts() {
					return new org.openqa.selenium.WebDriver.Timeouts() {
						@Override
						public org.openqa.selenium.WebDriver.Timeouts implicitlyWait(java.time.Duration duration) {
							return this;
						}

						@Override
						public org.openqa.selenium.WebDriver.Timeouts pageLoadTimeout(java.time.Duration duration) {
							return this;
						}

						@Override
						public org.openqa.selenium.WebDriver.Timeouts scriptTimeout(java.time.Duration duration) {
							return this;
						}
					};
				}

				@Override
				public org.openqa.selenium.WebDriver.Window window() {
					throw new UnsupportedOperationException("not needed by this test");
				}

				@Override
				public org.openqa.selenium.logging.Logs logs() {
					throw new UnsupportedOperationException("not needed by this test");
				}
			};
		}
	}
}
