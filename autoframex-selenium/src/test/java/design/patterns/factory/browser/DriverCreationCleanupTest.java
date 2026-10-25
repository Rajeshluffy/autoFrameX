package design.patterns.factory.browser;

import java.util.concurrent.atomic.AtomicInteger;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.FileDetector;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import design.patterns.object.pool.PoolConfig;

/**
 * {@link BrowserFactory} launched the browser and then applied the timeouts; if that second step threw (the
 * session died, the Grid refused a timeout) nothing held a reference to the running browser and it stayed open
 * for the rest of the run. And a Grid session had no file detector, so {@code sendKeys("C:\\data\\x.pdf")} sent
 * the path as text for the node to look up on its own disk.
 */
public class DriverCreationCleanupTest {

	private static final class QuitCountingDriver extends RemoteWebDriver {
		final AtomicInteger quits = new AtomicInteger();
		final boolean timeoutsFail;
		final boolean quitFails;
		FileDetector detector;

		QuitCountingDriver(boolean timeoutsFail, boolean quitFails) {
			this.timeoutsFail = timeoutsFail;
			this.quitFails = quitFails;
		}

		@Override
		public WebDriver.Options manage() {
			if (timeoutsFail) {
				throw new IllegalStateException("session is gone");
			}
			return super.manage();
		}

		@Override
		public void quit() {
			quits.incrementAndGet();
			if (quitFails) {
				throw new IllegalStateException("quit failed too");
			}
		}

		@Override
		public void setFileDetector(FileDetector detector) {
			this.detector = detector;
		}
	}

	private static void register(String id, QuitCountingDriver driver) {
		BrowserRegistry.register(id, cfg -> new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				return driver;
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				return driver;
			}
		});
	}

	@Test
	public void aBrowserWhoseTimeoutsCannotBeSetIsQuitNotLeaked() {
		QuitCountingDriver driver = new QuitCountingDriver(true, false);
		register("CLEANUP_TIMEOUT_FAILS", driver);

		IllegalStateException e = Assert.expectThrows(IllegalStateException.class,
				() -> new BrowserFactory().createDriver("CLEANUP_TIMEOUT_FAILS", new PoolConfig.Builder().build()));

		Assert.assertEquals(e.getMessage(), "session is gone", "the original failure is what the caller sees");
		Assert.assertEquals(driver.quits.get(), 1, "the half-created browser was quit");
	}

	@Test
	public void aFailingQuitDoesNotHideTheOriginalFailure() {
		QuitCountingDriver driver = new QuitCountingDriver(true, true);
		register("CLEANUP_QUIT_FAILS", driver);

		IllegalStateException e = Assert.expectThrows(IllegalStateException.class,
				() -> new BrowserFactory().createDriver("CLEANUP_QUIT_FAILS", new PoolConfig.Builder().build()));

		Assert.assertEquals(e.getMessage(), "session is gone");
		Assert.assertEquals(e.getSuppressed().length, 1, "the quit failure is attached, not thrown instead");
	}

	@Test
	public void aGridSessionUploadsLocalFilesToTheNode() {
		QuitCountingDriver driver = new QuitCountingDriver(false, false);

		RemoteGridBrowser.useLocalFileUploads(driver);

		Assert.assertTrue(driver.detector instanceof LocalFileDetector, "got " + driver.detector);
	}
}
