package com.framework.selenium.api.actions;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.utils.StepReporter;

/**
 * {@code switchToWindow(1)} right after the click that opens a popup failed at once with "index out of bounds",
 * because the window was not there yet. {@code switchToWindowByTitle/ByUrl} switched to each window in turn while
 * looking and, when nothing matched, left the browser on whichever window it had looked at last. These pin the
 * waiting, and that a failed search puts the browser back. No browser: a scripted set of windows.
 */
public class WindowSwitchingTest {

	private static final class RecordingReporter implements StepReporter {
		final List<String> steps = new ArrayList<>();

		@Override
		public void reportStep(String desc, String status, boolean bSnap) {
			steps.add(status.toUpperCase() + ": " + desc);
		}

		@Override
		public void reportStep(String desc, String status) {
			reportStep(desc, status, true);
		}
	}

	/** Polls a condition a few times against the fake driver, without sleeping; null when it never holds. */
	private static final class PollingWaiter implements ElementWaiter {
		private final WebDriver driver;
		final int attempts;

		PollingWaiter(WebDriver driver, int attempts) {
			this.driver = driver;
			this.attempts = attempts;
		}

		@Override
		public <T> T waitFor(ExpectedCondition<T> condition, int timeoutInSeconds, String errorMessage) {
			for (int i = 0; i < attempts; i++) {
				T result = condition.apply(driver);
				if (result != null) {
					return result;
				}
			}
			return null;
		}

		@Override
		public <T> T waitFor(ExpectedCondition<T> condition, String errorMessage) {
			return waitFor(condition, 1, errorMessage);
		}

		@Override
		public WebDriverWait getWait(int timeoutInSeconds) {
			throw new UnsupportedOperationException();
		}

		@Override
		public WebElement waitForClickable(WebElement ele) {
			throw new UnsupportedOperationException();
		}

		@Override
		public WebElement waitForVisibility(WebElement element) {
			throw new UnsupportedOperationException();
		}
	}

	/** Windows keyed by handle, in opening order, each with a title and a URL. Some can open late. */
	private static final class FakeBrowser extends RemoteWebDriver {
		final Map<String, String[]> windows = new LinkedHashMap<>();
		final Map<String, Integer> opensAfterListings = new LinkedHashMap<>();
		String current;
		int listings;

		FakeBrowser add(String handle, String title, String url) {
			windows.put(handle, new String[] { title, url });
			if (current == null) {
				current = handle;
			}
			return this;
		}

		FakeBrowser addLate(String handle, String title, String url, int afterListings) {
			windows.put(handle, new String[] { title, url });
			opensAfterListings.put(handle, afterListings);
			return this;
		}

		@Override
		public Set<String> getWindowHandles() {
			listings++;
			Set<String> open = new LinkedHashSet<>();
			for (String handle : windows.keySet()) {
				Integer after = opensAfterListings.get(handle);
				if (after == null || listings > after) {
					open.add(handle);
				}
			}
			return open;
		}

		@Override
		public String getWindowHandle() {
			return current;
		}

		@Override
		public String getTitle() {
			return windows.get(current)[0];
		}

		@Override
		public String getCurrentUrl() {
			return windows.get(current)[1];
		}

		@Override
		public WebDriver.TargetLocator switchTo() {
			return (WebDriver.TargetLocator) Proxy.newProxyInstance(WebDriver.TargetLocator.class.getClassLoader(),
					new Class<?>[] { WebDriver.TargetLocator.class }, (proxy, method, args) -> {
						if (method.getName().equals("window")) {
							current = (String) args[0];
							return this;
						}
						throw new UnsupportedOperationException(method.getName());
					});
		}
	}

	private static WindowFrameActions actions(FakeBrowser browser, RecordingReporter reporter, int attempts) {
		ElementLocator locator = (ElementLocator) Proxy.newProxyInstance(ElementLocator.class.getClassLoader(),
				new Class<?>[] { ElementLocator.class }, (proxy, method, args) -> {
					throw new UnsupportedOperationException(method.getName());
				});
		return new WindowFrameActions(() -> browser, reporter, new PollingWaiter(browser, attempts), locator);
	}

