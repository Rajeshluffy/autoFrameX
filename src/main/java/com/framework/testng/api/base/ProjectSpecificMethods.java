package com.framework.testng.api.base;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import com.framework.config.data.ConfigManager;
import com.framework.config.data.ExecutionMode;
import com.framework.selenium.api.base.SeleniumBase;
import com.framework.utils.DataLibrary;
import com.framework.utils.EncryptionUtils;
import com.framework.utils.FakerDataFactory;
import com.framework.utils.Reporter;
import com.framework.utils.ValidationUtils;
import com.framework.utils.VideoRecorder;

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

	private static final Logger logger = LoggerFactory.getLogger(ProjectSpecificMethods.class);

	// ThreadLocal so each parallel test thread has its own recorder instance
	private static final ThreadLocal<VideoRecorder> videoRecorder = new ThreadLocal<>();

	// Current account bound to this thread during @BeforeMethod
	private static final ThreadLocal<AccountData> currentAccount = new ThreadLocal<>();

	// =========================================================================
	// DATA PROVIDER
	// =========================================================================

	/**
	 * Mode-aware DataProvider for all test methods that declare
	 * {@code @Test(dataProvider = "fetchData")}.
	 *
	 * <ul>
	 *   <li><b>DATA_PROVIDER mode</b> — reads every account row from the Excel
	 *       file configured via {@code excel.data.file} in the TestNG XML. Each
	 *       row is returned as a single {@link AccountData} element so the test
	 *       method receives exactly one typed argument.</li>
	 *   <li><b>TARGETED mode</b> — builds a single {@link AccountData} from the
	 *       global config (credentials + URL already resolved by
	 *       {@link ConfigManager}). The test method runs exactly once.</li>
	 * </ul>
	 *
	 * <p>Test methods annotated with {@link TestMetadata#allRows()} = false and
	 * running in DATA_PROVIDER mode still receive all rows — the {@code allRows}
	 * flag is honoured only when {@code excelFileName} is set directly on the
	 * class (legacy behaviour). Prefer the XML param approach for new tests.
	 */
	@DataProvider(name = "fetchData", parallel = true)
	public Object[][] fetchData() throws IOException {
		ExecutionMode mode = ConfigManager.getInstance().getConfig().getExecutionMode();

		if (mode == ExecutionMode.DATA_PROVIDER) {
			String dataFile = ConfigManager.getInstance().getConfig().getExcelDataFile();
			if (dataFile == null || dataFile.isEmpty()) {
				// Fallback: honour the legacy excelFileName field set in @BeforeClass
				dataFile = excelFileName;
			}
			if (dataFile == null || dataFile.isEmpty()) {
				logger.warn("DATA_PROVIDER mode active but no Excel file configured."
						+ " Set 'excel.data.file' in your testng.xml.");
				return new AccountData[0][0];
			}
			logger.info("DATA_PROVIDER mode — loading accounts from: " + dataFile);
			return DataLibrary.readExcelAsAccounts(dataFile);
		}

		// TARGETED mode: single account from config
		logger.info("TARGETED mode — using single account from config.");
		var cfg = ConfigManager.getInstance().getConfig();
		AccountData account = new AccountData(
				"targeted",
				cfg.getUserName(),
				cfg.getPassword(),
				cfg.getAppUrl());
		return new AccountData[][]{{account}};
	}

	// =========================================================================
	// @BeforeMethod — driver setup
	// =========================================================================

	/**
	 * Sets up the WebDriver for this test method.
	 *
	 * <p>If the test method declares a parameter of type {@link AccountData} (i.e.
	 * it uses the {@code fetchData} DataProvider), that account is injected into
	 * the thread-local so page objects can retrieve it via {@link #getAccount()}.
	 * When the account carries its own URL it overrides the global {@code appUrl}.
	 *
	 * @param method     current test method (auto-injected by TestNG)
	 * @param context    TestNG suite context (auto-injected by TestNG)
	 * @param parameters test parameters injected by the DataProvider (may be empty)
	 */
	@BeforeMethod(alwaysRun = true)
	public void preCondition(Method method, ITestContext context, Object[] parameters) {
		logger.info("▶ @BeforeMethod: " + method.getDeclaringClass().getSimpleName()
				+ "#" + method.getName());

		// 1. Bind account from DataProvider parameters (if present)
		AccountData account = extractAccount(parameters, context);
		currentAccount.set(account);
		logger.info("Account bound: " + account);

		// 2. Collect method / test-level parameter overrides; inject account URL if set
		ConcurrentMap<String, String> methodParams = extractMethodParameters(context, method);
		if (account.hasUrl()) {
			methodParams.put("url", account.getUrl());
		}

		// 3. Acquire driver from pool and store in ThreadLocal
		getDriverManager().setupDriver(method, methodParams);

		// 4. Log the URL the browser was navigated to — visible in the HTML report
		try {
			String currentUrl = getDriver().getCurrentUrl();
			reportStep("Browser opened: " + currentUrl, "INFO", false);
		} catch (Exception e) {
			logger.warn("Could not read current URL after driver setup: " + e.getMessage());
		}

		// 5. Start video recording — WebDriver screenshot based, works in headless CI
		try {
			VideoRecorder vr = new VideoRecorder();
			vr.start(method.getName(), getDriver());
			videoRecorder.set(vr);
		} catch (Exception e) {
			logger.warn("Video recording could not start for {}: {}", method.getName(), e.getMessage());
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

		// Stop video recording.
		// stop(passed) owns the full lifecycle:
		//   passed=true  → AVI deleted immediately, no MP4, returns null
		//   passed=false → AVI converted to MP4, kept for analysis, returns File
		VideoRecorder vr = videoRecorder.get();
		if (vr != null) {
			try {
				java.io.File videoFile = vr.stop(result.isSuccess());
				if (videoFile != null) {
					// Only reached on failure — log and attach to the Extent report
					logger.info("[Thread-{}] Video retained for failed test {}: {}",
							Thread.currentThread().getId(), method.getName(), videoFile.getAbsolutePath());
					reportStep("Failure video: " + videoFile.getName(), "info", false);
				}
			} catch (Exception e) {
				logger.warn("Video stop failed for {}: {}", method.getName(), e.getMessage());
			} finally {
				videoRecorder.remove();
			}
		}

		try {
			getDriverManager().teardownDriver(method, result.isSuccess());
		} catch (Exception e) {
			logger.error("Teardown error for " + method.getName() + ": " + e.getMessage());
		}

		// Release the thread-local Faker instance and account to prevent memory leaks
		FakerDataFactory.cleanup();
		currentAccount.remove();

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
			waitForPageAndApiReady();
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
	// TEST DATA HELPERS — Faker, Validation, Encryption
	// =========================================================================

	/**
	 * Returns the thread-local {@link FakerDataFactory} static accessor namespace.
	 * Use for generating realistic test data without importing FakerDataFactory:
	 * <pre>
	 *   String name    = FakerDataFactory.getFullName();
	 *   String email   = FakerDataFactory.getEmail();
	 *   String company = FakerDataFactory.getCompanyName();
	 * </pre>
	 */
	protected static FakerDataFactory faker() {
		return null; // static methods only — use FakerDataFactory.getXxx() directly
	}

	/**
	 * Asserts that {@code actual} equals {@code expected}, logging the comparison.
	 * Delegates to {@link ValidationUtils#assertTextEquals}.
	 */
	protected static void assertTextEquals(String actual, String expected, String message) {
		ValidationUtils.assertTextEquals(actual, expected, message);
	}

	/**
	 * Asserts that {@code actual} contains {@code substring}.
	 * Delegates to {@link ValidationUtils#assertTextContains}.
	 */
	protected static void assertTextContains(String actual, String substring, String message) {
		ValidationUtils.assertTextContains(actual, substring, message);
	}

	/**
	 * Asserts that {@code actualCode} equals {@code expectedCode}.
	 * Delegates to {@link ValidationUtils#assertResponseCode}.
	 */
	protected static void assertResponseCode(int actualCode, int expectedCode, String message) {
		ValidationUtils.assertResponseCode(actualCode, expectedCode, message);
	}

	/**
	 * Returns a soft-assertion collector for the current test method.
	 * Call {@code soft.assertAll()} at the end of the test to throw on any failures:
	 * <pre>
	 *   ValidationUtils.SoftAssert soft = softAssert();
	 *   soft.assertEquals(actual, expected, "field check");
	 *   soft.assertAll();
	 * </pre>
	 */
	protected static ValidationUtils.SoftAssert softAssert() {
		return new ValidationUtils.SoftAssert();
	}

	/**
	 * Masks a sensitive value for safe logging (shows first 2 and last 2 chars).
	 * Delegates to {@link EncryptionUtils#mask}.
	 * <pre>
	 *   reportStep("Password used: " + mask(password), "info", false);
	 * </pre>
	 */
	protected static String mask(String sensitiveValue) {
		return EncryptionUtils.mask(sensitiveValue);
	}

	/**
	 * Masks all credential patterns in a log string (password=, Bearer token, etc.).
	 * Delegates to {@link EncryptionUtils#maskSensitiveValues}.
	 */
	protected static String maskLog(String logLine) {
		return EncryptionUtils.maskSensitiveValues(logLine);
	}

	// =========================================================================
	// ACCOUNT ACCESS
	// =========================================================================

	/**
	 * Returns the {@link AccountData} bound to the current test thread.
	 *
	 * <p>Available from {@code @BeforeMethod} onwards. Page objects and test
	 * classes call this to retrieve per-account credentials without importing
	 * {@link ConfigManager} directly:
	 * <pre>
	 *   loginPage.enterCredentials(getAccount().getUserName(), getAccount().getPassword());
	 * </pre>
	 *
	 * @throws IllegalStateException if called before {@code preCondition} has run
	 */
	protected AccountData getAccount() {
		AccountData account = currentAccount.get();
		if (account == null) {
			throw new IllegalStateException(
					"No AccountData bound — ensure @BeforeMethod (preCondition) has executed.");
		}
		return account;
	}

	// =========================================================================
	// PRIVATE HELPERS
	// =========================================================================

	/**
	 * Extracts an {@link AccountData} from the DataProvider parameters array.
	 * Falls back to building one from the global config when none is present
	 * (e.g. tests that do not use the {@code fetchData} DataProvider).
	 */
	private AccountData extractAccount(Object[] parameters, ITestContext context) {
		if (parameters != null) {
			for (Object param : parameters) {
				if (param instanceof AccountData) {
					return (AccountData) param;
				}
			}
		}
		// No AccountData in parameters — build from global config (TARGETED fallback)
		var cfg = ConfigManager.getInstance().getConfig();
		String url = null;
		if (context != null && context.getCurrentXmlTest() != null) {
			url = context.getCurrentXmlTest().getParameter("url");
		}
		return new AccountData(
				"targeted",
				cfg.getUserName(),
				cfg.getPassword(),
				url != null ? url : cfg.getAppUrl());
	}



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