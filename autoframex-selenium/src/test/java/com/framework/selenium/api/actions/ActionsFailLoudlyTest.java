package com.framework.selenium.api.actions;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.exception.TestStepFailedException;
import com.framework.selenium.api.design.Locators;
import com.framework.selenium.exception.ElementNotFoundException;
import com.framework.utils.StepReporter;
import com.framework.utils.WaitUtils;

/**
 * The wait helpers return {@code null} on timeout and only warn. These tests pin that every
 * action that needs the element fails the step instead of carrying on and reporting success,
 * and that a step that already failed is not retried or re-reported by its own catch block.
 * No browser: the reporter, waiter and elements are hand-written stubs.
 */
public class ActionsFailLoudlyTest {

	// ------------------------------------------------------------------ stubs

	/** Records every step; throws on "fail" like the real Reporter does (or stays silent when told to). */
	private static final class RecordingReporter implements StepReporter {
		final List<String> steps = new ArrayList<>();
		private final boolean throwOnFail;

		RecordingReporter(boolean throwOnFail) {
			this.throwOnFail = throwOnFail;
		}

		@Override
		public void reportStep(String desc, String status, boolean bSnap) {
			steps.add(status.toUpperCase() + ": " + desc);
			if (throwOnFail && "fail".equalsIgnoreCase(status)) {
				throw new TestStepFailedException("Test failed: " + desc);
			}
		}

		@Override
		public void reportStep(String desc, String status) {
			reportStep(desc, status, true);
		}

		long failCount() {
			return steps.stream().filter(s -> s.startsWith("FAIL")).count();
		}
	}

	/** A waiter whose waits all time out, i.e. return null. */
	private static final class TimedOutWaiter implements ElementWaiter {
		@Override
		public WebDriverWait getWait(int timeoutInSeconds) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T waitFor(ExpectedCondition<T> condition, String errorMessage) {
			return null;
		}

		@Override
		public <T> T waitFor(ExpectedCondition<T> condition, int timeoutInSeconds, String errorMessage) {
			return null;
		}

		@Override
		public WebElement waitForClickable(WebElement ele) {
			return null;
		}

		@Override
		public WebElement waitForVisibility(WebElement element) {
			return null;
		}
	}

	/** A WebElement that records every call made to it. */
	private static WebElement element(List<String> calls) {
		return (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(),
				new Class<?>[] { WebElement.class }, (proxy, method, args) -> {
					calls.add(method.getName());
					if (method.getReturnType() == boolean.class) {
						return false;
					}
					return null;
				});
	}

	/** A driver with no session; only usable where the code under test never touches it. */
	private static RemoteWebDriver noDriver() {
		return null;
	}

	private static void assertTouchedNothing(List<String> calls) {
		Assert.assertFalse(calls.contains("sendKeys") || calls.contains("click") || calls.contains("clear"),
				"the element must not be used after its wait timed out, calls: " + calls);
	}

	// --------------------------------------------------------------- orFail

	@Test
	public void orFailReturnsTheFoundValue() {
		RecordingReporter reporter = new RecordingReporter(true);
		Assert.assertEquals(ElementSupport.orFail(reporter, "found", "unused", false), "found");
		Assert.assertTrue(reporter.steps.isEmpty());
	}

	@Test
	public void orFailStillThrowsWhenTheReporterOnlyRecords() {
		RecordingReporter recordsOnly = new RecordingReporter(false);
		try {
			ElementSupport.orFail(recordsOnly, null, "Element not visible", false);
			Assert.fail("a null result must fail the step");
		} catch (TestStepFailedException expected) {
			Assert.assertEquals(recordsOnly.failCount(), 1);
		}
	}

	// ----------------------------------------------------------------- typing

	@Test
	public void clearAndTypeDoesNotRetypeIntoAnElementThatNeverBecameClickable() {
		List<String> calls = new ArrayList<>();
		RecordingReporter reporter = new RecordingReporter(true);
		TypeActions type = new TypeActions(reporter, new TimedOutWaiter());

		Assert.assertThrows(TestStepFailedException.class, () -> type.clearAndType(element(calls), "secret"));

		assertTouchedNothing(calls);
		Assert.assertEquals(reporter.failCount(), 1, "one fail entry, not a second one from a retry: " + reporter.steps);
	}

	@Test
	public void typeAndTabFailsWhenTheElementNeverBecomesVisible() {
		List<String> calls = new ArrayList<>();
		TypeActions type = new TypeActions(new RecordingReporter(true), new TimedOutWaiter());
		Assert.assertThrows(TestStepFailedException.class, () -> type.typeAndTab(element(calls), "x"));
		assertTouchedNothing(calls);
	}

	@Test
	public void typeAndEnterFailsWhenTheElementNeverBecomesVisible() {
		List<String> calls = new ArrayList<>();
		TypeActions type = new TypeActions(new RecordingReporter(true), new TimedOutWaiter());
		Assert.assertThrows(TestStepFailedException.class, () -> type.typeAndEnter(element(calls), "x"));
		assertTouchedNothing(calls);
	}

