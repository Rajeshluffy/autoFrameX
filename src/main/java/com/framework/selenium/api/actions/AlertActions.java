package com.framework.selenium.api.actions;

import java.util.function.Supplier;

import org.openqa.selenium.Alert;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.framework.utils.Reporter;
import com.framework.utils.WaitUtils;

/** Alert-handling operations — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class AlertActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final Reporter reporter;
	private final WaitActions waitActions;

	public AlertActions(Supplier<RemoteWebDriver> driverSupplier, Reporter reporter, WaitActions waitActions) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
		this.waitActions = waitActions;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	public void acceptAlert() {
		try {
			Alert alert = waitActions.waitFor(ExpectedConditions.alertIsPresent(),
					WaitUtils.getShortWaitTime(), "Alert not present");
			if (alert != null) {
				String text = alert.getText();
				alert.accept();
				reporter.reportStep("Accepted alert: '" + text + "'", "pass", false);
			} else {
				reporter.reportStep("No alert present to accept", "warning", false);
			}
		} catch (NoAlertPresentException e) {
			reporter.reportStep("No alert present", "warning", false);
		}
	}

	public void dismissAlert() {
		try {
			Alert alert = waitActions.waitFor(ExpectedConditions.alertIsPresent(),
					WaitUtils.getShortWaitTime(), "Alert not present");
			if (alert != null) {
				String text = alert.getText();
				alert.dismiss();
				reporter.reportStep("Dismissed alert: '" + text + "'", "pass", false);
			} else {
				reporter.reportStep("No alert present to dismiss", "warning", false);
			}
		} catch (NoAlertPresentException e) {
			reporter.reportStep("No alert present", "warning", false);
		}
	}

	public void switchToAlert() {
		try {
			waitActions.waitFor(ExpectedConditions.alertIsPresent(), WaitUtils.getShortWaitTime(), "Alert not present");
			driver().switchTo().alert();
			reporter.reportStep("Switched to alert", "info", false);
		} catch (NoAlertPresentException e) {
			reporter.reportStep("No alert present to switch", "warning", false);
		}
	}

	public String getAlertText() {
		try {
			Alert alert = waitActions.waitFor(ExpectedConditions.alertIsPresent(), WaitUtils.getShortWaitTime(),
					"Alert not present");
			if (alert != null) {
				String text = alert.getText();
				reporter.reportStep("Alert text: " + text, "pass", false);
				return text;
			}
		} catch (NoAlertPresentException e) {
			reporter.reportStep("No alert present", "warning", false);
		}
		return "";
	}

	public void typeAlert(String data) {
		try {
			Alert alert = waitActions.waitFor(ExpectedConditions.alertIsPresent(), WaitUtils.getShortWaitTime(),
					"Alert not present");
			if (alert != null) {
				alert.sendKeys(data);
				reporter.reportStep("Typed in alert: " + data, "info", false);
			}
		} catch (NoAlertPresentException e) {
			reporter.reportStep("No alert present to type", "warning", false);
		}
	}
}
