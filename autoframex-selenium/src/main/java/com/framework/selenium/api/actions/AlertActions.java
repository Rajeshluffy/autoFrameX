package com.framework.selenium.api.actions;

import com.framework.exception.TestStepFailedException;
import com.framework.utils.StepReporter;
import java.util.function.Supplier;

import org.openqa.selenium.Alert;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.framework.utils.WaitUtils;

/** Alert-handling operations — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class AlertActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final StepReporter reporter;
	private final ElementWaiter waitActions;

	public AlertActions(Supplier<RemoteWebDriver> driverSupplier, StepReporter reporter, ElementWaiter waitActions) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
		this.waitActions = waitActions;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	/** Waits for an alert; fails the step (with a screenshot) if none appears in the short wait time. */
	private Alert requireAlert(String purpose) {
		try {
			Alert alert = waitActions.waitFor(ExpectedConditions.alertIsPresent(),
					WaitUtils.getShortWaitTime(), "Alert not present");
			return ElementSupport.orFail(reporter, alert, "No alert present to " + purpose, true);
		} catch (NoAlertPresentException e) {
			reporter.reportStep("No alert present to " + purpose, "fail", true);
			throw new TestStepFailedException("Test failed: No alert present to " + purpose);
		}
	}

	public void acceptAlert() {
		Alert alert = requireAlert("accept");
		String text = alert.getText();
		alert.accept();
		reporter.reportStep("Accepted alert: '" + text + "'", "pass", false);
	}

	public void dismissAlert() {
		Alert alert = requireAlert("dismiss");
		String text = alert.getText();
		alert.dismiss();
		reporter.reportStep("Dismissed alert: '" + text + "'", "pass", false);
	}

	public void switchToAlert() {
		requireAlert("switch to");
		driver().switchTo().alert();
		reporter.reportStep("Switched to alert", "info", false);
	}

	public String getAlertText() {
		String text = requireAlert("read").getText();
		reporter.reportStep("Alert text: " + text, "pass", false);
		return text;
	}

	public void typeAlert(String data) {
		requireAlert("type into").sendKeys(data);
		reporter.reportStep("Typed in alert: " + data, "info", false);
	}
}
