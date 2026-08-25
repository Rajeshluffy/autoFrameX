package com.framework.selenium.api.actions;

import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriverException;

import com.framework.utils.PerfClock;
import com.framework.utils.Reporter;
import com.framework.utils.WaitUtils;

/** Text-entry operations — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class TypeActions {

	private final Reporter reporter;
	private final WaitActions waitActions;

	public TypeActions(Reporter reporter, WaitActions waitActions) {
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

		} catch (ElementNotInteractableException e) {
			reporter.reportStep("Element not interactable: " + ElementSupport.describe(ele), "fail", true);
		} catch (Exception e) {
			ElementSupport.pause(reporter, WaitUtils.getPollingIntervalMs());
			try {
				ele.clear();
				ele.sendKeys(data);
			} catch (Exception e1) {
				reporter.reportStep("Type failed: " + e1.getMessage(), "fail", true);
			}
		}
	}

	public void typeAndTab(WebElement ele, String data) {
		if (ele == null || data == null) {
			reporter.reportStep("Cannot type - element or data is null", "fail", false);
			return;
		}

		try {
			long waitStartNanos = PerfClock.start();
			WebElement visibleElement = waitActions.waitForVisibility(ele);
			long waitMs = PerfClock.elapsedMs(waitStartNanos);
			if (visibleElement != null) {
				long typeStartNanos = PerfClock.start();
				visibleElement.clear();
				visibleElement.sendKeys(data, Keys.TAB);
				long typeMs = PerfClock.elapsedMs(typeStartNanos);
				reporter.reportStep(String.format("Typed and tabbed: %s (wait: %dms, type: %dms, total: %dms)",
						data, waitMs, typeMs, waitMs + typeMs), "info", false);
			}
		} catch (Exception e) {
			reporter.reportStep("Type and tab failed: " + e.getMessage(), "fail", true);
		}
	}

	public void typeAndEnter(WebElement ele, String data) {
		if (ele == null || data == null) {
			reporter.reportStep("Cannot type - element or data is null", "fail", false);
			return;
		}

		try {
			long waitStartNanos = PerfClock.start();
			WebElement visibleElement = waitActions.waitForVisibility(ele);
			long waitMs = PerfClock.elapsedMs(waitStartNanos);
			if (visibleElement != null) {
				long typeStartNanos = PerfClock.start();
				visibleElement.clear();
				visibleElement.sendKeys(data, Keys.ENTER);
				long typeMs = PerfClock.elapsedMs(typeStartNanos);
				reporter.reportStep(String.format("Typed and entered: %s (wait: %dms, type: %dms, total: %dms)",
						data, waitMs, typeMs, waitMs + typeMs), "info", false);
			}
		} catch (Exception e) {
			reporter.reportStep("Type and enter failed: " + e.getMessage(), "fail", true);
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
			waitActions.waitForVisibility(ele).clear();
		} catch (ElementNotInteractableException e) {
			reporter.reportStep("Clear failed - element not interactable", "fail", true);
		} catch (Exception e) {
			reporter.reportStep("Clear failed: " + e.getMessage(), "fail", true);
		}
	}

	public void type(WebElement ele, String data) {
		try {
			waitActions.waitForVisibility(ele).sendKeys(data);
		} catch (ElementNotInteractableException e) {
			reporter.reportStep("Element not interactable", "fail", true);
		} catch (Exception e) {
			reporter.reportStep("Type failed: " + e.getMessage(), "fail", true);
		}
	}
}
