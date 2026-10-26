package com.framework.selenium.api.actions;

import com.framework.exception.TestStepFailedException;
import com.framework.utils.StepReporter;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriverException;

import com.framework.utils.PerfClock;
import com.framework.utils.WaitUtils;

/** Text-entry operations — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class TypeActions {

	private final StepReporter reporter;
	private final ElementWaiter waitActions;

	public TypeActions(StepReporter reporter, ElementWaiter waitActions) {
		this.reporter = reporter;
		this.waitActions = waitActions;
	}

	public void clearAndType(WebElement ele, String data) {
		if (ele == null || data == null) {
			reporter.reportStep("Cannot type - element or data is null", "fail", false);
			return;
		}

		try {
			long waitStartNanos = PerfClock.start();
			WebElement visibleElement = waitActions.waitForClickable(ele);
			long waitMs = PerfClock.elapsedMs(waitStartNanos);
			if (visibleElement == null) {
				reporter.reportStep("Element not visible for typing", "fail", true);
				return;
			}

			long typeStartNanos = PerfClock.start();
			visibleElement.clear();
			visibleElement.sendKeys(data);
			long typeMs = PerfClock.elapsedMs(typeStartNanos);
			reporter.reportStep(String.format("Typed text: %s (wait: %dms, type: %dms, total: %dms)",
					data, waitMs, typeMs, waitMs + typeMs), "info", false);

		} catch (TestStepFailedException e) {
			// The step already failed (e.g. "Element not visible for typing"): do not retype into it.
			throw e;
		} catch (ElementNotInteractableException e) {
			reporter.reportStep("Element not interactable: " + ElementSupport.describe(ele), "fail", true);
		} catch (Exception e) {
			ElementSupport.pause(reporter, WaitUtils.getPollingIntervalMs());
			try {
				ele.clear();
				ele.sendKeys(data);
			} catch (Exception e1) {
				reporter.reportStep("Type failed: " + e1.getMessage() + " (first attempt: " + e + ")", "fail", true);
			}
		}
	}

	public void typeAndTab(WebElement ele, String data) {
		typeThenKey(ele, data, Keys.TAB, "Typed and tabbed", "Type and tab failed");
	}

	public void typeAndEnter(WebElement ele, String data) {
		typeThenKey(ele, data, Keys.ENTER, "Typed and entered", "Type and enter failed");
	}

	private void typeThenKey(WebElement ele, String data, Keys key, String doneMessage, String failMessage) {
		if (ele == null || data == null) {
			reporter.reportStep("Cannot type - element or data is null", "fail", false);
			return;
		}

		try {
			long waitStartNanos = PerfClock.start();
			WebElement visibleElement = ElementSupport.orFail(reporter, waitActions.waitForVisibility(ele),
					"Element not visible for typing: " + ElementSupport.describe(ele), true);
			long waitMs = PerfClock.elapsedMs(waitStartNanos);

			long typeStartNanos = PerfClock.start();
			visibleElement.clear();
			visibleElement.sendKeys(data, key);
			long typeMs = PerfClock.elapsedMs(typeStartNanos);
			reporter.reportStep(String.format("%s: %s (wait: %dms, type: %dms, total: %dms)",
					doneMessage, data, waitMs, typeMs, waitMs + typeMs), "info", false);
		} catch (TestStepFailedException e) {
			throw e;
		} catch (Exception e) {
			reporter.reportStep(failMessage + ": " + e.getMessage(), "fail", true);
		}
	}

	public void append(WebElement ele, String data) {
		try {
			ele.sendKeys(data);
		} catch (WebDriverException e) {
			reporter.reportStep("Append failed: " + e.getMessage(), "fail", true);
		}
	}

	public void clear(WebElement ele) {
		try {
			ElementSupport.orFail(reporter, waitActions.waitForVisibility(ele),
					"Element not visible for clear: " + ElementSupport.describe(ele), true).clear();
		} catch (TestStepFailedException e) {
			throw e;
		} catch (ElementNotInteractableException e) {
			reporter.reportStep("Clear failed - element not interactable", "fail", true);
		} catch (Exception e) {
			reporter.reportStep("Clear failed: " + e.getMessage(), "fail", true);
		}
	}

	public void type(WebElement ele, String data) {
		try {
			ElementSupport.orFail(reporter, waitActions.waitForVisibility(ele),
					"Element not visible for typing: " + ElementSupport.describe(ele), true).sendKeys(data);
		} catch (TestStepFailedException e) {
			throw e;
		} catch (ElementNotInteractableException e) {
			reporter.reportStep("Element not interactable", "fail", true);
		} catch (Exception e) {
			reporter.reportStep("Type failed: " + e.getMessage(), "fail", true);
		}
	}
}
