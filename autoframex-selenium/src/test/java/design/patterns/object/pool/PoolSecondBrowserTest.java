package design.patterns.object.pool;

import java.util.concurrent.atomic.AtomicInteger;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import design.patterns.factory.browser.Browser;
import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserRegistry;

/**
 * The pool was built for the single browser the suite named first. A method annotated
 * {@code @BrowserConfig(browser = FIREFOX)}, or a second {@code <test browser="firefox">} in the same run,
 * asked it for another browser and got {@code IllegalArgumentException: Unsupported browser}, even though the
 * browser was registered and perfectly usable. A registered browser the pool was not started with is now added
 * when it is first asked for; an id that is not registered anywhere is still rejected.
 */
public class PoolSecondBrowserTest {

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

	private static PoolConfig poolFor(String firstBrowser) {
		return new PoolConfig.Builder()
				.minPoolSize(0)
				.maxPoolSize(2)
				.borrowTimeoutSeconds(3)
				.healthCheckEnabled(false)
				.stateResetEnabled(false)
				.clearSupportedBrowsers()
				.addSupportedBrowser(firstBrowser)
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

	@Test
	public void aRegisteredBrowserTheSuiteDidNotStartWithIsAddedOnFirstUse() {
		AtomicInteger firstCreated = new AtomicInteger();
		AtomicInteger secondCreated = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String first = register("SECOND_BROWSER_FIRST", firstCreated, quits);
		String second = register("SECOND_BROWSER_SECOND", secondCreated, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), poolFor(first));
		try {
			RemoteWebDriver driverOfFirst = pool.acquire(first, null);
			RemoteWebDriver driverOfSecond = pool.acquire(second, null);   // used to throw "Unsupported browser"

			Assert.assertNotNull(driverOfSecond);
			Assert.assertNotSame(driverOfSecond, driverOfFirst);
			Assert.assertEquals(firstCreated.get(), 1);
			Assert.assertEquals(secondCreated.get(), 1, "the second browser's own provider created it");
			Assert.assertTrue(pool.getStatistics().contains(second), "statistics list the added browser:\n" + pool.getStatistics());
		} finally {
			pool.close();
		}
	}

	@Test
	public void theAddedBrowserIsPooledAndReusedLikeTheFirst() {
		AtomicInteger firstCreated = new AtomicInteger();
		AtomicInteger secondCreated = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String first = register("REUSE_SECOND_FIRST", firstCreated, quits);
		String second = register("REUSE_SECOND_SECOND", secondCreated, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), poolFor(first));
		try {
			RemoteWebDriver one = pool.acquire(second, null);
			pool.release(one, false);
			RemoteWebDriver two = pool.acquire(second, null);

			Assert.assertSame(two, one, "a released driver of the added browser serves the next test");
			Assert.assertEquals(secondCreated.get(), 1);
			Assert.assertEquals(firstCreated.get(), 0, "the first browser was never needed");
		} finally {
			pool.close();
		}
	}

	@Test
	public void theAddedBrowsersDriversAreQuitWhenThePoolCloses() throws InterruptedException {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String first = register("CLOSE_SECOND_FIRST", new AtomicInteger(), new AtomicInteger());
		String second = register("CLOSE_SECOND_SECOND", created, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), poolFor(first));
		RemoteWebDriver driver = pool.acquire(second, null);
		pool.release(driver, false);        // idle in the pool, not yet quit
		pool.close();

		long deadline = System.nanoTime() + 5_000_000_000L;
		while (quits.get() < 1 && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
		Assert.assertEquals(quits.get(), 1, "closing the pool must not leave the added browser's driver running");
	}

	@Test
	public void anIdThatIsNotRegisteredAnywhereIsStillRejected() {
		String first = register("REJECT_FIRST", new AtomicInteger(), new AtomicInteger());
		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), poolFor(first));
		try {
			IllegalArgumentException thrown = Assert.expectThrows(IllegalArgumentException.class,
					() -> pool.acquire("NO_SUCH_BROWSER_ANYWHERE", null));
			Assert.assertTrue(thrown.getMessage().contains("NO_SUCH_BROWSER_ANYWHERE"), thrown.getMessage());
		} finally {
			pool.close();
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
