package com.framework.selenium.api.actions;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.framework.utils.Reporter;
import com.framework.utils.WaitUtils;

/**
 * Explicit/fluent wait operations and page/network readiness checks —
 * extracted from {@code SeleniumBase} as part of the TD-07 composition
 * refactor. Depends on nothing but a driver supplier and a {@link Reporter}
 * for logging, so it can be composed standalone or reused by other action
 * classes that need to wait for an element first (e.g. {@code ClickActions}).
 */
public class WaitActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final Reporter reporter;

	public WaitActions(Supplier<RemoteWebDriver> driverSupplier, Reporter reporter) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	public WebDriverWait getWait(int timeoutInSeconds) {
		return new WebDriverWait(driver(), Duration.ofSeconds(timeoutInSeconds));
	}

	public <T> T waitFor(ExpectedCondition<T> condition, String errorMessage) {
		return WaitUtils.waitFor(driver(), reporter, condition, errorMessage);
	}

	public <T> T waitFor(ExpectedCondition<T> condition, int timeoutInSeconds, String errorMessage) {
		return WaitUtils.waitFor(driver(), reporter, condition, timeoutInSeconds, errorMessage);
	}

	public <T> T fluentWaitFor(Function<RemoteWebDriver, T> condition) {
		return WaitUtils.fluentWaitFor(driver(), condition);
	}

	public <T> T fluentWaitFor(Function<RemoteWebDriver, T> condition, int timeoutInSeconds, int pollingIntervalMs) {
		return WaitUtils.fluentWaitFor(driver(), condition, timeoutInSeconds, pollingIntervalMs);
	}

	public void setImplicitWait(int seconds) {
		WaitUtils.setImplicitWait(driver(), reporter, seconds);
	}

	public void resetImplicitWait() {
		WaitUtils.resetImplicitWait(driver(), reporter);
	}

	public WebElement waitForClickable(WebElement ele) {
		try {
			return WaitUtils.waitFor(driver(), reporter, ExpectedConditions.elementToBeClickable(ele),
					WaitUtils.getDefaultWaitTime(),
					"Element not clickable: " + ElementSupport.describe(ele));
		} catch (Exception e) {
			reporter.reportStep("Failed to wait for clickable element", "fail", true);
			return null;
		}
	}

	public WebElement waitForVisibility(WebElement element) {
		try {
			return WaitUtils.waitFor(driver(), reporter, ExpectedConditions.visibilityOf(element),
					WaitUtils.getDefaultWaitTime(),
					"Element not visible: " + ElementSupport.describe(element));
		} catch (Exception e) {
			reporter.reportStep("Failed to wait for element visibility", "fail", true);
			return null;
		}
	}

	public void waitForApperance(WebElement element) {
		try {
			WaitUtils.waitFor(driver(), reporter, ExpectedConditions.visibilityOf(element),
					WaitUtils.getDefaultWaitTime(),
					"Element did not appear: " + ElementSupport.describe(element));
		} catch (Exception e) {
			reporter.reportStep("Element did not appear within timeout", "warning", false);
		}
	}

	public void waitForDisapperance(WebElement element) {
		WaitUtils.waitFor(driver(), reporter, ExpectedConditions.invisibilityOf(element),
				WaitUtils.getShortWaitTime(),
				"Element did not disappear: " + ElementSupport.describe(element));
	}

	public void waitForPageToLoad() {
		String script = "return document.readyState";
		try {
			getWait(10).until(webDriver -> driver().executeScript(script).equals("complete"));
		} catch (Exception e) {
			reporter.reportStep("JavaScript click failed: " + e.getMessage(), "fail", true);
		}
	}

	public void waitForSpinnerDisappear() {
		getWait(30).until(ExpectedConditions.invisibilityOfElementLocated(
				By.cssSelector(".loading-spinner")));
	}

	/**
	 * Injects a lightweight JavaScript interceptor once per page to count
	 * in-flight XHR and Fetch requests.
	 *
	 * <p>The guard {@code window.__networkTrackerInjected} makes this safe to
	 * call multiple times on the same page — the monkey-patch is applied only
	 * once and survives across multiple {@link #waitForPageAndApiReady()} calls.</p>
	 */
	private void injectNetworkTracker() {
		String script =
			"if (!window.__networkTrackerInjected) {" +
			"  window.__pendingRequests = 0;" +
			"  window.__networkTrackerInjected = true;" +
			"  var origSend = XMLHttpRequest.prototype.send;" +
			"  XMLHttpRequest.prototype.send = function() {" +
			"    window.__pendingRequests++;" +
			"    this.addEventListener('loadend', function() {" +
			"      window.__pendingRequests = Math.max(0, window.__pendingRequests - 1);" +
			"    });" +
			"    return origSend.apply(this, arguments);" +
			"  };" +
			"  var origFetch = window.fetch;" +
			"  if (origFetch) {" +
			"    window.fetch = function() {" +
			"      window.__pendingRequests++;" +
			"      return origFetch.apply(this, arguments).then(" +
			"        function(r) { window.__pendingRequests = Math.max(0, window.__pendingRequests - 1); return r; }," +
			"        function(e) { window.__pendingRequests = Math.max(0, window.__pendingRequests - 1); throw e; }" +
			"      );" +
			"    };" +
			"  }" +
			"}";
		try {
			driver().executeScript(script);
		} catch (Exception e) {
			reporter.reportStep("Network tracker injection failed: " + e.getMessage(), "warning", false);
		}
	}

	/** Waits up to 30 seconds for DOM ready + no pending XHR/Fetch/jQuery-AJAX. */
	public void waitForPageAndApiReady() {
		waitForPageAndApiReady(30);
	}

	public void waitForPageAndApiReady(int timeoutSeconds) {
		injectNetworkTracker();

		String checkScript =
			"var domReady   = (document.readyState === 'complete');" +
			"var noXhrFetch = (typeof window.__pendingRequests === 'undefined'" +
			"                  || window.__pendingRequests === 0);" +
			"var noJQuery   = (typeof jQuery === 'undefined' || jQuery.active === 0);" +
			"return domReady && noXhrFetch && noJQuery;";

		try {
			fluentWaitFor(driver -> {
				Object result = driver().executeScript(checkScript);
				return result instanceof Boolean && (Boolean) result;
			}, timeoutSeconds, 300);
			reporter.reportStep("Page and all API calls are fully loaded", "info", false);
		} catch (Exception e) {
			reporter.reportStep("Timed out waiting for page/API to be ready: " + e.getMessage(), "warning", false);
		}
	}

	public void waitApiToLoad() {
		try {
			fluentWaitFor(driver -> {
				Long activeRequests = (Long) driver().executeScript(
						"return window.performance.getEntriesByType('resource').length;");
				return activeRequests != null && activeRequests > 0;
			}, 30, 500);
			reporter.reportStep("API resources loaded successfully", "info", false);
		} catch (Exception e) {
			reporter.reportStep("Wait for API to load failed: " + e.getMessage(), "warning", false);
		}
	}

	/** Minimal pause — use only when absolutely necessary. */
	public void pause(int timeoutMs) {
		ElementSupport.pause(reporter, timeoutMs);
	}
}