	@Test
	public void typeAndClearFailWhenTheElementNeverBecomesVisible() {
		List<String> calls = new ArrayList<>();
		TypeActions type = new TypeActions(new RecordingReporter(true), new TimedOutWaiter());
		Assert.assertThrows(TestStepFailedException.class, () -> type.type(element(calls), "x"));
		Assert.assertThrows(TestStepFailedException.class, () -> type.clear(element(calls)));
		assertTouchedNothing(calls);
	}

	// ------------------------------------------------------------------ clicks

	private static ClickActions clicks(RecordingReporter reporter) {
		return new ClickActions(ActionsFailLoudlyTest::noDriver, reporter, new TimedOutWaiter(), null);
	}

	@Test
	public void everyClickVariantFailsWhenTheElementNeverBecomesClickable() {
		for (String name : new String[] { "click", "clickWithNoSnap", "contextClick", "doubleClick", "hoverAndClick",
				"moveToElement" }) {
			List<String> calls = new ArrayList<>();
			RecordingReporter reporter = new RecordingReporter(true);
			ClickActions clicks = clicks(reporter);
			WebElement ele = element(calls);
			Assert.assertThrows(name + " must fail the step, not report success", TestStepFailedException.class, () -> {
				switch (name) {
				case "click": clicks.click(ele); break;
				case "clickWithNoSnap": clicks.clickWithNoSnap(ele); break;
				case "contextClick": clicks.contextClick(ele); break;
				case "doubleClick": clicks.doubleClick(ele); break;
				case "hoverAndClick": clicks.hoverAndClick(ele); break;
				default: clicks.moveToElement(ele);
				}
			});
			assertTouchedNothing(calls);
			Assert.assertEquals(reporter.failCount(), 1,
					name + " should record the failure once, not once more from its own catch block: " + reporter.steps);
		}
	}

	@Test
	public void dragAndDropFailsWhenEitherEndNeverBecomesVisible() {
		RecordingReporter reporter = new RecordingReporter(true);
		ClickActions clicks = clicks(reporter);
		Assert.assertThrows(TestStepFailedException.class,
				() -> clicks.dragAndDrop(element(new ArrayList<>()), element(new ArrayList<>())));
	}

	// ------------------------------------------------------------ alerts, frames

	@Test
	public void alertActionsFailWhenNoAlertAppears() {
		AlertActions alerts = new AlertActions(ActionsFailLoudlyTest::noDriver, new RecordingReporter(true),
				new TimedOutWaiter());
		Assert.assertThrows(TestStepFailedException.class, alerts::acceptAlert);
		Assert.assertThrows(TestStepFailedException.class, alerts::dismissAlert);
		Assert.assertThrows(TestStepFailedException.class, alerts::switchToAlert);
		Assert.assertThrows(TestStepFailedException.class, alerts::getAlertText);
		Assert.assertThrows(TestStepFailedException.class, () -> alerts.typeAlert("x"));
	}

	@Test
	public void switchToFrameFailsWhenTheFrameNeverBecomesAvailable() {
		RecordingReporter reporter = new RecordingReporter(true);
		WindowFrameActions frames = new WindowFrameActions(ActionsFailLoudlyTest::noDriver, reporter,
				new TimedOutWaiter(), null);
		Assert.assertThrows(TestStepFailedException.class, () -> frames.switchToFrame(0));
		Assert.assertThrows(TestStepFailedException.class, () -> frames.switchToFrame("main"));
		Assert.assertThrows(TestStepFailedException.class, () -> frames.switchToFrame(element(new ArrayList<>())));
		Assert.assertTrue(reporter.steps.stream().noneMatch(s -> s.contains("Switched")),
				"must not report a switch that did not happen: " + reporter.steps);
	}

	// ---------------------------------------------------- locating, fluent waits

	private static RemoteWebDriver driverWhereNothingExists() {
		return new RemoteWebDriver() {
			@Override
			public WebElement findElement(By by) {
				throw new org.openqa.selenium.NoSuchElementException("no such element: " + by);
			}
		};
	}

	@Test
	public void aMissingElementSurfacesAsElementNotFoundException() {
		RecordingReporter reporter = new RecordingReporter(true);
		RemoteWebDriver driver = driverWhereNothingExists();
		LocatorActions locator = new LocatorActions(() -> driver, reporter);

		ElementNotFoundException e = Assert.expectThrows(ElementNotFoundException.class,
				() -> locator.locateElement(Locators.ID, "bendTableDiv"));
		Assert.assertTrue(e.getMessage().contains("bendTableDiv"), e.getMessage());
		Assert.assertEquals(reporter.failCount(), 0,
				"absence may be a valid answer for the caller, so it is a warning here, not a failed step");

		Assert.assertThrows(ElementNotFoundException.class, () -> locator.locateElement("missingId"));
	}

	@Test
	public void fluentWaitKeepsPollingThroughSeleniumsNoSuchElementException() {
		RemoteWebDriver driver = driverWhereNothingExists();
		int[] polls = { 0 };
		String result = WaitUtils.fluentWaitFor(driver, d -> {
			if (++polls[0] < 3) {
				throw new org.openqa.selenium.NoSuchElementException("not there yet");
			}
			return "ready";
		}, 5, 10);
		Assert.assertEquals(result, "ready");
		Assert.assertEquals(polls[0], 3);
	}
}
