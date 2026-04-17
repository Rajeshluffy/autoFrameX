package com.framework.testng.api.base;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import com.framework.config.data.ConfigManager;
import com.framework.selenium.api.base.SeleniumBase;
import com.framework.utils.DataLibrary;
import com.framework.utils.FakerDataFactory;
import com.framework.utils.Reporter;

import design.patterns.factory.browser.BrowserConfig;
import design.patterns.object.pool.DriverPoolManager;

/**
 * Base class for all test classes — wires the TestNG lifecycle into the
 * object-pool driver management.
 *
 * <h3>Lifecycle order (TestNG guarantees this)</h3>
 * 
 * <pre>
 *   &#64;BeforeSuite  → Reporter.startReport()          (report init)
 *   &#64;BeforeTest   → Reporter.initFromContext()       (pool init)
 *   &#64;BeforeClass  → Reporter.startTestCase()         (extent node)
 *                 ↑ child's setValues() runs FIRST because TestNG
 *                   calls the most-derived class's @BeforeClass first
 *   &#64;BeforeMethod → ProjectSpecificMethods.preCondition()  (driver setup)
 *   &#64;Test         → actual test
 *   &#64;AfterMethod  → ProjectSpecificMethods.postCondition() (driver return)
 *   &#64;AfterClass   → ProjectSpecificMethods.cleanupTestCase()
 *   @AfterSuite   → Reporter.endReport()            (flush + pool shutdown)
 * </pre>
 *
 * <p>
 * <b>Thread safety:</b> Each test method runs in a dedicated thread.
 * The {@link DriverPoolManager} uses a {@link ThreadLocal} so every thread
 * has its own isolated driver context — safe for {@code parallel="methods"}
 * and {@code parallel="classes"}.
 *
 * @author Framework Team
 * @version 3.2
 */
public class ProjectSpecificMethods extends SeleniumBase {

	private static final Logger logger = Logger.getLogger(ProjectSpecificMethods.class.getName());

	// =========================================================================
	// DATA PROVIDER
	// =========================================================================

	/**
	 * Reads test data from the Excel file named in {@link #excelFileName}.
	 * Set {@code excelFileName} in your child class {@code @BeforeClass}.
	 */
	@DataProvider(name = "fetchData", indices = 0)
	public Object[][] fetchData() throws IOException {
		if (excelFileName == null || excelFileName.isEmpty()) {
			logger.warning("excelFileName not set — returning empty data set.");
			return new Object[0][0];
		}
		return DataLibrary.readExcelData(excelFileName);
	}

	// =========================================================================
	// @BeforeMethod — driver setup
	// =========================================================================

	/**
	 * Acquires a WebDriver from the pool and binds it to the current thread.
	 * Also creates the Extent report node for this method.
	 *
	 * <p>
	 * {@code ITestContext} is supported in {@code @BeforeMethod} and is
	 * auto-injected by TestNG — no manual lookup required.
	 *
	 * @param method  current test method (auto-injected)
	 * @param context TestNG context (auto-injected)
	 */
	@BeforeMethod(alwaysRun = true)
	public void preCondition(Method method, ITestContext context) {
		logger.info("▶ @BeforeMethod: " + method.getDeclaringClass().getSimpleName()
				+ "#" + method.getName());

		// 1. Collect any method / test-level parameter overrides
		ConcurrentMap<String, String> methodParams = extractMethodParameters(context, method);

		// 2. Acquire driver from pool and store in ThreadLocal
		getDriverManager().setupDriver(method, methodParams);

		// 3. Create the Extent report child-node for this method
		// removed setNode(method, context); as it is handled by TestNG via @BeforeMethod in Reporter.java

		// 4. Log the URL the browser was navigated to — visible in the HTML report
		try {
			String currentUrl = getDriver().getCurrentUrl();
			reportStep("Browser opened: " + currentUrl, "INFO", false);
		} catch (Exception e) {
			logger.warning("Could not read current URL after driver setup: " + e.getMessage());
		}

		logger.info("Driver ready for: " + method.getName());
	}

	// =========================================================================
	// @AfterMethod — driver teardown
	// =========================================================================

	/**
	 * Returns the driver to the pool (or destroys it on failure) and tears
	 * down the Extent report node.
	 *
	 * @param method current test method (auto-injected)
	 * @param result test result containing pass/fail status (auto-injected)
	 */
	@AfterMethod(alwaysRun = true)
	public void postCondition(Method method, ITestResult result) {
		logger.info("@AfterMethod: " + method.getName()
				+ " [" + (result.isSuccess() ? "PASSED" : "FAILED") + "]");

		try {
			getDriverManager().teardownDriver(method, result.isSuccess());
		} catch (Exception e) {
			logger.severe("Teardown error for " + method.getName() + ": " + e.getMessage());
		}

		// Release the thread-local Faker instance to prevent memory leaks
		FakerDataFactory.cleanup();

		// Flush the Extent report node (defined in Reporter)
		tearDownTest(method, result);
	}

