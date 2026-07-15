package com.framework.selenium.api.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.selenium.api.design.Locators;
import com.framework.selenium.exception.ElementNotFoundException;
import com.framework.utils.Reporter;

/** Element-location strategies — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class LocatorActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final Reporter reporter;

	public LocatorActions(Supplier<RemoteWebDriver> driverSupplier, Reporter reporter) {
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
			reporter.reportStep(errorMsg, "fail", true);
			throw new ElementNotFoundException(errorMsg, e);
		}
	}

	public WebElement locateElement(String value) {
		try {
			return driver().findElement(By.id(value));
		} catch (NoSuchElementException e) {
			String errorMsg = "Element not found with ID: " + value;
			reporter.reportStep(errorMsg, "fail", true);
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
			if (!driver().findElements(by1).isEmpty()) {
				reporter.reportStep("Element found using primary locator - " + locatorType1 + ": " + value1,
						"pass", false);
				return driver().findElement(by1);
			}

			if (!driver().findElements(by2).isEmpty()) {
				reporter.reportStep("Element found using fallback locator - " + locatorType2 + ": " + value2,
						"pass", false);
				return driver().findElement(by2);
			}

			String errorMsg = "Element not found with either locator - " + locatorType1 + ": " + value1 +
					" OR " + locatorType2 + ": " + value2;
			reporter.reportStep(errorMsg, "fail", true);
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
