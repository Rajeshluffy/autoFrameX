package design.patterns.object.pool;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.framework.config.data.ConfigManager;
import com.framework.config.data.ConfigResolver;
import com.framework.config.data.ProjectConfig;

import design.patterns.factory.browser.BrowserConfig;
import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserRegistry;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central manager for WebDriver pool lifecycle with improved thread safety.
 * Implements Facade pattern, keyed by execution context (see {@link ConfigManager}).
 *
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Thread-local driver instance management</li>
 * <li>Configuration loading with priority order</li>
 * <li>Pool lifecycle (initialization and shutdown)</li>
 * <li>Integration with TestNG lifecycle</li>
 * </ul>
 *
 * <p>
 * <b>CRITICAL IMPROVEMENTS:</b>
 * <ul>
 * <li>Proper thread-local context management (no leaks)</li>
 * <li>Synchronized pool initialization</li>
 * <li>Better error handling and fallbacks</li>
 * <li>Support for parallel test execution</li>
 * </ul>
 *
 * <p>
 * Thread Safety: All operations are thread-safe for parallel execution.
 *
 * <p>
 * <b>Multi-context support:</b> like {@link ConfigManager}, this was a plain
 * JVM-wide singleton — one pool for the entire process, unable to support two
 * concurrently-running applications with different sizing/browser needs.
 * It is now a registry of pools keyed by the same {@code contextId} string
 * {@link ConfigManager} uses, bound per-thread via {@link #bindContext}.
 * {@link #getInstance()} keeps its original signature and behavior for every
 * existing caller — it transparently resolves to whichever context is bound
 * on the calling thread, defaulting to a single shared context if nothing
 * ever calls {@link #bindContext}.
 *
 * @author Framework Team
 * @version 3.2 - Multi-context (multi-application) pool isolation
 */
public class DriverPoolManager {

	private static final Logger logger = LoggerFactory.getLogger(DriverPoolManager.class);

	/** Context id used when nothing ever calls {@link #bindContext}. */
	private static final String DEFAULT_CONTEXT = "default";

	private static final ConcurrentMap<String, DriverPoolManager> REGISTRY = new ConcurrentHashMap<>();
	private static final ThreadLocal<String> CONTEXT_ID = new ThreadLocal<>();

	// Pool resources (volatile for visibility across threads)
	private volatile WebDriverPoolFactory driverPool;
	private volatile PoolConfig poolConfig;

	// Thread-local storage for driver context (one per thread)
	private final ThreadLocal<DriverContext> contextThreadLocal = ThreadLocal.withInitial(() -> null);

	// Test metadata tracking (thread-safe map)
	private final ConcurrentMap<String, TestMetadata> testMetadata = new ConcurrentHashMap<>();

	// Initialization state tracking
	private volatile boolean poolInitialized = false;

	/**
	 * Lazily returns the resolved framework configuration from the property file.
	 * Using a method instead of field initializer avoids static init order issues.
	 */
	private static ProjectConfig config() {
		return ConfigManager.getInstance().getConfig();
	}

	/**
	 * Private constructor — instances are only created via the {@link #REGISTRY}.
	 */
	private DriverPoolManager() {
		logger.debug("DriverPoolManager instance created");
	}

	/**
	 * Binds the calling thread to a pool context. Must be called on every thread
	 * before it uses {@link #getInstance()} if per-application isolation is
	 * needed — see {@link ConfigManager#bindContext} for the full contract
	 * (same {@code contextId} should be used for both managers together).
	 *
	 * @param contextId identifies which application's pool this thread should
	 *                  use; null falls back to the default context
	 */
	public static void bindContext(String contextId) {
		CONTEXT_ID.set(contextId != null && !contextId.isBlank() ? contextId : DEFAULT_CONTEXT);
	}

	/**
	 * Gets the pool manager bound to the calling thread's context, creating it
	 * on first use. Falls back to {@link #DEFAULT_CONTEXT} if the thread never
	 * called {@link #bindContext} — identical to the old singleton behavior for
	 * any caller that doesn't opt into multi-context isolation.
	 *
	 * @return DriverPoolManager instance for the current thread's context
	 */
	public static DriverPoolManager getInstance() {
		String id = CONTEXT_ID.get();
		if (id == null) id = DEFAULT_CONTEXT;
		return REGISTRY.computeIfAbsent(id, k -> {
			logger.info("DriverPoolManager created for context: " + k);
			return new DriverPoolManager();
		});
	}

	/**
	 * Shuts down every registered context's pool. Called once from
	 * {@code @AfterSuite} — a suite may have created several contexts across
	 * parallel {@code <test>} blocks, each needing its own shutdown.
	 */
	public static void shutdownAll() {
		REGISTRY.values().forEach(DriverPoolManager::shutdownPool);
	}

	/**
	 * Initializes the driver pool with configuration.
	 * Should be called once before test execution (e.g., @BeforeSuite).
	 * 
	 * <p>
	 * <b>THREAD-SAFE:</b> Uses synchronized block to prevent race conditions
	 * in parallel suite execution.
	 * 
	 * <p>
	 * Configuration Priority:
	 * <ol>
	 * <li>TestNG Suite/Test parameters</li>
	 * <li>CI/CD Environment Variables (BROWSER, MAX_POOL_SIZE, etc.)</li>
	 * <li>System Properties (-Dbrowser=chrome)</li>
	 * <li>Configuration files (properties/yaml)</li>
	 * <li>Default values</li>
	 * </ol>
	 * 
	 * @param testngParams parameters from TestNG context (can be null)
	 */
	public synchronized void initializePool(ConcurrentMap<String, String> testngParams) {
		if (poolInitialized) {
			logger.warn("Pool already initialized, skipping re-initialization");
			return;
		}

		logger.info("Initializing WebDriver pool...");

		try {
			// Load configuration with priority resolution
			poolConfig = loadConfiguration(testngParams);
			logger.info("Pool configuration loaded: " + poolConfig);

			// Create factory and pool
			BrowserFactory factory = new BrowserFactory();
			driverPool = new WebDriverPoolFactory(factory, poolConfig);

			poolInitialized = true;
			logger.info("Pool initialized successfully");

			// Safety net: close all drivers if the JVM exits before @AfterSuite fires
			// (e.g. SIGTERM in containers, IDE force-stop, uncaught exception in suite).
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				if (poolInitialized && driverPool != null) {
					logger.info("JVM shutdown hook: closing WebDriver pool...");
					try {
						driverPool.close();
					} catch (Exception ex) {
					logger.warn("Error in shutdown hook: " + ex.getMessage());
					}
				}
			}, "DriverPool-ShutdownHook"));

		} catch (Exception e) {
			logger.error("Failed to initialize pool: " + e.getMessage());
			throw new RuntimeException("Pool initialization failed", e);
		}
	}

	/**
	 * Sets up a driver for the current test method.
	 * Should be called in @BeforeMethod.
	 * 
	 * <p>
	 * <b>THREAD-SAFE:</b> Each thread gets its own driver context via ThreadLocal.
	 * 
	 * @param method       test method being executed
	 * @param methodParams method-specific parameters (can be null)
	 */
	public void setupDriver(Method method, ConcurrentMap<String, String> methodParams) {
		String testName = getTestName(method);
		long threadId = Thread.currentThread().getId();

		logger.info(String.format("Setting up driver for: %s [Thread: %d]",
				testName, threadId));

		ensurePoolInitialized();

		try {
			// Determine browser and URL with priority resolution
			String browserType = determineBrowserId(method, methodParams);
			String url = determineUrl(method, methodParams);

			logger.debug(String.format("Acquiring driver: browser=%s, url=%s",
					browserType, url));

			// Acquire driver from pool (thread-safe operation)
			RemoteWebDriver driver = driverPool.acquire(browserType, url);

			// Create wait instance
			int waitTimeout = getWaitTimeout(methodParams);
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitTimeout));

			// Store in thread-local context
			DriverContext context = new DriverContext(driver, wait, testName, browserType);
			contextThreadLocal.set(context);

			// Track metadata for reporting
			testMetadata.put(testName, new TestMetadata(browserType, url,
					System.currentTimeMillis()));

			logger.info(String.format("Driver ready: %s [Browser: %s, Thread: %d, ID: %d]",
					testName, browserType, threadId,
					System.identityHashCode(driver)));

		} catch (Exception e) {
			logger.error("Failed to setup driver for " + testName + ": " + e.getMessage());
			cleanupContext(testName);
			throw new RuntimeException("Driver setup failed for: " + testName, e);
		}
	}

	/**
	 * Tears down the driver after test execution.
	 * Should be called in @AfterMethod.
	 * 
	 * <p>
	 * <b>THREAD-SAFE:</b> Operates only on current thread's driver context.
	 * 
	 * @param method test method that completed
	 * @param passed whether test passed
	 */
	public void teardownDriver(Method method, boolean passed) {
		String testName = getTestName(method);
		DriverContext context = contextThreadLocal.get();
		long threadId = Thread.currentThread().getId();

		if (context == null || context.getDriver() == null) {
			logger.warn(String.format("No driver to teardown for: %s [Thread: %d]",
					testName, threadId));
			return;
		}

		try {
			RemoteWebDriver driver = context.getDriver();

			// Log test result with duration
			logTestResult(testName, passed, context);

			if (!passed) {
				// Test failed — driver may be in undefined state (stale alert, broken
				// navigation, half-complete XHR).  Transition to POISONED so it is
				// never re-pooled; pool queues an async driver.quit() off the test thread.
				driverPool.release(driver, true);
				logger.info(String.format(
						"Driver poisoned and queued for async quit (test failed): %s [Thread: %d]",
						testName, threadId));
			} else if (poolConfig.isCloseAfterEach()) {
				// Passed + closeAfterEach=true: release healthy driver back to the pool.
				// WebDriverPoolFactory.release(driver, false) handles all cleanup:
				//   • dismisses any lingering alerts
				//   • closes extra windows and navigates to about:blank
				//   • clears cookies, localStorage, sessionStorage
				//   • transitions state IDLE → available for next test
				driverPool.release(driver, false);
				logger.info(String.format(
						"Driver released (windows closed, state reset, session kept): %s [Thread: %d]",
						testName, threadId));
			} else {
				// Pool-reuse mode: return healthy driver without window teardown.
				// Pool still resets cookies/storage before the next test borrows it.
				driverPool.release(driver, false);
				logger.info(String.format("Driver released for reuse: %s [Thread: %d]",
						testName, threadId));
			}

		} catch (Exception e) {
			logger.warn("Error during teardown for " + testName + ": " + e.getMessage());
		} finally {
			// Always cleanup context to prevent thread-local leaks
			cleanupContext(testName);
		}
	}

	/**
	 * Destroys a driver immediately without pooling.
	 * Use when you need to force-quit a driver mid-test.
	 * 
	 * @param driver driver to destroy
	 */
	public void destroy(RemoteWebDriver driver) {
		if (driver == null) {
			logger.warn("Cannot destroy null driver");
			return;
		}

		ensurePoolInitialized();

		try {
			driverPool.destroy(driver);
			logger.info("Driver destroyed manually [ID: " + System.identityHashCode(driver) + "]");

			// Clean up context if it matches
			DriverContext context = contextThreadLocal.get();
			if (context != null && context.getDriver() == driver) {
				contextThreadLocal.remove();
			}
		} catch (Exception e) {
			logger.warn("Error destroying driver: " + e.getMessage());
		}
	}

	/**
	 * Shuts down the pool and releases all resources.
	 * Should be called once after all tests (e.g., @AfterSuite).
	 * 
	 * <p>
	 * <b>THREAD-SAFE:</b> Synchronized to prevent concurrent shutdown attempts.
	 */
	public synchronized void shutdownPool() {
		if (!poolInitialized || driverPool == null) {
			logger.warn("Pool not initialized or already shutdown");
			return;
		}

		logger.info("Shutting down driver pool...");
		logger.info("Final statistics:\n" + getPoolStatistics());

		try {
			driverPool.close();
			poolInitialized = false;
		} catch (Exception e) {
			logger.error("Error during pool shutdown: " + e.getMessage());
		} finally {
			driverPool = null;
			poolConfig = null;
			testMetadata.clear();
		}

		logger.info("Pool shutdown complete");
	}

	/**
	 * Gets the current thread's WebDriver instance.
	 * 
	 * @return WebDriver instance
	 * @throws IllegalStateException if no driver available
	 */
	public RemoteWebDriver getDriver() {
		DriverContext context = contextThreadLocal.get();

		if (context == null || context.getDriver() == null) {
			throw new IllegalStateException(
					"No driver available for thread " + Thread.currentThread().getId() +
							". Ensure setupDriver() was called in @BeforeMethod.");
		}

		return context.getDriver();
	}

	/**
	 * Gets the current thread's WebDriverWait instance.
	 * 
	 * @return WebDriverWait instance
	 * @throws IllegalStateException if no wait available
	 */
	public WebDriverWait getWait() {
		DriverContext context = contextThreadLocal.get();

		if (context == null || context.getWait() == null) {
			throw new IllegalStateException(
					"No wait available for thread " + Thread.currentThread().getId() +
							". Ensure setupDriver() was called in @BeforeMethod.");
		}

		return context.getWait();
	}

	/**
	 * Gets current pool statistics.
	 * 
	 * @return formatted statistics string
	 */
	public String getPoolStatistics() {
		if (!poolInitialized || driverPool == null) {
			return "Pool not initialized";
		}
		return driverPool.getStatistics();
	}

	// ========================================================================
	// PRIVATE HELPER METHODS
	// ========================================================================

	/**
	 * Loads pool configuration from multiple sources with priority.
	 * Default values come from the framework property file via ConfigManager.
	 */
	private PoolConfig loadConfiguration(ConcurrentMap<String, String> testngParams) {
		PoolConfig.Builder builder = new PoolConfig.Builder();

		// ── Browser scope ─────────────────────────────────────────────────────
		// Builder() defaults to both CHROME + FIREFOX.  Replace with only the
		// browser configured for this suite run so pre-warm never launches a
		// browser process that no test will ever borrow.
		String configuredBrowser = resolveBrowserId(
				loadStringConfig("BROWSER", "browser", testngParams, config().getBrowserName()));
		builder.clearSupportedBrowsers()
		       .addSupportedBrowser(configuredBrowser);
		logger.info("Pool will pre-warm: " + configuredBrowser);

		// Threaded through as data (not read directly from ConfigManager by
		// design.patterns.factory.browser.* — see BrowserRegistry's GRID_* providers
		// and RemoteGridBrowser's two-constructor design) so the grid hub URL
		// resolves the same way the other config values on this Builder do.
		builder.gridHubUrl(config().getGridHubUrl());

		// ── Size limits ───────────────────────────────────────────────────────
		int maxPoolSize = loadIntConfig("MAX_POOL_SIZE", "maxPoolSize",
				testngParams, config().getPoolMaxSize());
		builder.maxPoolSize(maxPoolSize);

		int minPoolSize = loadIntConfig("MIN_POOL_SIZE", "minPoolSize",
				testngParams, config().getPoolMinSize());
		// Cap minPoolSize at maxPoolSize to prevent creating extra empty browser instances
		builder.minPoolSize(Math.min(minPoolSize, maxPoolSize));

		// ── Idle eviction ─────────────────────────────────────────────────────
		int maxIdleMinutes = loadIntConfig("MAX_IDLE_MINUTES", "maxIdleMinutes",
				testngParams, config().getPoolMaxIdleMinutes());
		builder.maxIdleMinutes(maxIdleMinutes);

		// ── Borrow timeout ────────────────────────────────────────────────────
		// Seconds a caller blocks in acquire() when the pool is at max capacity
		// before a DriverAcquisitionException is thrown.
		int borrowTimeoutSeconds = loadIntConfig("BORROW_TIMEOUT", "borrowTimeoutSeconds",
				testngParams, config().getPoolBorrowTimeoutSeconds());
		builder.borrowTimeoutSeconds(borrowTimeoutSeconds);

		// ── Reuse cap ─────────────────────────────────────────────────────────
		// Hard limit on use-count per driver before it is retired.  Prevents
		// Chrome / Firefox memory and file-descriptor leaks in long suite runs.
		int maxReuseCount = loadIntConfig("MAX_REUSE_COUNT", "maxReuseCount",
				testngParams, config().getPoolMaxReuseCount());
		builder.maxReuseCount(maxReuseCount);

		// ── Feature toggles ───────────────────────────────────────────────────
		boolean healthCheck = loadBoolConfig("HEALTH_CHECK_ENABLED", "healthCheck",
				testngParams, true);
		builder.healthCheckEnabled(healthCheck);

		boolean stateReset = loadBoolConfig("STATE_RESET_ENABLED", "stateReset",
				testngParams, true);
		builder.stateResetEnabled(stateReset);

		// ProjectConfig exposes isCloseAfterEach() (boolean getter convention)
		boolean closeAfterEach = loadBoolConfig("CLOSE_AFTER_EACH", "closeAfterEach",
				testngParams, config().isCloseAfterEach());
		builder.closeAfterEach(closeAfterEach);

		// ── Driver timeouts ───────────────────────────────────────────────────
		// Carried on PoolConfig (not read directly from ConfigManager by
		// BrowserFactory/design.patterns.factory.browser.* — see that package's
		// package-info.java) so design.patterns.* doesn't need to import
		// com.framework.config.data for these three values.
		int pageLoadTimeoutSeconds = loadIntConfig("PAGE_LOAD_TIMEOUT", "pageLoadTimeout",
				testngParams, config().getPageLoadTimeout());
		builder.pageLoadTimeoutSeconds(pageLoadTimeoutSeconds);

		int scriptTimeoutSeconds = loadIntConfig("SCRIPT_TIMEOUT", "scriptTimeout",
				testngParams, config().getScriptTimeout());
		builder.scriptTimeoutSeconds(scriptTimeoutSeconds);

		int implicitWaitSeconds = loadIntConfig("IMPLICIT_WAIT", "implicitWait",
				testngParams, config().getImplicit());
		builder.implicitWaitSeconds(implicitWaitSeconds);

		return builder.build();
	}

	/**
	 * Determines the browser id (a {@link BrowserRegistry} key) with priority order.
	 */
	private String determineBrowserId(Method method, ConcurrentMap<String, String> params) {
		// Priority 1: Method parameter
		if (params != null && params.containsKey("browser")) {
			return resolveBrowserId(params.get("browser"));
		}

		// Priority 2: Method annotation
		BrowserConfig methodConfig = method.getAnnotation(BrowserConfig.class);
		if (methodConfig != null) {
			return resolveBrowserId(methodConfig.value());
		}

		// Priority 3: Class annotation
		BrowserConfig classConfig = method.getDeclaringClass()
				.getAnnotation(BrowserConfig.class);
		if (classConfig != null) {
			return resolveBrowserId(classConfig.value());
		}

		// Priority 4: CI/CD environment
		String envBrowser = System.getenv("BROWSER");
		if (envBrowser != null && !envBrowser.isEmpty()) {
			return resolveBrowserId(envBrowser);
		}

		// Priority 5: System property
		String sysBrowser = System.getProperty("browser");
		if (sysBrowser != null && !sysBrowser.isEmpty()) {
			return resolveBrowserId(sysBrowser);
		}

		// Priority 6: Config file defaults
		String cfgBrowser = config().getBrowserName();
		if (cfgBrowser != null && !cfgBrowser.isEmpty()) {
			return resolveBrowserId(cfgBrowser);
		}

		// Priority 7: Built-in fallback
		return "CHROME";
	}

	/**
	 * Determines the URL the browser should open, evaluated in priority order:
	 *
	 * <ol>
	 *   <li>TestNG parameter {@code url} (per-method or per-test override)</li>
	 *   <li>TestNG parameter {@code baseUrl} (suite/test-level direct URL)</li>
	 *   <li>{@code @BrowserConfig(url="...")} on the test method</li>
	 *   <li>CI/CD environment variable {@code BASE_URL}</li>
	 *   <li>JVM system property {@code -DbaseUrl=...}</li>
	 *   <li>{@code ProjectConfig.appUrl} — resolved by {@code ProjectDirector}
	 *       from the project's properties file using the {@code environment} parameter
	 *       (dev → devUrl, qa → qaUrl, prod → prodUrl)</li>
	 * </ol>
	 *
	 * <p>Steps 1–5 allow teams to override the URL at any granularity without
	 * touching the properties file. Step 6 is the normal path when running with
	 * just {@code configClass} + {@code environment} in the TestNG XML.
	 */
	private String determineUrl(Method method, ConcurrentMap<String, String> params) {
		// Priority 1: explicit "url" TestNG parameter (per-method)
		if (params != null) {
			String url = params.get("url");
			if (url != null && !url.isEmpty()) {
				return url;
			}

			// Priority 2: "baseUrl" TestNG parameter (suite / test level)
			String baseUrl = params.get("baseUrl");
			if (baseUrl != null && !baseUrl.isEmpty()) {
				return baseUrl;
			}
		}

		// Priority 3: @BrowserConfig annotation on the test method
		BrowserConfig methodConfig = method.getAnnotation(BrowserConfig.class);
		if (methodConfig != null && !methodConfig.url().isEmpty()) {
			return methodConfig.url();
		}

		// Priority 4: CI/CD environment variable BASE_URL
		String envUrl = System.getenv("BASE_URL");
		if (envUrl != null && !envUrl.isEmpty()) {
			return envUrl;
		}

		// Priority 5: JVM system property -DbaseUrl=...
		String sysUrl = System.getProperty("baseUrl");
		if (sysUrl != null && !sysUrl.isEmpty()) {
			return sysUrl;
		}

		// Priority 6: URL resolved from configClass + environment in ProjectDirector
		// (e.g. environment=dev → alfaDOCK.dev.url = https://www.alfadock-pack.com/)
		String cfgUrl = config().getAppUrl();
		if (cfgUrl != null && !cfgUrl.isEmpty()) {
			return cfgUrl;
		}

		throw new IllegalStateException(
			"No URL could be resolved. Add one of the following to your TestNG XML:\n" +
			"  <parameter name=\"baseUrl\"     value=\"https://your-app.com/\"/>  (direct override)\n" +
			"  <parameter name=\"environment\" value=\"dev\"/>                    (routes via configClass)");
	}

	private String resolveBrowserId(String browserName) {
		String id = browserName.toUpperCase().trim();
		if (BrowserRegistry.isRegistered(id)) {
			return id;
		}
		logger.warn("Invalid/unregistered browser id: '" + browserName + "', defaulting to CHROME");
		return "CHROME"; // built-in fallback; property file default preferred
	}

	// Delegate to the shared ConfigResolver (see its Javadoc) so this priority
	// chain has exactly one implementation, shared with ProjectDirector's
	// project-config resolution instead of being copy-pasted and drifting.

	private int loadIntConfig(String envKey, String paramKey,
			ConcurrentMap<String, String> params, int defaultValue) {
		return ConfigResolver.resolveInt(paramKey, envKey, params, defaultValue);
	}

	private boolean loadBoolConfig(String envKey, String paramKey,
			ConcurrentMap<String, String> params, boolean defaultValue) {
		return ConfigResolver.resolveBoolean(paramKey, envKey, params, defaultValue);
	}

	private String loadStringConfig(String envKey, String paramKey,
			ConcurrentMap<String, String> params, String defaultValue) {
		return ConfigResolver.resolveString(paramKey, envKey, params, defaultValue);
	}

	private int getWaitTimeout(ConcurrentMap<String, String> params) {
		return loadIntConfig("WAIT_TIMEOUT", "waitTimeout", params, config().getDefaultWaitTimeout());
	}

	private void ensurePoolInitialized() {
		if (!poolInitialized || driverPool == null) {
			throw new IllegalStateException(
					"Pool not initialized. Call initializePool() in @BeforeSuite first.");
		}
	}

	private void logTestResult(String testName, boolean passed, DriverContext context) {
		TestMetadata metadata = testMetadata.get(testName);

		if (metadata != null) {
			long duration = System.currentTimeMillis() - metadata.getStartTime();
			logger.info(String.format("Test %s: %s | Duration: %dms | Browser: %s",
					passed ? "PASSED" : "FAILED", testName, duration,
					metadata.getBrowserType()));
		}
	}

	private void cleanupContext(String testName) {
		contextThreadLocal.remove();
		testMetadata.remove(testName);
			logger.debug("Cleaned up context for: " + testName);
	}

	private String getTestName(Method method) {
		return method.getDeclaringClass().getSimpleName() + "." + method.getName();
	}

	// ========================================================================
	// INNER CLASSES
	// ========================================================================

	/**
	 * Thread-local driver context holder.
	 */
	private static class DriverContext {
		private final RemoteWebDriver driver;
		private final WebDriverWait wait;
		private final String testName;
		private final String browserType;

		DriverContext(RemoteWebDriver driver, WebDriverWait wait,
				String testName, String browserType) {
			this.driver = driver;
			this.wait = wait;
			this.testName = testName;
			this.browserType = browserType;
		}

		RemoteWebDriver getDriver() {
			return driver;
		}

		WebDriverWait getWait() {
			return wait;
		}

		String getTestName() {
			return testName;
		}

		String getBrowserType() {
			return browserType;
		}
	}

	/**
	 * Test metadata for tracking and reporting.
	 */
	private static class TestMetadata {
		private final String browserType;
		private final String url;
		private final long startTime;

		TestMetadata(String browserType, String url, long startTime) {
			this.browserType = browserType;
			this.url = url;
			this.startTime = startTime;
		}

		String getBrowserType() {
			return browserType;
		}

		long getStartTime() {
			return startTime;
		}
	}
}
