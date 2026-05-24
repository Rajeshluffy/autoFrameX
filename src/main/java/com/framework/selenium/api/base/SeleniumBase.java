package com.framework.selenium.api.base;

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;

import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.framework.selenium.api.design.Browser;
import com.framework.selenium.api.design.Element;
import com.framework.selenium.api.design.Locators;
import com.framework.utils.Reporter;
import com.framework.utils.WaitUtils;

import design.patterns.factory.browser.BrowserType;
import design.patterns.object.pool.WebDriverPoolFactory;

/**
 * <b>Class Name:</b> SeleniumBase <br>
 * <b>Purpose:</b> Implements the Browser and Element interfaces to provide a
 * robust, thread-safe base for Selenium-based automation. <br>
 * <b>Responsibilities:</b> Wrapper for WebDriver actions, centralized
 * reporting,
 * and explicit wait management with minimal static waits.
 * 
 * @author Framework Architect
 * @version 3.1 - Fixed driver manager access and improved error handling
 */
public class SeleniumBase extends Reporter implements Browser, Element {
	protected Actions act;

	/**
	 * Fetches the WebDriver instance for the current thread.
	 * FIXED: Proper method call instead of field access
	 * 
	 * @return RemoteWebDriver instance from the pool.
	 * @throws IllegalStateException if driver not available
	 */
	private RemoteWebDriver getDriver() {
		try {
			return getDriverManager().getDriver();
		} catch (Exception e) {
			reportStep("Driver not available: " + e.getMessage(), "fail", false);
			throw new IllegalStateException("No WebDriver available. Ensure @BeforeMethod executed.", e);
		}
	}

	/**
	 * Creates a custom wait with specific timeout
	 * 
	 * @param timeoutInSeconds timeout duration in seconds
	 * @return WebDriverWait instance
	 */
	public WebDriverWait getWait(int timeoutInSeconds) {
		return new WebDriverWait(getDriver(), Duration.ofSeconds(timeoutInSeconds));
	}

	/**
	 * Generic wait method with custom expected condition and default timeout
	 * 
	 * @param <T>          return type
	 * @param condition    ExpectedCondition to wait for
	 * @param errorMessage message to log on failure
	 * @return result of the condition or null on timeout
	 */
	public <T> T waitFor(ExpectedCondition<T> condition, String errorMessage) {
		return WaitUtils.waitFor(getDriver(), this, condition, errorMessage);
	}

	/**
	 * Generic wait method with custom expected condition and timeout
	 * IMPROVED: Better error handling and logging
	 * 
	 * @param <T>              return type
	 * @param condition        ExpectedCondition to wait for
	 * @param timeoutInSeconds timeout in seconds
	 * @param errorMessage     message to log on failure
	 * @return result of the condition or null on timeout
	 */

	public <T> T waitFor(ExpectedCondition<T> condition, int timeoutInSeconds, String errorMessage) {
		return WaitUtils.waitFor(getDriver(), this, condition, timeoutInSeconds, errorMessage);
	}

	/**
	 * Generic fluent wait with default timeout and polling
	 * 
	 * @param <T>       return type
	 * @param condition Function to wait for
	 * @return result of the condition or null on timeout
	 */
	public <T> T fluentWaitFor(Function<RemoteWebDriver, T> condition) {
		return WaitUtils.fluentWaitFor(getDriver(), condition);
	}

	/**
	 * Generic fluent wait with polling and ignoring specific exceptions
	 * IMPROVED: Added configurable ignored exceptions
	 * 
	 * @param <T>               return type
	 * @param condition         Function to wait for
	 * @param timeoutInSeconds  timeout in seconds
	 * @param pollingIntervalMs polling interval in milliseconds
	 * @return result of the condition or null on timeout
	 */
	public <T> T fluentWaitFor(Function<RemoteWebDriver, T> condition, int timeoutInSeconds, int pollingIntervalMs) {
		return WaitUtils.fluentWaitFor(getDriver(), condition, timeoutInSeconds, pollingIntervalMs);
	}

	// ========================================================================
	// IMPLICIT WAIT OPERATIONS - NEW
	// ========================================================================

	/**
	 * Sets a temporary implicit wait for the current driver.
	 * 
	 * @param seconds timeout in seconds
	 */
	@Override
	public void setImplicitWait(int seconds) {
		WaitUtils.setImplicitWait(getDriver(), this, seconds);
	}

	/**
	 * Resets the implicit wait to the default value configured in the framework.
	 */
	@Override
	public void resetImplicitWait() {
		WaitUtils.resetImplicitWait(getDriver(), this);
	}

	// ========================================================================
	// WAIT OPERATIONS - IMPROVED
	// ========================================================================

