package com.framework.selenium.api.actions;

import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import com.framework.utils.PerfClock;
import com.framework.utils.Reporter;
import com.framework.utils.WaitUtils;

/**
 * Element state inspection and verification (text, attributes, visibility,
 * dropdowns) — extracted from {@code SeleniumBase} as part of the TD-07
 * composition refactor. Depends on {@link WaitActions} to wait for
 * visibility before reading state.
 */
public class ElementInspectionActions {

	private final Reporter reporter;
	private final WaitActions waitActions;

	public ElementInspectionActions(Reporter reporter, WaitActions waitActions) {
		this.reporter = reporter;
		this.waitActions = waitActions;
	}

	public String safeGetText(WebElement ele) {
		return ElementSupport.safeGetText(ele);
	}

	public boolean verifyDisplayed(WebElement ele) {
		try {
			long waitStartNanos = PerfClock.start();
			WebElement visibleElement = waitActions.waitForVisibility(ele);
			long waitMs = PerfClock.elapsedMs(waitStartNanos);
			if (visibleElement != null && visibleElement.isDisplayed()) {
				reporter.reportStep(String.format("Element is displayed: %s (render/visibility wait: %dms)",
						ElementSupport.describe(ele), waitMs), "pass", false);
				return true;
			} else {
				reporter.reportStep(String.format("Element not displayed: %s (render/visibility wait: %dms)",
						ElementSupport.describe(ele), waitMs), "warning", false);
				return false;
			}
		} catch (Exception e) {
			reporter.reportStep("Display check failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	public boolean verifyExactText(WebElement ele, String expectedText) {
		try {
			WebElement visible = waitActions.waitForVisibility(ele);
			if (visible == null) {
				reporter.reportStep("Element not visible — cannot verify text: '" + expectedText + "'", "fail", true);
				return false;
			}
			String actualText = visible.getText();
			if (actualText.equals(expectedText)) {
				reporter.reportStep("Text matches: '" + expectedText + "'", "pass", false);
				return true;
			} else {
				reporter.reportStep("Text mismatch - Expected: '" + expectedText +
						"', Actual: '" + actualText + "'", "warning", false);
				return false;
			}
		} catch (Exception e) {
			reporter.reportStep("Text verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	public boolean verifyPartialText(WebElement ele, String expectedText) {
		try {
			String actualText = waitActions.waitForVisibility(ele).getText();
			if (actualText.contains(expectedText)) {
				reporter.reportStep("Text contains: '" + expectedText + "'", "pass", false);
				return true;
			} else {
				reporter.reportStep("Text doesn't contain: '" + expectedText + "'", "warning", false);
				return false;
			}
		} catch (Exception e) {
			reporter.reportStep("Partial text verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	public String getAttribute(WebElement ele, String attributeValue) {
		try {
			return ele.getAttribute(attributeValue);
		} catch (WebDriverException e) {
			reporter.reportStep("Attribute fetch failed: " + e.getMessage(), "warning", false);
			return "";
		}
	}

	public boolean verifyExactAttribute(WebElement ele, String attribute, String value) {
		try {
			String actualValue = ele.getAttribute(attribute);
			if (actualValue != null && actualValue.equals(value)) {
				reporter.reportStep("Attribute '" + attribute + "' matches: " + value, "pass", false);
				return true;
			} else {
				reporter.reportStep("Attribute mismatch - Expected: " + value + ", Actual: " + actualValue,
						"warning", false);
				return false;
			}
		} catch (Exception e) {
			reporter.reportStep("Attribute verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	public void verifyPartialAttribute(WebElement ele, String attribute, String value) {
		try {
			String actualValue = ele.getAttribute(attribute);
			if (actualValue != null && actualValue.contains(value)) {
				reporter.reportStep("Attribute '" + attribute + "' contains: " + value, "pass", false);
			} else {
				reporter.reportStep("Attribute doesn't contain: " + value, "warning", false);
			}
		} catch (Exception e) {
			reporter.reportStep("Partial attribute verification failed: " + e.getMessage(), "fail", false);
		}
	}

	public boolean verifyDisappeared(WebElement ele) {
		try {
			Boolean result = waitActions.waitFor(ExpectedConditions.invisibilityOf(ele),
					WaitUtils.getShortWaitTime(), "Element didn't disappear");
			if (result != null && result) {
				reporter.reportStep("Element disappeared as expected", "pass", false);
				return true;
			}
		} catch (Exception e) {
			reporter.reportStep("Element still visible", "warning", false);
		}
		return false;
	}

	public boolean verifyEnabled(WebElement ele) {
		try {
			if (ele.isEnabled()) {
				reporter.reportStep("Element is enabled", "pass", false);
				return true;
			} else {
				reporter.reportStep("Element is not enabled", "warning", false);
				return false;
			}
		} catch (Exception e) {
			reporter.reportStep("Enable check failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	public boolean verifySelected(WebElement ele) {
		try {
			if (ele.isSelected()) {
				reporter.reportStep("Element is selected", "pass", false);
				return true;
			} else {
				reporter.reportStep("Element is not selected", "warning", false);
				return false;
			}
		} catch (WebDriverException e) {
			reporter.reportStep("Selection check failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	public String getElementText(WebElement ele) {
		try {
			String text = waitActions.waitForVisibility(ele).getText();
			reporter.reportStep("Retrieved text: " + text, "info", false);
			return text;
		} catch (Exception e) {
			reporter.reportStep("Text retrieval failed: " + e.getMessage(), "fail", false);
			return "";
		}
	}

	public String getBackgroundColor(WebElement ele) {
		try {
			String cssValue = ele.getCssValue("color");
			reporter.reportStep("Background color: " + cssValue, "info", false);
			return cssValue;
		} catch (Exception e) {
			reporter.reportStep("Color retrieval failed: " + e.getMessage(), "fail", false);
			return "";
		}
	}

	public boolean isElementVisuallyVisible(WebElement element) {
		String visibility = element.getCssValue("visibility");
		return visibility.equals("visible");
	}

	public String getTypedText(WebElement ele) {
		try {
			String value = ele.getAttribute("value");
			reporter.reportStep("Retrieved value: " + value, "info", false);
			return value;
		} catch (Exception e) {
			reporter.reportStep("Value retrieval failed: " + e.getMessage(), "fail", false);
			return "";
		}
	}

	public void selectDropDownUsingText(WebElement ele, String value) {
		try {
			Select sel = new Select(waitActions.waitForVisibility(ele));
			sel.selectByVisibleText(value);
			reporter.reportStep("Selected dropdown option: " + value, "info", false);
		} catch (Exception e) {
			reporter.reportStep("Dropdown selection failed: " + e.getMessage(), "fail", true);
		}
	}

	public void selectDropDownUsingIndex(WebElement ele, int index) {
		try {
			Select sel = new Select(waitActions.waitForVisibility(ele));
			sel.selectByIndex(index);
			reporter.reportStep("Selected dropdown index: " + index, "info", false);
		} catch (Exception e) {
			reporter.reportStep("Dropdown selection failed: " + e.getMessage(), "fail", true);
		}
	}

	public void selectDropDownUsingValue(WebElement ele, String value) {
		try {
			Select sel = new Select(waitActions.waitForVisibility(ele));
			sel.selectByValue(value);
			reporter.reportStep("Selected dropdown value: " + value, "info", false);
		} catch (Exception e) {
			reporter.reportStep("Dropdown selection failed: " + e.getMessage(), "fail", true);
		}
	}
}
