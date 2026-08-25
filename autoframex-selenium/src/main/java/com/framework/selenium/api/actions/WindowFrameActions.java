package com.framework.selenium.api.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.framework.selenium.api.design.Locators;
import com.framework.utils.Reporter;
import com.framework.utils.WaitUtils;

import design.patterns.object.pool.DriverPoolManager;

/** Window/frame/session management — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class WindowFrameActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final Reporter reporter;
	private final WaitActions waitActions;
	private final LocatorActions locatorActions;

	public WindowFrameActions(Supplier<RemoteWebDriver> driverSupplier, Reporter reporter,
			WaitActions waitActions, LocatorActions locatorActions) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
		this.waitActions = waitActions;
		this.locatorActions = locatorActions;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	public String getWindowName() {
		String currWindowName = driver().getWindowHandle();
		reporter.reportStep("Succesfully got the current window name", "pass");
		return currWindowName;
	}

	public List<String> getAllWindowName() {
		List<String> allHandles = null;
		try {
			Set<String> allWindows = driver().getWindowHandles();
			allHandles = new ArrayList<>(allWindows);
			if (allHandles.size() > 1) {
				reporter.reportStep("Mutile Tab is be openned: ", "pass");
			} else {
				reporter.reportStep("Only one Tab is be openned: ", "pass");
			}
		} catch (NoSuchWindowException e) {
			reporter.reportStep("Window not found", "fail");
		}
		return allHandles;
	}

	public void switchToWindow(int index) {
		try {
			Set<String> allWindows = driver().getWindowHandles();
			List<String> allHandles = new ArrayList<>(allWindows);
			if (index < allHandles.size()) {
				driver().switchTo().window(allHandles.get(index));
				reporter.reportStep("Switched to window index: " + index + " - " + driver().getTitle(),
						"info", false);
			} else {
				reporter.reportStep("Window index out of bounds: " + index, "fail", false);
			}
		} catch (NoSuchWindowException e) {
			reporter.reportStep("Window not found at index: " + index, "fail", false);
		}
	}

	public boolean switchToWindowByTitle(String title) {
		try {
			Set<String> allWindows = driver().getWindowHandles();
			for (String window : allWindows) {
				driver().switchTo().window(window);
				if (driver().getTitle().equals(title)) {
					reporter.reportStep("Switched to window: " + title, "info", false);
					return true;
				}
			}
			reporter.reportStep("Window not found with title: " + title, "warning", false);
		} catch (NoSuchWindowException e) {
			reporter.reportStep("Window switch failed: " + e.getMessage(), "fail", false);
		}
		return false;
	}

	public boolean switchToWindowByUrl(String url) {
		try {
			Set<String> allWindows = driver().getWindowHandles();
			for (String window : allWindows) {
				driver().switchTo().window(window);
				if (driver().getCurrentUrl().equals(url)) {
					reporter.reportStep("Switched to window: " + url, "info", false);
					return true;
				}
			}
			reporter.reportStep("Window not found with title: " + url, "warning", false);
		} catch (NoSuchWindowException e) {
			reporter.reportStep("Window switch failed: " + e.getMessage(), "fail", false);
		}
		return false;
	}

	public void switchToFrame(int index) {
		try {
			waitActions.waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(index),
					WaitUtils.getShortWaitTime(), "Frame not available at index: " + index);
			reporter.reportStep("Switched to frame index: " + index, "info", false);
		} catch (NoSuchFrameException e) {
			reporter.reportStep("Frame not found at index: " + index, "fail", false);
		}
	}

	public void switchToFrame(WebElement ele) {
		try {
			waitActions.waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(ele),
					WaitUtils.getShortWaitTime(), "Frame not available");
			reporter.reportStep("Switched to frame element", "info", false);
		} catch (NoSuchFrameException e) {
			reporter.reportStep("Frame not found", "fail", false);
		}
	}

	public void switchToFrame(String idOrName) {
		try {
			waitActions.waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(idOrName),
					WaitUtils.getShortWaitTime(), "Frame not available: " + idOrName);
			reporter.reportStep("Switched to frame: " + idOrName, "info", false);
		} catch (NoSuchFrameException e) {
			reporter.reportStep("Frame not found: " + idOrName, "fail", false);
		}
	}

	public void switchToFrameUsingXPath(String xpath) {
		try {
			WebElement frameElement = locatorActions.locateElement(Locators.XPATH, xpath);
			if (frameElement != null) {
				switchToFrame(frameElement);
			}
		} catch (NoSuchFrameException e) {
			reporter.reportStep("Frame not found with XPath: " + xpath, "fail", false);
		}
	}

	public void defaultContent() {
		try {
			driver().switchTo().defaultContent();
			reporter.reportStep("Switched to default content", "info", false);
		} catch (Exception e) {
			reporter.reportStep("Failed to switch to default content: " + e.getMessage(), "fail", false);
		}
	}

	public void close() {
		try {
			driver().close();
			reporter.reportStep("Browser window closed", "info", false);
		} catch (Exception e) {
			reporter.reportStep("Failed to close browser: " + e.getMessage(), "warning", false);
		}
	}

	/**
	 * Ends the current WebDriver session.
	 *
	 * <p>Routed through {@link DriverPoolManager#destroy} rather than calling
	 * {@code driver().quit()} directly (framework-3.1 architecture review,
	 * finding F4): a direct {@code quit()} killed the session but left the
	 * pool's {@code activeDrivers} bookkeeping unaware the driver had died —
	 * on a passed test, {@code teardownDriver()} would then re-queue the dead
	 * driver as healthy, handing the next borrower a zombie session.
	 * {@code destroy()} removes the driver from the pool's active set,
	 * decrements its live-count, and clears this thread's driver context, so
	 * a subsequent {@code teardownDriver()} correctly finds nothing to release.
	 */
	public void quit() {
		try {
			DriverPoolManager.getInstance().destroy(driver());
			reporter.reportStep("Browser session ended", "info", false);
		} catch (Exception e) {
			reporter.reportStep("Failed to quit browser: " + e.getMessage(), "warning", false);
		}
	}
}