	@Override
	public WebElement waitForClickable(WebElement ele) {
		try {
			return WaitUtils.waitFor(getDriver(), this, ExpectedConditions.elementToBeClickable(ele),
					WaitUtils.getDefaultWaitTime(),
					"Element not clickable: " + getElementDescription(ele));
		} catch (Exception e) {
			reportStep("Failed to wait for clickable element", "fail", true);
			return null;
		}
	}

	@Override
	public WebElement waitForVisibility(WebElement element) {
		try {
			return WaitUtils.waitFor(getDriver(), this, ExpectedConditions.visibilityOf(element),
					WaitUtils.getDefaultWaitTime(),
					"Element not visible: " + getElementDescription(element));
		} catch (Exception e) {
			reportStep("Failed to wait for element visibility", "fail", true);
			return null;
		}
	}

	@Override
	public void waitForApperance(WebElement element) {
		try {
			WaitUtils.waitFor(getDriver(), this, ExpectedConditions.visibilityOf(element),
					WaitUtils.getDefaultWaitTime(),
					"Element did not appear: " + getElementDescription(element));
		} catch (Exception e) {
			reportStep("Element did not appear within timeout", "warning", false);
		}
	}

	@Override
	public void waitForDisapperance(WebElement element) {
		WaitUtils.waitFor(getDriver(), this, ExpectedConditions.invisibilityOf(element),
				WaitUtils.getShortWaitTime(),
				"Element did not disappear: " + getElementDescription(element));
	}

	// ========================================================================
	// CLICK OPERATIONS - IMPROVED
	// ========================================================================