	// =========================================================================
	// @AfterClass — class-level cleanup
	// =========================================================================

	@AfterClass(alwaysRun = true)
	public void cleanupTestCase(ITestContext context) {
		logger.info("@AfterClass cleanup: " + testcaseName);
	}

	// =========================================================================
	// DRIVER ACCESS — delegates to DriverPoolManager (ThreadLocal-backed)
	// =========================================================================

	/**
	 * Returns the {@link RemoteWebDriver} bound to the current thread.
	 * Safe to call from any {@code @Test} method or page object.
	 */
	protected RemoteWebDriver getDriver() {
		try {
			return getDriverManager().getDriver();
		} catch (Exception e) {
			throw new IllegalStateException(
					"No WebDriver available — ensure @BeforeMethod ran successfully.", e);
		}
	}

	   // ========================================================================
	// RETRY OPERATIONS
	// ========================================================================

	/**
	 * Retries a {@link Runnable} action up to {@code maxAttempts} times, pausing
	 * between each attempt. Reports a warning via the reporter if all retries fail.
	 *
	 * @param reporter           the Reporter instance for logging (may be null)
	 * @param action             the action to retry
	 * @param maxAttempts        maximum number of retry attempts
	 * @param waitBetweenRetries wait time between retries in milliseconds
	 * @return {@code true} if the action succeeded within the retry limit,
	 *         {@code false} otherwise
	 */
	public static boolean retryAction(Reporter reporter, Runnable action,
			int maxAttempts, int waitBetweenRetries) {
		Exception lastException = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				action.run();
				return true;
			} catch (Exception e) {
				lastException = e;
				if (attempt < maxAttempts) {
					try {
						Thread.sleep(waitBetweenRetries);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}
		if (lastException != null && reporter != null) {
			reporter.reportStep(
					"Action failed after " + maxAttempts + " attempts: " + lastException.getMessage(),
					"warning", false);
		}
		return false;
	}
	
	/**
	 * Returns the {@link WebDriverWait} bound to the current thread.
	 * Falls back to creating a new wait with default timeout if needed.
	 */
	public WebDriverWait getWait() {
		try {
			return getDriverManager().getWait();
		} catch (Exception e) {
			RemoteWebDriver driver = getDriver();
			if (driver != null) {
				// Fallback timeout read from property file
				int timeout = ConfigManager.getInstance().getConfig().getDefaultWaitTimeout();
				return new WebDriverWait(driver, Duration.ofSeconds(timeout));
			}
			throw new IllegalStateException(
					"No WebDriverWait available — ensure @BeforeMethod ran successfully.", e);
		}
	}

	/**
	 * Creates a one-off {@link WebDriverWait} with a custom timeout.
	 * Use this inside a test method where a longer / shorter wait is needed.
	 *
	 * @param seconds wait timeout in seconds
	 */
	protected WebDriverWait createCustomWait(int seconds) {
		return new WebDriverWait(getDriver(), Duration.ofSeconds(seconds));
	}

	/**
	 * Convenience: returns the current page URL.
	 */
	protected String getCurrentUrl() {
		try {
			return getDriver().getCurrentUrl();
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * Convenience: returns the current page title.
	 */
	protected String getPageTitle() {
		try {
			return getDriver().getTitle();
		} catch (Exception e) {
			return "";
		}
	}

	// =========================================================================
	// PRIVATE HELPERS
	// =========================================================================



	/**
	 * Collects test-level parameters from the TestNG XML context, then merges
	 * any {@link BrowserConfig} annotation on the method so browser / URL
	 * overrides work on a per-method basis.
	 */
	private ConcurrentMap<String, String> extractMethodParameters(
			ITestContext context, Method method) {

		ConcurrentMap<String, String> params = new ConcurrentHashMap<>();

		if (context == null) {
			return params;
		}

		// Test-level params from testng.xml (override suite params)
		if (context.getCurrentXmlTest() != null) {
			context.getCurrentXmlTest().getAllParameters().forEach(params::put);
		}

		// Per-method @BrowserConfig annotation
		BrowserConfig cfg = method.getAnnotation(BrowserConfig.class);
		if (cfg != null) {
			if (cfg.value() != null && !cfg.value().isEmpty())
				params.put("browser", cfg.value());
			if (cfg.url() != null && !cfg.url().isEmpty())
				params.put("url", cfg.url());
		}

		return params;
	}

	/**
	 * Exposes the {@link DriverPoolManager} singleton.
	 * Overrides the same method in {@link com.framework.utils.Reporter} to
	 * ensure both use the same instance.
	 */
	protected DriverPoolManager getDriverManager() {
		if (driverManager == null) {
			driverManager = DriverPoolManager.getInstance();
		}
		return driverManager;
	}
}