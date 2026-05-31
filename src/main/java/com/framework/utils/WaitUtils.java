package com.framework.utils;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.framework.config.data.ConfigManager;

/**
 * <b>Class Name:</b> WaitUtils <br>
 * <b>Package:</b> com.framework.utils <br>
 * <b>Purpose:</b> Centralized, stateless, thread-safe utility class for all
 * Selenium wait and synchronization mechanisms. <br>
 * <b>Design Pattern:</b> Utility Class — {@code final} with a private
 * constructor to prevent instantiation. <br>
 * <b>Thread Safety:</b> All methods receive the {@link RemoteWebDriver}
 * instance as a parameter, ensuring each thread operates on its own isolated
 * driver from the framework's {@code DriverPoolManager} (ThreadLocal-backed).
 *
 * @author Framework Architect
 * @version 1.0
 */
public final class WaitUtils {

	/** Prevent instantiation — utility class only. */
	private WaitUtils() {
	}

	// ========================================================================
	// CONFIGURATION GETTERS (sourced from ConfigManager)
	// ========================================================================

	/**
	 * Returns the explicit (default) wait timeout in seconds from the framework
	 * configuration.
	 */
	public static int getDefaultWaitTime() {
		return ConfigManager.getInstance().getConfig().getExplicit();
	}

	/**
	 * Returns the short wait timeout in seconds (e.g. for alerts, frames) from the
	 * framework configuration.
	 */
	public static int getShortWaitTime() {
		return ConfigManager.getInstance().getConfig().getShortWait();
	}

	/**
	 * Returns the fluent-wait polling interval in milliseconds from the framework
	 * configuration.
	 */
	public static int getPollingIntervalMs() {
		return ConfigManager.getInstance().getConfig().getPollingIntervalMs();
	}

	/**
	 * Returns the maximum number of element-interaction retry attempts from the
	 * framework configuration.
	 */
	public static int getMaxRetryAttempts() {
		return ConfigManager.getInstance().getConfig().getElementMaxRetryAttempts();
	}

	// ========================================================================
	// EXPLICIT WAIT OPERATIONS
	// ========================================================================

	/**
	 * Creates a {@link WebDriverWait} with the specified timeout.
	 *
	 * @param driver           the WebDriver instance for the current thread
	 * @param timeoutInSeconds timeout duration in seconds
	 * @return a configured {@link WebDriverWait}
	 */
	public static WebDriverWait getWait(RemoteWebDriver driver, int timeoutInSeconds) {
		return new WebDriverWait(driver, Duration.ofSeconds(timeoutInSeconds));
	}

	/**
	 * Waits for an {@link ExpectedCondition} using the framework default timeout.
	 *
	 * @param <T>          the return type of the condition
	 * @param driver       the WebDriver instance for the current thread
	 * @param reporter     the Reporter instance used to log failures (may be null)
	 * @param condition    the condition to wait for
	 * @param errorMessage message to log on timeout
	 * @return result of the condition, or {@code null} on timeout/error
	 */
	public static <T> T waitFor(RemoteWebDriver driver, Reporter reporter,
			ExpectedCondition<T> condition, String errorMessage) {
		return waitFor(driver, reporter, condition, getDefaultWaitTime(), errorMessage);
	}

	/**
	 * Waits for an {@link ExpectedCondition} with a custom timeout.
	 *
	 * @param <T>              the return type of the condition
	 * @param driver           the WebDriver instance for the current thread
	 * @param reporter         the Reporter instance used to log failures (may be null)
	 * @param condition        the condition to wait for
	 * @param timeoutInSeconds timeout in seconds
	 * @param errorMessage     message to log on timeout
	 * @return result of the condition, or {@code null} on timeout/error
	 */
	public static <T> T waitFor(RemoteWebDriver driver, Reporter reporter,
			ExpectedCondition<T> condition, int timeoutInSeconds, String errorMessage) {
		try {
			return getWait(driver, timeoutInSeconds).until(condition);
		} catch (TimeoutException e) {
			if (reporter != null) {
				reporter.reportStep(errorMessage + " after " + timeoutInSeconds + " seconds", "warning", false);
			}
			return null;
		} catch (Exception e) {
			if (reporter != null) {
				reporter.reportStep("Wait failed: " + errorMessage + " - " + e.getMessage(), "fail", false);
			}
			return null;
		}
	}

	// ========================================================================
	// FLUENT WAIT OPERATIONS
	// ========================================================================

	/**
	 * Fluent wait with the framework default timeout and polling interval, ignoring
	 * common transient exceptions.
	 *
	 * @param <T>       the return type of the condition
	 * @param driver    the WebDriver instance for the current thread
	 * @param condition the function to evaluate repeatedly
	 * @return result of the condition, or {@code null} on timeout
	 */
	public static <T> T fluentWaitFor(RemoteWebDriver driver, Function<RemoteWebDriver, T> condition) {
		return fluentWaitFor(driver, condition, getDefaultWaitTime(), getPollingIntervalMs());
	}

