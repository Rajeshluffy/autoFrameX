package com.framework.selenium.api.actions;

import com.framework.exception.TestStepFailedException;
import com.framework.utils.StepReporter;
import java.time.Duration;
import java.util.function.Supplier;

import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.selenium.api.design.Locators;
import com.framework.utils.PerfClock;
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
public class ClickActions implements ElementClicker {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final StepReporter reporter;
	private final ElementWaiter waitActions;
	private final ElementLocator locatorActions;

	public ClickActions(Supplier<RemoteWebDriver> driverSupplier, StepReporter reporter,
			ElementWaiter waitActions, ElementLocator locatorActions) {
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
			long waitStartNanos = PerfClock.start();
			WebElement clickableElement = waitActions.waitForClickable(ele);
			long waitMs = PerfClock.elapsedMs(waitStartNanos);
			if (clickableElement == null) {
				reporter.reportStep("Element not clickable: " + ElementSupport.describe(ele), "fail", true);
				return;
			}

			String text = ElementSupport.safeGetText(clickableElement);

			long clickStartNanos = PerfClock.start();
			if (clickableElement.isEnabled()) {
				clickableElement.click();
				long clickMs = PerfClock.elapsedMs(clickStartNanos);
				reporter.reportStep(String.format("Clicked element: %s (wait: %dms, click: %dms, total: %dms)",
						text, waitMs, clickMs, waitMs + clickMs), "info", false);
			} else {
				driver().executeScript("arguments[0].click()", clickableElement);
				long clickMs = PerfClock.elapsedMs(clickStartNanos);
				reporter.reportStep(String.format(
						"Clicked element using JavaScript: %s (wait: %dms, click: %dms, total: %dms)",
						text, waitMs, clickMs, waitMs + clickMs), "info", false);
			}

		} catch (StaleElementReferenceException e) {
			boolean success = WaitUtils.retryAction(reporter, () -> {
				WebElement retryElement = waitActions.waitForClickable(ele);
				if (retryElement == null) {
					// Without this the attempt "succeeded" with nothing clicked.
					throw new IllegalStateException("Element not clickable on retry: " + ElementSupport.describe(ele));
				}
				retryElement.click();
			}, WaitUtils.getMaxRetryAttempts(), WaitUtils.getPollingIntervalMs());

			if (!success) {
				reporter.reportStep("Failed to click element after retries", "fail", true);
			}

		} catch (TestStepFailedException e) {
			throw e;
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
		} catch (TestStepFailedException e) {
			throw e;
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
			WebElement clickable = ElementSupport.orFail(reporter, waitActions.waitForClickable(ele),
					"Element not clickable: " + ElementSupport.describe(ele), false);
			clickable.click();
		} catch (TestStepFailedException e) {
			throw e;
		} catch (Exception e) {
			reporter.reportStep("Click failed: " + e.getMessage(), "fail", false);
		}
	}

	public void contextClick(WebElement ele) {
		try {
			WebElement clickable = ElementSupport.orFail(reporter, waitActions.waitForClickable(ele),
					"Element not clickable for right click: " + ElementSupport.describe(ele), true);
			new Actions(driver()).contextClick(clickable).perform();
			reporter.reportStep("Right click performed", "info", false);
		} catch (TestStepFailedException e) {
			throw e;
		} catch (Exception e) {
			reporter.reportStep("Right click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void doubleClick(WebElement ele) {
		try {
			WebElement clickable = ElementSupport.orFail(reporter, waitActions.waitForClickable(ele),
					"Element not clickable for double click: " + ElementSupport.describe(ele), true);
			new Actions(driver()).doubleClick(clickable).perform();
			reporter.reportStep("Double click performed", "info", false);
		} catch (TestStepFailedException e) {
			throw e;
		} catch (Exception e) {
			reporter.reportStep("Double click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void hoverAndClick(WebElement ele) {
		try {
			WebElement clickable = ElementSupport.orFail(reporter, waitActions.waitForClickable(ele),
					"Element not clickable for hover and click: " + ElementSupport.describe(ele), true);
			new Actions(driver()).moveToElement(clickable).pause(Duration.ofMillis(500)).click().perform();
			reporter.reportStep("Hover and click completed", "info", false);
		} catch (TestStepFailedException e) {
			throw e;
		} catch (Exception e) {
			reporter.reportStep("Hover and click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void moveToElement(WebElement ele) {
		try {
			WebElement visible = ElementSupport.orFail(reporter, waitActions.waitForVisibility(ele),
					"Element not visible for hover: " + ElementSupport.describe(ele), true);
			new Actions(driver()).moveToElement(visible).perform();
		} catch (TestStepFailedException e) {
			throw e;
		} catch (Exception e) {
			reporter.reportStep("Mouse hover failed: " + e.getMessage(), "fail", true);
		}
	}

	public void dragAndDrop(WebElement eleSource, WebElement eleTarget) {
		try {
			WebElement source = ElementSupport.orFail(reporter, waitActions.waitForVisibility(eleSource),
					"Drag source not visible: " + ElementSupport.describe(eleSource), true);
			WebElement target = ElementSupport.orFail(reporter, waitActions.waitForVisibility(eleTarget),
					"Drop target not visible: " + ElementSupport.describe(eleTarget), true);
			new Actions(driver()).dragAndDrop(source, target).perform();
			reporter.reportStep("Drag and drop completed", "info", false);
		} catch (TestStepFailedException e) {
			throw e;
		} catch (Exception e) {
			reporter.reportStep("Drag and drop failed: " + e.getMessage(), "fail", true);
		}
	}
}
