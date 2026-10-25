package design.patterns.object.pool;

import java.util.concurrent.CompletableFuture;
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
 * A pooled browser is retired after {@code maxReuseCount} uses, so a long run does not keep one Chrome process
 * for hours (memory and file descriptors creep up). The count never advanced: every reuse was registered as a
 * brand-new pooled entry whose counter started again at 1, so the limit was never reached and the browser was
 * reused indefinitely. The timeout-borrow path (a thread waiting for a free browser) did not check the limit at all.
 */
public class PoolReuseCapTest {

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

	private static PoolConfig config(String browser, int maxPoolSize, int maxReuse) {
		return new PoolConfig.Builder()
				.minPoolSize(0)
				.maxPoolSize(maxPoolSize)
				.maxReuseCount(maxReuse)
				.borrowTimeoutSeconds(5)
				.healthCheckEnabled(false)   // session-less fakes cannot answer getCurrentUrl()
				.stateResetEnabled(false)
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

	private static void awaitQuits(AtomicInteger quits, int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (quits.get() < expected && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
	}

	@Test
	public void aBrowserIsRetiredOnceItHasBeenUsedMaxReuseCountTimes() throws InterruptedException {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String browser = register("REUSE_CAP_BROWSER", created, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), config(browser, 1, 3));
		try {
			RemoteWebDriver first = pool.acquire(browser, null);
			pool.release(first, false);
			Assert.assertSame(pool.acquire(browser, null), first, "use 2 reuses the browser");
			pool.release(first, false);
			Assert.assertSame(pool.acquire(browser, null), first, "use 3 reuses the browser");
			pool.release(first, false);
			Assert.assertEquals(created.get(), 1, "three uses so far: one browser");

			RemoteWebDriver fourth = pool.acquire(browser, null);
			Assert.assertNotSame(fourth, first, "the browser has served maxReuseCount=3 tests and must be retired");
			Assert.assertEquals(created.get(), 2, "a fresh browser replaced it");
			awaitQuits(quits, 1);
			Assert.assertEquals(quits.get(), 1, "the retired browser was quit, not leaked");
			pool.release(fourth, false);
		} finally {
			pool.close();
		}
	}

	@Test
	public void theCountRestartsWithTheReplacementBrowser() throws InterruptedException {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String browser = register("REUSE_CAP_RESTART_BROWSER", created, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), config(browser, 1, 2));
		try {
			for (int use = 1; use <= 6; use++) {
				RemoteWebDriver driver = pool.acquire(browser, null);
				pool.release(driver, false);
			}
			// six uses with a limit of two: browsers serve uses 1-2, 3-4 and 5-6.
			Assert.assertEquals(created.get(), 3, "one browser per two uses");
		} finally {
			pool.close();
		}
	}

	@Test
	public void aThreadWaitingForABrowserDoesNotGetOneThatIsPastItsLimit() throws Exception {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger quits = new AtomicInteger();
		String browser = register("REUSE_CAP_WAIT_BROWSER", created, quits);

		WebDriverPoolFactory pool = new WebDriverPoolFactory(unwrappingFactory(), config(browser, 1, 1));
		try {
			RemoteWebDriver held = pool.acquire(browser, null);   // the only slot is taken

			// This thread finds the pool full and waits (the borrow-timeout path).
			CompletableFuture<RemoteWebDriver> waiter = CompletableFuture.supplyAsync(() -> pool.acquire(browser, null));
			Thread.sleep(400);
			Assert.assertFalse(waiter.isDone(), "the second acquire waits while the pool is full");

			pool.release(held, false);   // goes back to the queue with its single permitted use already spent

			RemoteWebDriver got = waiter.get(10, TimeUnit.SECONDS);
			Assert.assertNotSame(got, held, "the waiter must not be handed a browser that reached maxReuseCount");
			Assert.assertEquals(created.get(), 2, "the spent browser was replaced");
			awaitQuits(quits, 1);
			Assert.assertEquals(quits.get(), 1);
			pool.release(got, false);
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