	@Test
	public void aWindowThatOpensAMomentLaterIsWaitedFor() {
		FakeBrowser browser = new FakeBrowser().add("w1", "Home", "https://app/home")
				.addLate("w2", "Popup", "https://app/popup", 3);   // not listed until the 4th look
		RecordingReporter reporter = new RecordingReporter();

		actions(browser, reporter, 10).switchToWindow(1);

		Assert.assertEquals(browser.current, "w2", "it switched to the popup once it had opened");
		Assert.assertTrue(reporter.steps.stream().anyMatch(s -> s.startsWith("INFO: Switched to window index: 1")), reporter.steps.toString());
	}

	@Test
	public void aWindowThatNeverOpensIsReportedAsOutOfBoundsAndTheBrowserStaysPut() {
		FakeBrowser browser = new FakeBrowser().add("w1", "Home", "https://app/home");
		RecordingReporter reporter = new RecordingReporter();

		actions(browser, reporter, 4).switchToWindow(1);

		Assert.assertEquals(browser.current, "w1");
		Assert.assertTrue(reporter.steps.stream().anyMatch(s -> s.startsWith("FAIL") && s.contains("out of bounds") && s.contains("1 open")),
				reporter.steps.toString());
	}

	@Test
	public void aNegativeIndexIsReportedNotThrownAsAnIndexError() {
		FakeBrowser browser = new FakeBrowser().add("w1", "Home", "https://app/home");
		RecordingReporter reporter = new RecordingReporter();

		actions(browser, reporter, 2).switchToWindow(-1);

		Assert.assertTrue(reporter.steps.get(0).startsWith("FAIL") && reporter.steps.get(0).contains("out of bounds"), reporter.steps.toString());
	}

	@Test
	public void switchingByTitleFindsTheWindowAndStaysOnIt() {
		FakeBrowser browser = new FakeBrowser().add("w1", "Home", "https://app/home").add("w2", "Invoice 42", "https://app/inv/42");
		Assert.assertTrue(actions(browser, new RecordingReporter(), 3).switchToWindowByTitle("Invoice 42"));
		Assert.assertEquals(browser.current, "w2");
	}

	@Test
	public void aSearchThatFindsNothingPutsTheBrowserBackWhereItWas() {
		FakeBrowser browser = new FakeBrowser().add("w1", "Home", "https://app/home").add("w2", "Other", "https://app/other");
		browser.current = "w1";
		RecordingReporter reporter = new RecordingReporter();

		boolean found = actions(browser, reporter, 3).switchToWindowByTitle("No such window");

		Assert.assertFalse(found);
		Assert.assertEquals(browser.current, "w1", "it used to be left on w2, the last window it had looked at");
		Assert.assertTrue(reporter.steps.stream().anyMatch(s -> s.startsWith("WARNING") && s.contains("title")), reporter.steps.toString());
	}

	@Test
	public void aUrlSearchSaysUrlAndAlsoRestoresTheWindow() {
		FakeBrowser browser = new FakeBrowser().add("w1", "Home", "https://app/home").add("w2", "Other", "https://app/other");
		RecordingReporter reporter = new RecordingReporter();

		Assert.assertFalse(actions(browser, reporter, 2).switchToWindowByUrl("https://app/missing"));

		Assert.assertEquals(browser.current, "w1");
		Assert.assertTrue(reporter.steps.stream().anyMatch(s -> s.contains("with URL: https://app/missing")),
				"the message used to say 'title' for a URL search: " + reporter.steps);
	}

	@Test
	public void aWindowWhoseTitleArrivesLaterIsFoundOnALaterLook() {
		FakeBrowser browser = new FakeBrowser().add("w1", "Home", "https://app/home")
				.addLate("w2", "Report", "https://app/report", 2);
		Assert.assertTrue(actions(browser, new RecordingReporter(), 8).switchToWindowByTitle("Report"));
		Assert.assertEquals(browser.current, "w2");
	}
}
