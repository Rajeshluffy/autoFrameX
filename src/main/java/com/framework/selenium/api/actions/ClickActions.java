package com.framework.selenium.api.actions;

import java.time.Duration;
import java.util.function.Supplier;

import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.selenium.api.design.Locators;
import com.framework.utils.Reporter;
import com.framework.utils.WaitUtils;

/**
 * Pointer-interaction methods (click variants, hover, drag/drop) — extracted
 * from {@code SeleniumBase} as part of the TD-07 composition refactor. Depends
 * on {@link WaitActions} (to wait for clickable/visible elements first) and
 * {@link LocatorActions} (for the locator-based click overload).
 *
 * <p>{@link Actions} is built fresh in every method rather than held as a
 * field — it carries no state across calls in the original implementation.
 */
public class ClickActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final Reporter reporter;
	private final WaitActions waitActions;
	private final LocatorActions locatorActions;

	public ClickActions(Supplier<RemoteWebDriver> driverSupplier, Reporter reporter,
			WaitActions waitActions, LocatorActions locatorActions) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
		this.waitActions = waitActions;
		this.locatorActions = locatorActions;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	public void click(WebElement ele) {
		if (ele == null) {
			reporter.reportStep("Cannot click null element", "fail", false);
			return;
		}

		try {
			WebElement clickableElement = waitActions.waitForClickable(ele);
			if (clickableElement == null) {
				reporter.reportStep("Element not clickable: " + ElementSupport.describe(ele), "fail", true);
				return;
			}

			String text = ElementSupport.safeGetText(clickableElement);

			if (clickableElement.isEnabled()) {
				clickableElement.click();
				reporter.reportStep("Clicked element: " + text, "info", false);
			} else {
				driver().executeScript("arguments[0].click()", clickableElement);
				reporter.reportStep("Clicked element using JavaScript: " + text, "info", false);
			}

		} catch (StaleElementReferenceException e) {
			boolean success = WaitUtils.retryAction(reporter, () -> {
				WebElement retryElement = waitActions.waitForClickable(ele);
				if (retryElement != null) {
					retryElement.click();
				}
			}, WaitUtils.getMaxRetryAttempts(), WaitUtils.getPollingIntervalMs());

			if (!success) {
				reporter.reportStep("Failed to click element after retries", "fail", true);
			}

		} catch (Exception e) {
			reporter.reportStep("Click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void clickWithJs(WebElement ele) {
		if (ele == null) {
			reporter.reportStep("Cannot click null element", "fail", false);
			return;
		}

		try {
			boolean success = WaitUtils.retryAction(reporter, () -> driver().executeScript("arguments[0].click()", ele),
					WaitUtils.getMaxRetryAttempts(),
					WaitUtils.getPollingIntervalMs());

			if (success) {
				reporter.reportStep("Clicked using JavaScript: " + ElementSupport.describe(ele), "info", false);
			} else {
				reporter.reportStep("JavaScript click failed", "fail", true);
			}
		} catch (Exception e) {
			reporter.reportStep("JavaScript click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void click(Locators locatorType, String value) {
		WebElement ele = locatorActions.locateElement(locatorType, value);
		if (ele != null) {
			click(ele);
		}
	}

	public void clickWithNoSnap(WebElement ele) {
		try {
			WebElement clickable = waitActions.waitForClickable(ele);
			if (clickable != null) {
				clickable.click();
			}
		} catch (Exception e) {
			reporter.reportStep("Click failed: " + e.getMessage(), "fail", false);
		}
	}

	public void contextClick(WebElement ele) {
		try {
			Actions act = new Actions(driver());
			act.contextClick(waitActions.waitForClickable(ele)).perform();
			reporter.reportStep("Right click performed", "info", false);
		} catch (Exception e) {
			reporter.reportStep("Right click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void doubleClick(WebElement ele) {
		try {
			Actions act = new Actions(driver());
			act.doubleClick(waitActions.waitForClickable(ele)).perform();
			reporter.reportStep("Double click performed", "info", false);
		} catch (Exception e) {
			reporter.reportStep("Double click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void hoverAndClick(WebElement ele) {
		try {
			Actions act = new Actions(driver());
			WebElement clickableElement = waitActions.waitForClickable(ele);
			if (clickableElement != null) {
				act.moveToElement(clickableElement).pause(Duration.ofMillis(500)).click().perform();
				reporter.reportStep("Hover and click completed", "info", false);
			}
		} catch (Exception e) {
			reporter.reportStep("Hover and click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void moveToElement(WebElement ele) {
		try {
			Actions act = new Actions(driver());
			WebElement visibleElement = waitActions.waitForVisibility(ele);
			if (visibleElement != null) {
				act.moveToElement(visibleElement).perform();
			}
		} catch (Exception e) {
			reporter.reportStep("Mouse hover failed: " + e.getMessage(), "fail", true);
		}
	}

	public void dragAndDrop(WebElement eleSource, WebElement eleTarget) {
		try {
			Actions act = new Actions(driver());
			act.dragAndDrop(waitActions.waitForVisibility(eleSource), waitActions.waitForVisibility(eleTarget)).perform();
			reporter.reportStep("Drag and drop completed", "info", false);
		} catch (Exception e) {
			reporter.reportStep("Drag and drop failed: " + e.getMessage(), "fail", true);
		}
	}
}
