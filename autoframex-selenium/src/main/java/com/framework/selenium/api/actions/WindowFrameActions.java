package com.framework.selenium.api.actions;

import com.framework.utils.StepReporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.framework.selenium.api.design.Locators;
import com.framework.utils.WaitUtils;

import design.patterns.object.pool.DriverPoolManager;

/** Window/frame/session management — extracted from {@code SeleniumBase} as part of the TD-07 composition refactor. */
public class WindowFrameActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final StepReporter reporter;
	private final ElementWaiter waitActions;
	private final ElementLocator locatorActions;

	public WindowFrameActions(Supplier<RemoteWebDriver> driverSupplier, StepReporter reporter,
			ElementWaiter waitActions, ElementLocator locatorActions) {
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

	/**
	 * Switches to the window at {@code index} (in the order the browser lists its windows).
	 *
	 * <p>A window opened by the previous click (a popup, a link with target=_blank) is usually not there yet
	 * when the next line runs, so this waits up to the short wait time for it to exist. It used to look
	 * once and fail at once with "index out of bounds".
	 */
	public void switchToWindow(int index) {
		try {
			if (index >= 0) {
				waitActions.waitFor(d -> d.getWindowHandles().size() > index ? Boolean.TRUE : null,
						WaitUtils.getShortWaitTime(), "Window " + index + " did not open");
			}
			List<String> allHandles = new ArrayList<>(driver().getWindowHandles());
			if (index >= 0 && index < allHandles.size()) {
				driver().switchTo().window(allHandles.get(index));
				reporter.reportStep("Switched to window index: " + index + " - " + driver().getTitle(),
						"info", false);
			} else {
				reporter.reportStep("Window index out of bounds: " + index + " (" + allHandles.size() + " open)", "fail", false);
			}
		} catch (NoSuchWindowException e) {
			reporter.reportStep("Window not found at index: " + index, "fail", false);
		}
	}

	/**
	 * Switches to the window whose title is exactly {@code title}, waiting up to the short wait time for it
	 * (a popup's title is often set a moment after the window opens).
	 *
	 * @return {@code true} if switched; {@code false} if no window had that title, in which case the browser
	 *         is back on the window it was on. It used to be left on the last window it had looked at.
	 */
	public boolean switchToWindowByTitle(String title) {
		return switchToWindowWhere("title", title, d -> title.equals(d.getTitle()));
	}

	/** As {@link #switchToWindowByTitle(String)}, matching the window's exact current URL. */
	public boolean switchToWindowByUrl(String url) {
		return switchToWindowWhere("URL", url, d -> url.equals(d.getCurrentUrl()));
	}

	private boolean switchToWindowWhere(String what, String wanted, Predicate<WebDriver> matches) {
		String original = currentHandleOrNull();
		try {
			Boolean found = waitActions.waitFor(d -> {
				for (String window : new ArrayList<>(d.getWindowHandles())) {
					d.switchTo().window(window);
					if (matches.test(d)) {
						return Boolean.TRUE;
					}
				}
				return null;
			}, WaitUtils.getShortWaitTime(), "No window with " + what + " '" + wanted + "' appeared");
			if (found != null) {
				reporter.reportStep("Switched to window with " + what + ": " + wanted, "info", false);
				return true;
			}
			switchBackTo(original);
			reporter.reportStep("Window not found with " + what + ": " + wanted, "warning", false);
		} catch (NoSuchWindowException e) {
			switchBackTo(original);
			reporter.reportStep("Window switch failed: " + e.getMessage(), "fail", false);
		}
		return false;
	}

	private String currentHandleOrNull() {
		try {
			return driver().getWindowHandle();
		} catch (NoSuchWindowException e) {
			return null;     // the current window has already been closed
		}
	}

	private void switchBackTo(String handle) {
		if (handle == null) {
			return;
		}
		try {
			driver().switchTo().window(handle);
		} catch (NoSuchWindowException ignored) {
			// closed in the meantime: nothing to go back to
		}
	}

	public void switchToFrame(int index) {
		try {
			ElementSupport.orFail(reporter, waitActions.waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(index),
					WaitUtils.getShortWaitTime(), "Frame not available at index: " + index), "Frame not available at index: " + index, false);
			reporter.reportStep("Switched to frame index: " + index, "info", false);
		} catch (NoSuchFrameException e) {
			reporter.reportStep("Frame not found at index: " + index, "fail", false);
		}
	}

	public void switchToFrame(WebElement ele) {
		try {
			ElementSupport.orFail(reporter, waitActions.waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(ele),
					WaitUtils.getShortWaitTime(), "Frame not available"), "Frame not available", false);
			reporter.reportStep("Switched to frame element", "info", false);
		} catch (NoSuchFrameException e) {
			reporter.reportStep("Frame not found", "fail", false);
		}
	}

	public void switchToFrame(String idOrName) {
		try {
			ElementSupport.orFail(reporter, waitActions.waitFor(ExpectedConditions.frameToBeAvailableAndSwitchToIt(idOrName),
					WaitUtils.getShortWaitTime(), "Frame not available: " + idOrName), "Frame not available: " + idOrName, false);
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
