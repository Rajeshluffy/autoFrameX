package com.alfa3DViewer.pages;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.FluentWait;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.Reporter;
import com.framework.utils.WaitUtils;

public class Loading extends ProjectSpecificMethods {

	private static final int LOAD_TIMEOUT_SECONDS = 300;
	private static final int INITIAL_TIMEOUT_SECONDS = 15;
	private static final By OVERLAY_CONTAINER = By.xpath("//div[@class='cdk-overlay-container']");

	public Loading loadingDisappear() {
		boolean loadStatus = waitForLoadingToComplete(
				getDriver(), this, OVERLAY_CONTAINER, INITIAL_TIMEOUT_SECONDS, LOAD_TIMEOUT_SECONDS);
		if (loadStatus) {
			reportStep("Loading completed successfully", "pass");
		} else {
			reportStep("Loading did not complete within the expected time or was too fast to catch", "warning");
		}
		return this;
	}


// ========================================================================
	// LOADING OVERLAY OPERATIONS
	// ========================================================================

	/**
	 * Waits for a loading overlay to appear and then fully disappear.
	 * <p>
	 * This method temporarily suppresses the implicit wait to 1 second during
	 * detection, then restores it afterward — even if an exception occurs.
	 *
	 * @param driver         the WebDriver instance for the current thread
	 * @param reporter       the Reporter instance for logging (may be null)
	 * @param overlayLocator the {@link By} locator identifying the overlay container
	 * @param initialTimeout seconds to wait for the overlay to <em>appear</em>
	 * @param mainTimeout    seconds to wait for the overlay to <em>disappear</em>
	 * @return {@code true} if loading completed (overlay disappeared),
	 *         {@code false} if it timed out or appeared too briefly to catch
	 */
	public static boolean waitForLoadingToComplete(RemoteWebDriver driver, Reporter reporter,
			By overlayLocator, int initialTimeout, int mainTimeout) {
		boolean disappearStatus = false;
		try {
			WaitUtils.setImplicitWait(driver, reporter, 1);

			waitForOverlayToAppear(driver, overlayLocator, initialTimeout);
			disappearStatus = waitForOverlayToDisappear(driver, overlayLocator, mainTimeout);

		} catch (TimeoutException e) {
			disappearStatus = false;
		} finally {
			// Always restore framework-default implicit wait
			WaitUtils.resetImplicitWait(driver, reporter);
		}
		return disappearStatus;
	}

	// ── Private helpers ──────────────────────────────────────────────────────

	private static void waitForOverlayToAppear(RemoteWebDriver driver,
			By overlayLocator, int timeoutSeconds) {
		createOverlayWait(driver, timeoutSeconds)
				.until(d -> overlayHasChildren(driver, overlayLocator));
	}

	private static boolean waitForOverlayToDisappear(RemoteWebDriver driver,
			By overlayLocator, int timeoutSeconds) {
		return createOverlayWait(driver, timeoutSeconds)
				.until(d -> !overlayHasChildren(driver, overlayLocator));
	}

	private static boolean overlayHasChildren(RemoteWebDriver driver, By overlayLocator) {
		try {
			List<WebElement> children = driver
					.findElement(overlayLocator)
					.findElements(By.xpath("./*"));
			return !children.isEmpty();
		} catch (Exception e) {
			return false; // Overlay absent — treat as "no children"
		}
	}

	private static FluentWait<RemoteWebDriver> createOverlayWait(
			RemoteWebDriver driver, int timeoutSeconds) {
		return new FluentWait<>(driver)
				.withTimeout(Duration.ofSeconds(timeoutSeconds))
				.pollingEvery(Duration.ofMillis(250))
				.ignoring(Exception.class);
	}





}
