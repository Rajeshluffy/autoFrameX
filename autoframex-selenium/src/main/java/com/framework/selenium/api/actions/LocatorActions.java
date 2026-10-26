package com.framework.selenium.api.actions;

import org.openqa.selenium.NoSuchElementException;
import com.framework.utils.StepReporter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.selenium.api.design.Locators;
import com.framework.selenium.exception.ElementNotFoundException;
import com.framework.utils.ImplicitWait;

/** Element-location strategies — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class LocatorActions implements ElementLocator {

	/** How often the two-locator lookup looks again while neither element is there yet. */
	static final long POLL_MILLIS = 200;

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final StepReporter reporter;

	public LocatorActions(Supplier<RemoteWebDriver> driverSupplier, StepReporter reporter) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	public WebElement locateElement(Locators locatorType, String value) {
		By by = ElementSupport.toBy(locatorType, value);
		if (by == null) {
			String errorMsg = "Invalid locator type: " + locatorType;
			reporter.reportStep(errorMsg, "fail", false);
			throw new ElementNotFoundException(errorMsg);
		}

		try {
			return driver().findElement(by);
		} catch (NoSuchElementException e) {
			String errorMsg = "Element not found - " + locatorType + ": " + value;
			// ElementNotFoundException is the documented signal: a caller may treat absence as a valid
			// answer (see its Javadoc). Only a warning here; if nobody catches it the test fails anyway.
			reporter.reportStep(errorMsg, "warning", false);
			throw new ElementNotFoundException(errorMsg, e);
		}
	}

	public WebElement locateElement(String value) {
		try {
			return driver().findElement(By.id(value));
		} catch (NoSuchElementException e) {
			String errorMsg = "Element not found with ID: " + value;
			reporter.reportStep(errorMsg, "warning", false);
			throw new ElementNotFoundException(errorMsg, e);
		}
	}

	public WebElement locateElement(Locators locatorType1, String value1, Locators locatorType2, String value2) {
		By by1 = ElementSupport.toBy(locatorType1, value1);
		By by2 = ElementSupport.toBy(locatorType2, value2);

		if (by1 == null || by2 == null) {
			String errorMsg = "Invalid locator type(s): " +
					(by1 == null ? locatorType1 : "") +
					(by2 == null ? " " + locatorType2 : "");
			reporter.reportStep(errorMsg.trim(), "fail", false);
			throw new ElementNotFoundException(errorMsg.trim());
		}

		try {
			// Both locators are polled together for at most one implicit-wait period, with the implicit wait
			// itself off. Probing them one after the other under the implicit wait made the fallback start
			// only after the primary had waited the whole period (10 s by default) for an element that was
			// never going to be there, and a miss on both took twice that.
			WebElement[] found = new WebElement[2];     // [0] primary, [1] fallback
			long patienceNanos = ImplicitWait.current(driver()).toNanos();
			ImplicitWait.without(driver(), () -> {
				long deadline = System.nanoTime() + patienceNanos;
				while (true) {
					List<WebElement> first = driver().findElements(by1);
					if (!first.isEmpty()) {
						found[0] = first.get(0);
						return;
					}
					List<WebElement> second = driver().findElements(by2);
					if (!second.isEmpty()) {
						found[1] = second.get(0);
						return;
					}
					if (System.nanoTime() - deadline >= 0) {
						return;
					}
					try {
						Thread.sleep(POLL_MILLIS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			});

			if (found[0] != null) {
				reporter.reportStep("Element found using primary locator - " + locatorType1 + ": " + value1,
						"pass", false);
				return found[0];
			}

			if (found[1] != null) {
				reporter.reportStep("Element found using fallback locator - " + locatorType2 + ": " + value2,
						"pass", false);
				return found[1];
			}

			String errorMsg = "Element not found with either locator - " + locatorType1 + ": " + value1 +
					" OR " + locatorType2 + ": " + value2;
			reporter.reportStep(errorMsg, "warning", false);
			throw new ElementNotFoundException(errorMsg);

		} catch (Exception e) {
			if (e instanceof ElementNotFoundException) {
				throw (ElementNotFoundException) e;
			}
			String errorMsg = "Error locating element with multiple locators: " + e.getMessage();
			reporter.reportStep(errorMsg, "fail", true);
			throw new ElementNotFoundException(errorMsg, e);
		}
	}

	public List<WebElement> locateElements(Locators type, String value) {
		By by = ElementSupport.toBy(type, value);
		if (by == null) {
			reporter.reportStep("Invalid locator type: " + type, "fail", false);
			return new ArrayList<>();
		}
		try {
			return driver().findElements(by);
		} catch (NoSuchElementException e) {
			reporter.reportStep("Elements not found - " + type + ": " + value, "warning", false);
			return new ArrayList<>();
		}
	}
}
