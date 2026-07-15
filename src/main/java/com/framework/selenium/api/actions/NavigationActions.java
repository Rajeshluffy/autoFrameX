package com.framework.selenium.api.actions;

import java.util.function.Supplier;

import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.utils.Reporter;

/** Page navigation and URL/title verification — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class NavigationActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final Reporter reporter;
	private final WaitActions waitActions;

	public NavigationActions(Supplier<RemoteWebDriver> driverSupplier, Reporter reporter, WaitActions waitActions) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
		this.waitActions = waitActions;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	public void refresh() {
		String script = "return document.readyState";
		try {
			driver().navigate().refresh();
			waitActions.getWait(10).until(webDriver -> driver().executeScript(script).equals("complete"));
		} catch (Exception e) {
			reporter.reportStep("JavaScript click failed: " + e.getMessage(), "fail", true);
		}
	}

	public boolean verifyUrl(String url) {
		try {
			String currentUrl = driver().getCurrentUrl();
			if (currentUrl.equals(url)) {
				reporter.reportStep("URL matched: " + url, "pass", false);
				return true;
			} else {
				reporter.reportStep("URL mismatch - Expected: " + url + ", Actual: " + currentUrl,
						"warning", false);
				return false;
			}
		} catch (Exception e) {
			reporter.reportStep("URL verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	public boolean verifyPartialUrl(String url) {
		try {
			String currentUrl = driver().getCurrentUrl();
			if (currentUrl.contains(url)) {
				reporter.reportStep("URL matched: " + currentUrl, "pass", false);
				return true;
			} else {
				reporter.reportStep("URL mismatch - Expected: " + url + ", Actual: " + currentUrl,
						"warning", false);
				return false;
			}
		} catch (Exception e) {
			reporter.reportStep("URL verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}

	public boolean verifyTitle(String title) {
		try {
			String currentTitle = driver().getTitle();
			if (currentTitle.equals(title)) {
				reporter.reportStep("Title matched: " + title, "pass", false);
				return true;
			} else {
				reporter.reportStep("Title mismatch - Expected: " + title + ", Actual: " + currentTitle,
						"warning", false);
				return false;
			}
		} catch (Exception e) {
			reporter.reportStep("Title verification failed: " + e.getMessage(), "fail", false);
			return false;
		}
	}
}
