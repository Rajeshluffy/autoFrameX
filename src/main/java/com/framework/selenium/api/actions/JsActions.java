package com.framework.selenium.api.actions;

import java.util.function.Supplier;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.utils.Reporter;

/** JavaScript-driven element operations — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class JsActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final Reporter reporter;

	public JsActions(Supplier<RemoteWebDriver> driverSupplier, Reporter reporter) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	public void executeTheScript(String js, WebElement ele) {
		try {
			driver().executeScript(js, ele);
		} catch (Exception e) {
			reporter.reportStep("JavaScript execution failed: " + e.getMessage(), "fail", false);
		}
	}

	public Object executeJs(String script, Object... args) {
		try {
			return driver().executeScript(script, args);
		} catch (Exception e) {
			reporter.reportStep("JavaScript execution failed: " + e.getMessage(), "fail", false);
			return null;
		}
	}

	public void setSliderValueJS(WebElement slider, String value) {
		String script = "arguments[0].setAttribute('aria-valuenow', arguments[1]);" +
				"arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
				"arguments[0].dispatchEvent(new Event('change', { bubbles: true }));";
		try {
			driver().executeScript(script, slider, value);
		} catch (Exception e) {
			reporter.reportStep("Mouse Slide failed: " + e.getMessage(), "fail", true);
		}
	}

	public void chooseDate(WebElement ele, String data) {
		try {
			driver().executeScript("arguments[0].setAttribute('value', '" + data + "')", ele);
			reporter.reportStep("Date set: " + data, "pass", false);
		} catch (Exception e) {
			reporter.reportStep("Date selection failed: " + e.getMessage(), "fail", true);
		}
	}

	public void scroll(WebElement ele) {
		driver().executeScript("arguments[0].scrollIntoView(true);", ele);
	}
}