	@Override
	public void click(WebElement ele) {
		if (ele == null) {
			reportStep("Cannot click null element", "fail", false);
			return;
		}

		try {
			// Wait for element to be clickable
			WebElement clickableElement = waitForClickable(ele);
			if (clickableElement == null) {
				reportStep("Element not clickable: " + getElementDescription(ele), "fail", true);
				return;
			}

			String text = safeGetText(clickableElement);

			// Try normal click first
			if (clickableElement.isEnabled()) {
				clickableElement.click();
				reportStep("Clicked element: " + text, "info", false);
			} else {
				// Fallback to JS click
				getDriver().executeScript("arguments[0].click()", clickableElement);
				reportStep("Clicked element using JavaScript: " + text, "info", false);
			}

		} catch (StaleElementReferenceException e) {
			// Retry with fluent wait
			boolean success = WaitUtils.retryAction(this, () -> {
				WebElement retryElement = waitForClickable(ele);
				if (retryElement != null) {
					retryElement.click();
				}
			}, WaitUtils.getMaxRetryAttempts(), WaitUtils.getPollingIntervalMs());

			if (!success) {
				reportStep("Failed to click element after retries", "fail", true);
			}

		} catch (Exception e) {
			reportStep("Click failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void clickWithJs(WebElement ele) {
		if (ele == null) {
			reportStep("Cannot click null element", "fail", false);
			return;
		}

		try {
			boolean success = WaitUtils.retryAction(this, () -> getDriver().executeScript("arguments[0].click()", ele),
					WaitUtils.getMaxRetryAttempts(),
					WaitUtils.getPollingIntervalMs());

			if (success) {
				reportStep("Clicked using JavaScript: " + getElementDescription(ele), "info", false);
			} else {
				reportStep("JavaScript click failed", "fail", true);
			}
		} catch (Exception e) {
			reportStep("JavaScript click failed: " + e.getMessage(), "fail", true);
		}
	}


	public void refresh() {

		String script = "return document.readyState";

		try {
			getDriver().navigate().refresh();
			getWait(10).until(webDriver -> getDriver().executeScript(script)
					.equals("complete"));

		} catch (Exception e) {
			reportStep("JavaScript click failed: " + e.getMessage(), "fail", true);
		}
	}

	// ========================================================================
	// TYPE OPERATIONS - IMPROVED
	// ========================================================================

	@Override
	public void clearAndType(WebElement ele, String data) {
		if (ele == null || data == null) {
			reportStep("Cannot type - element or data is null", "fail", false);
			return;
		}

		try {

			WebElement visibleElement = waitForClickable(ele);
			if (visibleElement == null) {
				reportStep("Element not visible for typing", "fail", true);
				return;
			}

			visibleElement.clear();
			visibleElement.sendKeys(data);
			reportStep("Typed text: " + data, "info", false);

		} catch (ElementNotInteractableException e) {
			reportStep("Element not interactable: " + getElementDescription(ele), "fail", true);
		} catch (Exception e) {
			// Retry once
			pause(WaitUtils.getPollingIntervalMs());
			try {
				ele.clear();
				ele.sendKeys(data);
			} catch (Exception e1) {
				reportStep("Type failed: " + e1.getMessage(), "fail", true);
			}
		}
	}

	@Override
	public void typeAndTab(WebElement ele, String data) {
		if (ele == null || data == null) {
			reportStep("Cannot type - element or data is null", "fail", false);
			return;
		}

		try {
			WebElement visibleElement = waitForVisibility(ele);
			if (visibleElement != null) {
				visibleElement.clear();
				visibleElement.sendKeys(data, Keys.TAB);
				reportStep("Typed and tabbed: " + data, "info", false);
			}
		} catch (Exception e) {
			reportStep("Type and tab failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void typeAndEnter(WebElement ele, String data) {
		if (ele == null || data == null) {
			reportStep("Cannot type - element or data is null", "fail", false);
			return;
		}

		try {
			WebElement visibleElement = waitForVisibility(ele);
			if (visibleElement != null) {
				visibleElement.clear();
				visibleElement.sendKeys(data, Keys.ENTER);
				reportStep("Typed and entered: " + data, "info", false);
			}
		} catch (Exception e) {
			reportStep("Type and enter failed: " + e.getMessage(), "fail", true);
		}
	}

	// ========================================================================
	// UTILITY METHODS - NEW
	// ========================================================================

	/**
	 * Safely gets text from element without throwing exception
	 * 
	 * @param ele element to get text from
	 * @return element text or empty string
	 */
	private String safeGetText(WebElement ele) {
		try {
			String text = ele.getText();
			return text != null && !text.isEmpty() ? text : ele.getAttribute("value");
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * Gets element description for logging
	 * 
	 * @param ele element to describe
	 * @return element description
	 */
	private String getElementDescription(WebElement ele) {
		try {
			String tag = ele.getTagName();
			String id = ele.getAttribute("id");
			String name = ele.getAttribute("name");

			if (id != null && !id.isEmpty()) {
				return tag + "[id='" + id + "']";
			} else if (name != null && !name.isEmpty()) {
				return tag + "[name='" + name + "']";
			} else {
				return tag;
			}
		} catch (Exception e) {
			return "unknown element";
		}
	}

	// ========================================================================
	// VERIFICATION METHODS - IMPROVED
	// ========================================================================

	@Override
	public boolean verifyDisplayed(WebElement ele) {
		try {
			WebElement visibleElement = waitForVisibility(ele);
			if (visibleElement != null && visibleElement.isDisplayed()) {
				reportStep("Element is displayed: " + getElementDescription(ele), "pass", false);
				return true;
			} else {
				reportStep("Element not displayed: " + getElementDescription(ele), "warning", false);
				return false;
			}
		} catch (Exception e) {
			reportStep("Display check failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	@Override
	public boolean verifyExactText(WebElement ele, String expectedText) {
		try {
			// waitForVisibility returns null when the element never becomes visible
			// (the catch block inside it swallows the TimeoutException and returns null).
			// Dereferencing null here caused a NullPointerException — fixed with an
			// explicit null guard before calling getText().
			WebElement visible = waitForVisibility(ele);
			if (visible == null) {
				reportStep("Element not visible — cannot verify text: '" + expectedText + "'", "fail", true);
				return false;
			}
			String actualText = visible.getText();
			if (actualText.equals(expectedText)) {
				reportStep("Text matches: '" + expectedText + "'", "pass", false);
				return true;
			} else {
				reportStep("Text mismatch - Expected: '" + expectedText +
						"', Actual: '" + actualText + "'", "warning", false);
				return false;
			}
		} catch (Exception e) {
			reportStep("Text verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	// ========================================================================
	// ALERT HANDLING - IMPROVED
	// ========================================================================

	@Override
	public void acceptAlert() {
		try {
			Alert alert = waitFor(ExpectedConditions.alertIsPresent(),
					WaitUtils.getShortWaitTime(), "Alert not present");
			if (alert != null) {
				String text = alert.getText();
				alert.accept();
				reportStep("Accepted alert: '" + text + "'", "pass", false);
			} else {
				reportStep("No alert present to accept", "warning", false);
			}
		} catch (NoAlertPresentException e) {
			reportStep("No alert present", "warning", false);
		}
	}

	@Override
	public void dismissAlert() {
		try {
			Alert alert = waitFor(ExpectedConditions.alertIsPresent(),
					WaitUtils.getShortWaitTime(), "Alert not present");
			if (alert != null) {
				String text = alert.getText();
				alert.dismiss();
				reportStep("Dismissed alert: '" + text + "'", "pass", false);
			} else {
				reportStep("No alert present to dismiss", "warning", false);
			}
		} catch (NoAlertPresentException e) {
			reportStep("No alert present", "warning", false);
		}
	}

	// ========================================================================
	// SCREENSHOT - IMPROVED
	// ========================================================================

	@Override
	public long takeSnap() {
		long number = (long) Math.floor(Math.random() * 900000000L) + 10000000L;
		try {
			File screenshot = getDriver().getScreenshotAs(OutputType.FILE);
			File destination = new File("./" + Reporter.folderName + "/images/" + number + ".jpg");
			FileUtils.copyFile(screenshot, destination);
		} catch (WebDriverException e) {
			reportStep("Screenshot failed - browser closed: " + e.getMessage(), "warning", false);
		} catch (IOException e) {
			reportStep("Screenshot save failed: " + e.getMessage(), "warning", false);
		}
		return number;
	}

	// ========================================================================
	// MINIMAL PAUSE
	// ========================================================================

	/**
	 * Minimal pause - use only when absolutely necessary
	 * 
	 * @param timeoutMs timeout in milliseconds
	 */
	public void pause(int timeoutMs) {
		try {
			Thread.sleep(timeoutMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			reportStep("Thread interrupted during pause", "warning", false);
		}
	}

	// ========================================================================
	// REMAINING METHODS (keeping existing implementations)
	// ========================================================================

	// Keep all other existing methods as they are...
	// (getAttribute, moveToElement, dragAndDrop, etc.)

	@Override
	public String getAttribute(WebElement ele, String attributeValue) {
		try {
			return ele.getAttribute(attributeValue);
		} catch (WebDriverException e) {
			reportStep("Attribute fetch failed: " + e.getMessage(), "warning", false);
			return "";
		}
	}

	@Override
	public void setSliderValueJS(WebElement slider, String value) {

		String script = "arguments[0].setAttribute('aria-valuenow', arguments[1]);" +
				"arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
				"arguments[0].dispatchEvent(new Event('change', { bubbles: true }));";
		try {
			getDriver().executeScript(script, slider, value);
		} catch (Exception e) {
			reportStep("Mouse Slide failed: " + e.getMessage(), "fail", true);
		}

	}

	@Override
	public void moveToElement(WebElement ele) {
		try {
			act = new Actions(getDriver());
			WebElement visibleElement = waitForVisibility(ele);
			if (visibleElement != null) {
				act.moveToElement(visibleElement).perform();
			}
		} catch (Exception e) {
			reportStep("Mouse hover failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void close() {
		try {
			getDriver().close();
			reportStep("Browser window closed", "info", false);
		} catch (Exception e) {
			reportStep("Failed to close browser: " + e.getMessage(), "warning", false);
		}
	}

	@Override
	public void quit() {
		try {
			getDriver().quit();
			reportStep("Browser session ended", "info", false);
		} catch (Exception e) {
			reportStep("Failed to quit browser: " + e.getMessage(), "warning", false);
		}
	}

	// Keep remaining interface implementations...
	@Override
	public void append(WebElement ele, String data) {
		try {
			ele.sendKeys(data);
		} catch (WebDriverException e) {
			reportStep("Append failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void clear(WebElement ele) {
		try {
			waitForVisibility(ele).clear();
		} catch (ElementNotInteractableException e) {
			reportStep("Clear failed - element not interactable", "fail", true);
		} catch (Exception e) {
			reportStep("Clear failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public String getElementText(WebElement ele) {
		try {
			String text = waitForVisibility(ele).getText();
			reportStep("Retrieved text: " + text, "info", false);
			return text;
		} catch (Exception e) {
			reportStep("Text retrieval failed: " + e.getMessage(), "fail", false);
			return "";
		}
	}

	@Override
	public String getBackgroundColor(WebElement ele) {
		try {
			String cssValue = ele.getCssValue("color");
			reportStep("Background color: " + cssValue, "info", false);
			return cssValue;
		} catch (Exception e) {
			reportStep("Color retrieval failed: " + e.getMessage(), "fail", false);
			return "";
		}
	}

	@Override
	public boolean isElementVisuallyVisible(WebElement element) {
		String visibility = element.getCssValue("visibility");
		return visibility.equals("visible");
	}

	@Override
	public String getTypedText(WebElement ele) {
		try {
			String value = ele.getAttribute("value");
			reportStep("Retrieved value: " + value, "info", false);
			return value;
		} catch (Exception e) {
			reportStep("Value retrieval failed: " + e.getMessage(), "fail", false);
			return "";
		}
	}

	@Override
	public void selectDropDownUsingText(WebElement ele, String value) {
		try {
			Select sel = new Select(waitForVisibility(ele));
			sel.selectByVisibleText(value);
			reportStep("Selected dropdown option: " + value, "info", false);
		} catch (Exception e) {
			reportStep("Dropdown selection failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void selectDropDownUsingIndex(WebElement ele, int index) {
		try {
			Select sel = new Select(waitForVisibility(ele));
			sel.selectByIndex(index);
			reportStep("Selected dropdown index: " + index, "info", false);
		} catch (Exception e) {
			reportStep("Dropdown selection failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void selectDropDownUsingValue(WebElement ele, String value) {
		try {
			Select sel = new Select(waitForVisibility(ele));
			sel.selectByValue(value);
			reportStep("Selected dropdown value: " + value, "info", false);
		} catch (Exception e) {
			reportStep("Dropdown selection failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public boolean verifyPartialText(WebElement ele, String expectedText) {
		try {
			String actualText = waitForVisibility(ele).getText();
			if (actualText.contains(expectedText)) {
				reportStep("Text contains: '" + expectedText + "'", "pass", false);
				return true;
			} else {
				reportStep("Text doesn't contain: '" + expectedText + "'", "warning", false);
				return false;
			}
		} catch (Exception e) {
			reportStep("Partial text verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	@Override
	public boolean verifyExactAttribute(WebElement ele, String attribute, String value) {
		try {
			String actualValue = ele.getAttribute(attribute);
			if (actualValue != null && actualValue.equals(value)) {
				reportStep("Attribute '" + attribute + "' matches: " + value, "pass", false);
				return true;
			} else {
				reportStep("Attribute mismatch - Expected: " + value + ", Actual: " + actualValue,
						"warning", false);
				return false;
			}
		} catch (Exception e) {
			reportStep("Attribute verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	@Override
	public void verifyPartialAttribute(WebElement ele, String attribute, String value) {
		try {
			String actualValue = ele.getAttribute(attribute);
			if (actualValue != null && actualValue.contains(value)) {
				reportStep("Attribute '" + attribute + "' contains: " + value, "pass", false);
			} else {
				reportStep("Attribute doesn't contain: " + value, "warning", false);
			}
		} catch (Exception e) {
			reportStep("Partial attribute verification failed: " + e.getMessage(), "fail", false);
		}
	}

	@Override
	public boolean verifyDisappeared(WebElement ele) {
		try {
			Boolean result = waitFor(ExpectedConditions.invisibilityOf(ele),
					WaitUtils.getShortWaitTime(), "Element didn't disappear");
			if (result != null && result) {
				reportStep("Element disappeared as expected", "pass", false);
				return true;
			}
		} catch (Exception e) {
			reportStep("Element still visible", "warning", false);
		}
		return false;
	}

	@Override
	public boolean verifyEnabled(WebElement ele) {
		try {
			if (ele.isEnabled()) {
				reportStep("Element is enabled", "pass", false);
				return true;
			} else {
				reportStep("Element is not enabled", "warning", false);
				return false;
			}
		} catch (Exception e) {
			reportStep("Enable check failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	@Override
	public boolean verifySelected(WebElement ele) {
		try {
			if (ele.isSelected()) {
				reportStep("Element is selected", "pass", false);
				return true;
			} else {
				reportStep("Element is not selected", "warning", false);
				return false;
			}
		} catch (WebDriverException e) {
			reportStep("Selection check failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	@Override
	public WebElement locateElement(Locators locatorType, String value) {
		try {
			switch (locatorType) {
			case CLASS_NAME:
				return getDriver().findElement(By.className(value));
			case CSS:
				return getDriver().findElement(By.cssSelector(value));
			case ID:
				return getDriver().findElement(By.id(value));
			case LINK_TEXT:
				return getDriver().findElement(By.linkText(value));
			case NAME:
				return getDriver().findElement(By.name(value));
			case PARTIAL_LINKTEXT:
				return getDriver().findElement(By.partialLinkText(value));
			case TAGNAME:
				return getDriver().findElement(By.tagName(value));
			case XPATH:
				return getDriver().findElement(By.xpath(value));
			default:
				reportStep("Invalid locator type: " + locatorType, "fail", false);
				return null;
			}
		} catch (NoSuchElementException e) {
			reportStep("Element not found - " + locatorType + ": " + value, "fail", true);
			return null;
		}
	}

	@Override
	public WebElement locateElement(String value) {
		try {
			return getDriver().findElement(By.id(value));
		} catch (NoSuchElementException e) {
			reportStep("Element not found with ID: " + value, "fail", true);
			return null;
		}
	}

	@Override
	public WebElement locateElement(Locators locatorType1, String value1, Locators locatorType2, String value2) {
		try {
			WebElement element = null;
			By by1 = getBy(locatorType1, value1);
			if (by1 != null && !getDriver().findElements(by1).isEmpty()) {
				element = getDriver().findElement(by1);
			} else {
				By by2 = getBy(locatorType2, value2);
				if (by2 != null && !getDriver().findElements(by2).isEmpty()) {
					element = getDriver().findElement(by2);
				} else {
					reportStep("Element not found with either locator - " + locatorType1 + ": " + value1 + " OR "
							+ locatorType2 + ": " + value2, "fail", true);
				}
			}
			return element;
		} catch (Exception e) {
			reportStep("Error locating element with multiple locators: " + e.getMessage(), "fail", true);
			return null;
		}
	}

	private By getBy(Locators locatorType, String value) {
		switch (locatorType) {
		case CLASS_NAME:
			return By.className(value);
		case CSS:
			return By.cssSelector(value);
		case ID:
			return By.id(value);
		case LINK_TEXT:
			return By.linkText(value);
		case NAME:
			return By.name(value);
		case PARTIAL_LINKTEXT:
			return By.partialLinkText(value);
		case TAGNAME:
			return By.tagName(value);
		case XPATH:
			return By.xpath(value);
		default:
			return null;
		}
	}

	@Override
	public List<WebElement> locateElements(Locators type, String value) {
		try {
			switch (type) {
			case CLASS_NAME:
				return getDriver().findElements(By.className(value));
			case CSS:
				return getDriver().findElements(By.cssSelector(value));
			case ID:
				return getDriver().findElements(By.id(value));
			case LINK_TEXT:
				return getDriver().findElements(By.linkText(value));
			case NAME:
				return getDriver().findElements(By.name(value));
			case PARTIAL_LINKTEXT:
				return getDriver().findElements(By.partialLinkText(value));
			case TAGNAME:
				return getDriver().findElements(By.tagName(value));
			case XPATH:
				return getDriver().findElements(By.xpath(value));
			default:
				reportStep("Invalid locator type: " + type, "fail", false);
				return new ArrayList<>();
			}
		} catch (NoSuchElementException e) {
			reportStep("Elements not found - " + type + ": " + value, "warning", false);
			return new ArrayList<>();
		}
	}

	@Override
	public void switchToAlert() {
		try {
			waitFor(ExpectedConditions.alertIsPresent(), WaitUtils.getShortWaitTime(), "Alert not present");
			getDriver().switchTo().alert();
			reportStep("Switched to alert", "info", false);
		} catch (NoAlertPresentException e) {
			reportStep("No alert present to switch", "warning", false);
		}
	}

	@Override
	public String getAlertText() {
		try {
			Alert alert = waitFor(ExpectedConditions.alertIsPresent(), WaitUtils.getShortWaitTime(),
					"Alert not present");
			if (alert != null) {
				String text = alert.getText();
				reportStep("Alert text: " + text, "pass", false);
				return text;
			}
		} catch (NoAlertPresentException e) {
			reportStep("No alert present", "warning", false);
		}
		return "";
	}

	@Override
	public void typeAlert(String data) {
		try {
			Alert alert = waitFor(ExpectedConditions.alertIsPresent(), WaitUtils.getShortWaitTime(),
					"Alert not present");
			if (alert != null) {
				alert.sendKeys(data);
				reportStep("Typed in alert: " + data, "info", false);
			}
		} catch (NoAlertPresentException e) {
			reportStep("No alert present to type", "warning", false);
		}
	}

	public String getWindowName() {
		String currWindowName = getDriver().getWindowHandle();
		reportStep("Succesfully got the current window name","pass");
		return currWindowName;

	}

	public List<String> getAllWindowName(){
		List<String> allHandles = null;
		try {
			Set<String> allWindows = getDriver().getWindowHandles();
			allHandles = new ArrayList<>(allWindows);
			if (allHandles.size()>1) {
				reportStep("Mutile Tab is be openned: ","pass");
			} else {
				reportStep("Only one Tab is be openned: ","pass");			}
		} catch (NoSuchWindowException e) {
			reportStep("Window not found" , "fail");
		}
		return allHandles;
	}


	@Override
	public void switchToWindow(int index) {
		try {
			Set<String> allWindows = getDriver().getWindowHandles();
			List<String> allHandles = new ArrayList<>(allWindows);
			if (index < allHandles.size()) {
				getDriver().switchTo().window(allHandles.get(index));
				reportStep("Switched to window index: " + index + " - " + getDriver().getTitle(),
						"info", false);
			} else {
				reportStep("Window index out of bounds: " + index, "fail", false);
			}
		} catch (NoSuchWindowException e) {
			reportStep("Window not found at index: " + index, "fail", false);
		}
	}

	@Override
	public boolean switchToWindowByTitle(String title) {
		try {
			Set<String> allWindows = getDriver().getWindowHandles();
			for (String window : allWindows) {
				getDriver().switchTo().window(window);
				if (getDriver().getTitle().equals(title)) {
					reportStep("Switched to window: " + title, "info", false);
					return true;
				}
			}
			reportStep("Window not found with title: " + title, "warning", false);
		} catch (NoSuchWindowException e) {
			reportStep("Window switch failed: " + e.getMessage(), "fail", false);
		}
		return false;
	}
	
	@Override
	public boolean switchToWindowByUrl(String url) {
		try {
			Set<String> allWindows = getDriver().getWindowHandles();
			for (String window : allWindows) {
				getDriver().switchTo().window(window);
				if (getDriver().getCurrentUrl().contains(url)) {
					reportStep("Switched to window: " + url, "info", false);
					return true;
				}
			}
			reportStep("Window not found with title: " + url, "warning", false);
		} catch (NoSuchWindowException e) {
			reportStep("Window switch failed: " + e.getMessage(), "fail", false);
		}
		return false;
	}
	

	@Override
	public void switchToFrame(int index) {
		try {
			waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(index),
					WaitUtils.getShortWaitTime(), "Frame not available at index: " + index);
			reportStep("Switched to frame index: " + index, "info", false);
		} catch (NoSuchFrameException e) {
			reportStep("Frame not found at index: " + index, "fail", false);
		}
	}

	@Override
	public void switchToFrame(WebElement ele) {
		try {
			waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(ele),
					WaitUtils.getShortWaitTime(), "Frame not available");
			reportStep("Switched to frame element", "info", false);
		} catch (NoSuchFrameException e) {
			reportStep("Frame not found", "fail", false);
		}
	}

	@Override
	public void switchToFrame(String idOrName) {
		try {
			waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(idOrName),
					WaitUtils.getShortWaitTime(), "Frame not available: " + idOrName);
			reportStep("Switched to frame: " + idOrName, "info", false);
		} catch (NoSuchFrameException e) {
			reportStep("Frame not found: " + idOrName, "fail", false);
		}
	}

	@Override
	public void defaultContent() {
		try {
			getDriver().switchTo().defaultContent();
			reportStep("Switched to default content", "info", false);
		} catch (Exception e) {
			reportStep("Failed to switch to default content: " + e.getMessage(), "fail", false);
		}
	}

	@Override
	public boolean verifyUrl(String url) {
		try {
			String currentUrl = getDriver().getCurrentUrl();
			if (currentUrl.equals(url)) {
				reportStep("URL matched: " + url, "pass", false);
				return true;
			} else {
				reportStep("URL mismatch - Expected: " + url + ", Actual: " + currentUrl,
						"warning", false);
				return false;
			}
		} catch (Exception e) {
			reportStep("URL verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}
	@Override
	public boolean verifyPartialUrl(String url) {
		try {
			String currentUrl = getDriver().getCurrentUrl();
			if (currentUrl.contains(url)) {
				reportStep("URL matched: " + currentUrl, "pass", false);
				return true;
			} else {
				reportStep("URL mismatch - Expected: " + url + ", Actual: " + currentUrl,
						"warning", false);
				return false;
			}
		} catch (Exception e) {
			reportStep("URL verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	@Override
	public boolean verifyTitle(String title) {
		try {
			String currentTitle = getDriver().getTitle();
			if (currentTitle.equals(title)) {
				reportStep("Title matched: " + title, "pass", false);
				return true;
			} else {
				reportStep("Title mismatch - Expected: " + title + ", Actual: " + currentTitle,
						"warning", false);
				return false;
			}
		} catch (Exception e) {
			reportStep("Title verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	@Override
	public long takeSnap(String name) {
		long number = (long) Math.floor(Math.random() * 900000000L) + 10000000L;
		try {
			File screenshot = getDriver().getScreenshotAs(OutputType.FILE);
			File destination = new File("./" + Reporter.folderName + "/images/" + name + "_" + number + ".jpg");
			FileUtils.copyFile(screenshot, destination);
		} catch (WebDriverException e) {
			reportStep("Screenshot failed: " + e.getMessage(), "warning", false);
		} catch (IOException e) {
			reportStep("Screenshot save failed: " + e.getMessage(), "warning", false);
		}
		return number;
	}

	public void waitApiToLoad() {
		try {
			fluentWaitFor(driver -> {
				Long activeRequests = (Long) getDriver().executeScript(
						"return window.performance.getEntriesByType('resource').length;");
				return activeRequests != null && activeRequests > 0; // customize logic
			}, 30, 500); // 30 seconds timeout, 500ms polling interval
			reportStep("API resources loaded successfully", "info", false);
		} catch (Exception e) {
			reportStep("Wait for API to load failed: " + e.getMessage(), "warning", false);
		}
	}
	
	@Override
	public void executeTheScript(String js, WebElement ele) {
		try {
			getDriver().executeScript(js, ele);
		} catch (Exception e) {
			reportStep("JavaScript execution failed: " + e.getMessage(), "fail", false);
		}
	}

	@Override
	public Object executeJs(String script, Object... args) {
		try {
			return getDriver().executeScript(script, args);
		} catch (Exception e) {
			reportStep("JavaScript execution failed: " + e.getMessage(), "fail", false);
			return null;
		}
	}

	// Additional methods from original file
	@Override
	public void dragAndDrop(WebElement eleSource, WebElement eleTarget) {
		try {
			act = new Actions(getDriver());
			act.dragAndDrop(waitForVisibility(eleSource), waitForVisibility(eleTarget)).perform();
			reportStep("Drag and drop completed", "info", false);
		} catch (Exception e) {
			reportStep("Drag and drop failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void contextClick(WebElement ele) {
		try {
			act = new Actions(getDriver());
			act.contextClick(waitForClickable(ele)).perform();
			reportStep("Right click performed", "info", false);
		} catch (Exception e) {
			reportStep("Right click failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void hoverAndClick(WebElement ele) {
		try {
			act = new Actions(getDriver());
			WebElement clickableElement = waitForClickable(ele);
			if (clickableElement != null) {
				act.moveToElement(clickableElement).pause(Duration.ofMillis(500)).click().perform();
				reportStep("Hover and click completed", "info", false);
			}
		} catch (Exception e) {
			reportStep("Hover and click failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void doubleClick(WebElement ele) {
		try {
			act = new Actions(getDriver());
			act.doubleClick(waitForClickable(ele)).perform();
			reportStep("Double click performed", "info", false);
		} catch (Exception e) {
			reportStep("Double click failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void chooseDate(WebElement ele, String data) {
		try {
			getDriver().executeScript("arguments[0].setAttribute('value', '" + data + "')", ele);
			reportStep("Date set: " + data, "pass", false);
		} catch (Exception e) {
			reportStep("Date selection failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void fileUpload(WebElement ele, String filePath) {
		try {
			hoverAndClick(ele);
			pause(1000);

			StringSelection stringSelection = new StringSelection(filePath);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);

			Robot robot = new Robot();
			robot.keyPress(KeyEvent.VK_CONTROL);
			robot.keyPress(KeyEvent.VK_V);
			robot.keyRelease(KeyEvent.VK_V);
			robot.keyRelease(KeyEvent.VK_CONTROL);

			pause(1000);

			robot.keyPress(KeyEvent.VK_ENTER);
			robot.keyRelease(KeyEvent.VK_ENTER);

			reportStep("File uploaded: " + filePath, "pass", false);
		} catch (Exception e) {
			reportStep("File upload failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void click(Locators locatorType, String value) {
		WebElement ele = locateElement(locatorType, value);
		if (ele != null) {
			click(ele);
		}
	}

	@Override
	public void clickWithNoSnap(WebElement ele) {
		try {
			WebElement clickable = waitForClickable(ele);
			if (clickable != null) {
				clickable.click();
			}
		} catch (Exception e) {
			reportStep("Click failed: " + e.getMessage(), "fail", false);
		}
	}

	@Override
	public void type(WebElement ele, String data) {
		try {
			waitForVisibility(ele).sendKeys(data);
		} catch (ElementNotInteractableException e) {
			reportStep("Element not interactable", "fail", true);
		} catch (Exception e) {
			reportStep("Type failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void switchToFrameUsingXPath(String xpath) {
		try {
			WebElement frameElement = locateElement(Locators.XPATH, xpath);
			if (frameElement != null) {
				switchToFrame(frameElement);
			}
		} catch (NoSuchFrameException e) {
			reportStep("Frame not found with XPath: " + xpath, "fail", false);
		}
	}

	@Override
	public void fileUploadWithJs(WebElement ele, String filePath) {
		try {
			clickWithJs(ele);
			pause(1000);

			StringSelection stringSelection = new StringSelection(filePath);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);

			Robot robot = new Robot();
			robot.keyPress(KeyEvent.VK_CONTROL);
			robot.keyPress(KeyEvent.VK_V);
			robot.keyRelease(KeyEvent.VK_V);
			robot.keyRelease(KeyEvent.VK_CONTROL);

			pause(1000);

			robot.keyPress(KeyEvent.VK_ENTER);
			robot.keyRelease(KeyEvent.VK_ENTER);

			reportStep("File uploaded with JS: " + filePath, "pass", false);
		} catch (Exception e) {
			reportStep("File upload failed: " + e.getMessage(), "fail", true);
		}
	}

	@Override
	public void scroll(WebElement ele) {
		getDriver().executeScript("arguments[0].scrollIntoView(true);", ele);

	}

	@Override
	public void startApp(WebDriverPoolFactory pool, BrowserType browserType, boolean headless, String url) {
		try {
			RemoteWebDriver driver = pool.acquire(browserType, null);
			driver.manage().window().maximize();
			driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(WaitUtils.getDefaultWaitTime()));
			if (url != null && !url.isEmpty()) {
				driver.get(url);
			}
			reportStep("The browser: " + browserType + " launched successfully", "pass", false);
		} catch (Exception e) {
			reportStep("The browser: " + browserType + " could not be launched", "fail", true);
		}
	}

	@Override
	public void startApp(WebDriverPoolFactory pool, String url, boolean headless) {
		try {
			RemoteWebDriver driver = pool.acquire(BrowserType.CHROME, null);
			driver.manage().window().maximize();
			driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(WaitUtils.getDefaultWaitTime()));
			if (url != null && !url.isEmpty()) {
				driver.get(url);
			}
			reportStep("The browser: CHROME launched successfully", "pass", false);
		} catch (Exception e) {
			reportStep("The browser: CHROME could not be launched", "fail", true);
		}
	}
}
