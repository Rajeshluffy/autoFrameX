package com.framework.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Under W3C a command sent while an alert is open dismisses the alert (the default "dismiss and notify"), so the
 * recorder's screenshot, twice a second, cancelled a {@code confirm()} the test was about to answer and the test's
 * own {@code acceptAlert()} then failed with "no alert present". The recorder now skips frames while one is open.
 */
public class VideoRecorderAlertTest {

	private static final class AlertAwareDriver extends RemoteWebDriver {
		final AtomicBoolean alertOpen = new AtomicBoolean();
		final AtomicInteger screenshots = new AtomicInteger();
		final AtomicInteger screenshotsWhileAlertOpen = new AtomicInteger();

		@Override
		public <X> X getScreenshotAs(OutputType<X> target) {
			screenshots.incrementAndGet();
			if (alertOpen.get()) {
				screenshotsWhileAlertOpen.incrementAndGet();   // in a real browser this is what dismisses it
			}
			try {
				File tmp = File.createTempFile("alert-aware", ".png");
				tmp.deleteOnExit();
				ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", tmp);
				@SuppressWarnings("unchecked")
				X result = (X) tmp;
				return result;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public WebDriver.TargetLocator switchTo() {
			return (WebDriver.TargetLocator) Proxy.newProxyInstance(WebDriver.TargetLocator.class.getClassLoader(),
					new Class<?>[] { WebDriver.TargetLocator.class }, (proxy, method, args) -> {
						if (method.getName().equals("alert")) {
							if (!alertOpen.get()) {
								throw new NoAlertPresentException("no alert");
							}
							return null;   // only asked for, never used
						}
						throw new UnsupportedOperationException(method.getName());
					});
		}
	}

	@Test
	public void noFrameIsCapturedWhileAnAlertIsOpen() throws InterruptedException {
		AlertAwareDriver driver = new AlertAwareDriver();
		driver.alertOpen.set(true);
		VideoRecorder recorder = new VideoRecorder();

		recorder.start("alertOpenTest", driver);
		Thread.sleep(1600);                       // three or so ticks at 2 frames a second
		recorder.stop(true);

		Assert.assertEquals(driver.screenshots.get(), 0, "a screenshot would have dismissed the alert");
	}

	@Test
	public void framesResumeOnceTheAlertIsGone() throws InterruptedException {
		AlertAwareDriver driver = new AlertAwareDriver();
		driver.alertOpen.set(true);
		VideoRecorder recorder = new VideoRecorder();

		recorder.start("alertClosedTest", driver);
		Thread.sleep(700);
		driver.alertOpen.set(false);
		Thread.sleep(1300);
		recorder.stop(true);

		Assert.assertTrue(driver.screenshots.get() >= 1, "recording continued after the alert closed");
		Assert.assertEquals(driver.screenshotsWhileAlertOpen.get(), 0);
	}
}