	/**
	 * Fluent wait with custom timeout and polling interval, ignoring common
	 * transient exceptions ({@link NoSuchElementException},
	 * {@link StaleElementReferenceException}, {@link ElementNotInteractableException}).
	 *
	 * @param <T>               the return type of the condition
	 * @param driver            the WebDriver instance for the current thread
	 * @param condition         the function to evaluate repeatedly
	 * @param timeoutInSeconds  timeout in seconds
	 * @param pollingIntervalMs polling interval in milliseconds
	 * @return result of the condition, or {@code null} on timeout
	 */
	public static <T> T fluentWaitFor(RemoteWebDriver driver, Function<RemoteWebDriver, T> condition,
			int timeoutInSeconds, int pollingIntervalMs) {
		FluentWait<RemoteWebDriver> wait = new FluentWait<>(driver)
				.withTimeout(Duration.ofSeconds(timeoutInSeconds))
				.pollingEvery(Duration.ofMillis(pollingIntervalMs))
				.ignoring(NoSuchElementException.class)
				.ignoring(StaleElementReferenceException.class)
				.ignoring(ElementNotInteractableException.class);
		try {
			return wait.until(condition);
		} catch (TimeoutException e) {
			return null;
		}
	}

	

	// ========================================================================
	// IMPLICIT WAIT OPERATIONS
	// ========================================================================

	/**
	 * Sets a temporary implicit wait on the given driver.
	 *
	 * @param driver   the WebDriver instance for the current thread
	 * @param reporter the Reporter instance for logging (may be null)
	 * @param seconds  timeout in seconds
	 */
	public static void setImplicitWait(RemoteWebDriver driver, Reporter reporter, int seconds) {
		try {
			driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(seconds));
			if (reporter != null) {
				reporter.reportStep("Set temporary implicit wait to " + seconds + " seconds", "info", false);
			}
		} catch (Exception e) {
			if (reporter != null) {
				reporter.reportStep("Failed to set temporary implicit wait", "warning", false);
			}
		}
	}

	/**
	 * Resets the implicit wait to the default value defined in the framework
	 * configuration.
	 *
	 * @param driver   the WebDriver instance for the current thread
	 * @param reporter the Reporter instance for logging (may be null)
	 */
	public static void resetImplicitWait(RemoteWebDriver driver, Reporter reporter) {
		try {
			
			int defaultWait = ConfigManager.getInstance().getConfig().getImplicit();
				 setImplicitWait(driver, reporter, defaultWait); 


			if (reporter != null) {
				reporter.reportStep(
						"Reset implicit wait to default (" + defaultWait + " seconds)", "info", false);
			}
		} catch (Exception e) {
			if (reporter != null) {
				reporter.reportStep("Failed to reset implicit wait", "warning", false);
			}
		}
	}

	/**
	 * Retries a runnable action until it succeeds or max attempts are reached,
	 * sleeping a fixed interval between attempts.
	 *
	 * @param context           optional context object (can be null)
	 * @param action            action to execute
	 * @param maxAttempts       maximum number of attempts
	 * @param pollingIntervalMs wait time between attempts in milliseconds
	 * @return true if successful, false if all attempts fail
	 */
	public static boolean retryAction(Object context, Runnable action, int maxAttempts, int pollingIntervalMs) {
		if (action == null || maxAttempts <= 0) {
			return false;
		}
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				action.run();
				return true;
			} catch (Exception e) {
				if (attempt == maxAttempts) {
					return false;
				}
				sleep(pollingIntervalMs);
			}
		}
		return false;
	}

	/**
	 * Convenience overload — uses config defaults for max attempts and polling.
	 *
	 * @param context optional context object (can be null)
	 * @param action  action to execute
	 * @return true if successful, false if all attempts fail
	 */
	public static boolean retryAction(Object context, Runnable action) {
		return retryAction(context, action, getMaxRetryAttempts(), getPollingIntervalMs());
	}

	/**
	 * Retries an action using <b>exponential backoff</b>.
	 *
	 * <p>Backoff schedule (milliseconds): 100 → 200 → 500 → 1000 → 2000 → 5000 (cap).
	 * This strategy is preferred over fixed polling for transient failures (network
	 * blips, stale DOM updates) because it gives the system more recovery time on
	 * successive failures without hammering it with rapid retries.
	 *
	 * @param action      action to execute
	 * @param maxAttempts maximum number of attempts (must be &ge; 1)
	 * @return {@code true} if the action succeeded on any attempt
	 */
	public static boolean retryWithExponentialBackoff(Runnable action, int maxAttempts) {
		if (action == null || maxAttempts <= 0) {
			return false;
		}

		// Backoff steps in ms: 100, 200, 500, 1000, 2000 — then capped at 5000
		long[] backoffMs = {100, 200, 500, 1000, 2000};
		long capMs = 5_000;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				action.run();
				return true;
			} catch (Exception e) {
				if (attempt == maxAttempts) {
					return false;
				}
				long delay = attempt <= backoffMs.length
						? backoffMs[attempt - 1]
						: capMs;
				sleep((int) delay);
			}
		}
		return false;
	}

	// ========================================================================
	// INTERNAL HELPERS
	// ========================================================================

	private static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}
}
