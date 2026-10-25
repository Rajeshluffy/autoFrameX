package design.patterns.object.pool;

import java.util.concurrent.atomic.AtomicReference;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import design.patterns.factory.browser.Browser;
import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserRegistry;

/**
 * Each health check got its own new non-daemon executor. Against a hung driver the check's thread stays blocked in
 * the socket read after {@code shutdownNow()}, so every hung check left a live non-daemon thread behind that could
 * keep the JVM from exiting. The checks now run on shared daemon threads.
 */
public class PoolHealthCheckThreadsTest {

	private static final class ProbeDriver extends RemoteWebDriver {
		final AtomicReference<Thread> checkedOn = new AtomicReference<>();

		@Override
		public String getCurrentUrl() {
			checkedOn.set(Thread.currentThread());
			return "about:blank";
		}

		@Override
		public void quit() {
			// session-less fake
		}
	}

	@Test
	public void healthChecksRunOnADaemonThread() {
		ProbeDriver driver = new ProbeDriver();
		BrowserRegistry.register("HEALTH_THREADS_BROWSER", cfg -> new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				return driver;
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				return driver;
			}
		});
		PoolConfig config = new PoolConfig.Builder()
				.minPoolSize(0)
				.maxPoolSize(1)
				.maxReuseCount(10)
				.borrowTimeoutSeconds(5)
				.healthCheckEnabled(true)
				.stateResetEnabled(false)
				.clearSupportedBrowsers()
				.addSupportedBrowser("HEALTH_THREADS_BROWSER")
				.build();
		BrowserFactory factory = new BrowserFactory() {
			@Override
			public RemoteWebDriver createDriver(String browserId, PoolConfig cfg) {
				return BrowserRegistry.resolve(browserId, cfg).launchBrowser();
			}
		};

		WebDriverPoolFactory pool = new WebDriverPoolFactory(factory, config);
		try {
			RemoteWebDriver first = pool.acquire("HEALTH_THREADS_BROWSER", null);
			pool.release(first, false);
			Assert.assertSame(pool.acquire("HEALTH_THREADS_BROWSER", null), first, "the second use goes through the health check");

			Thread checker = driver.checkedOn.get();
			Assert.assertNotNull(checker, "the health check ran");
			Assert.assertTrue(checker.isDaemon(), "a hung check must not be able to hold the JVM open: " + checker);
			Assert.assertNotSame(checker, Thread.currentThread(), "and it ran off the caller's thread, so it can time out");
		} finally {
			pool.close();
		}
	}
}
