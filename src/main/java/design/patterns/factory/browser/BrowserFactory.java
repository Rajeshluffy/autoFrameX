package design.patterns.factory.browser;

import java.time.Duration;
import java.util.logging.Logger;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.config.data.ConfigManager;

import design.patterns.object.pool.PoolConfig;

/**
 * Default implementation of {@link WebDriverFactoryInterface}.
 *
 * <p>
 * Uses the {@link BrowserType} Enum Factory pattern to delegate browser
 * instantiation — zero direct {@code new} calls for browser objects. The
 * object pool ({@link design.patterns.object.pool.WebDriverPoolFactory}) calls
 * this factory whenever it needs a fresh driver, so timeouts are configured
 * here once rather than scattered across tests.
 *
 * <p>
 * Design patterns applied:
 * <ul>
 * <li><b>Factory Pattern</b> — encapsulates driver creation logic</li>
 * <li><b>Enum Factory</b> — {@link BrowserType} dispatches to the right
 * browser</li>
 * </ul>
 *
 * @author Framework Team
 * @version 2.1
 */
public class BrowserFactory implements WebDriverFactoryInterface {

	private static final Logger logger = Logger.getLogger(BrowserFactory.class.getName());

	// Timeout constants (seconds)
	private static final int PAGE_LOAD_TIMEOUT = ConfigManager.getInstance().getConfig().getPageLoadTimeout();
	private static final int SCRIPT_TIMEOUT = ConfigManager.getInstance().getConfig().getScriptTimeout();
	private static final int IMPLICIT_WAIT = ConfigManager.getInstance().getConfig().getImplicit();

	// -------------------------------------------------------------------------
	// Primary factory method used by the pool
	// -------------------------------------------------------------------------

	/**
	 * Creates a fully-configured {@link RemoteWebDriver} for the given browser
	 * type. Called by {@link design.patterns.object.pool.WebDriverPoolFactory}
	 * each time a new driver is needed.
	 *
	 * @param browserType the enum value that identifies the browser
	 * @param config      pool configuration (reserved for future use)
	 * @return a new, maximized, timeout-configured WebDriver instance
	 */
	public RemoteWebDriver createDriver(BrowserType browserType, PoolConfig config) {
		logger.fine("Creating driver via Enum Factory: " + browserType);
		RemoteWebDriver driver = browserType.launchBrowser();
		configureTimeouts(driver);
		return driver;
	}

	// -------------------------------------------------------------------------
	// WebDriverFactoryInterface overrides
	// -------------------------------------------------------------------------

	/** Creates a driver without pool config — delegates to Enum Factory. */
	@Override
	public RemoteWebDriver createDriver(BrowserType browserType) {
		return createDriver(browserType, (PoolConfig) null);
	}

	/** Capabilities-based creation — reserved for remote/grid execution. */
	@Override
	public RemoteWebDriver createDriver(BrowserType browserType, Capabilities capabilities) {
		// For RemoteWebDriver / Selenium Grid support in future sprints
		logger.warning("Capabilities-based driver creation not yet implemented; falling back to default.");
		return createDriver(browserType, (PoolConfig) null);
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	/**
	 * Applies standard timeouts to a freshly-created driver.
	 *
	 * @param driver the driver to configure
	 */
	private void configureTimeouts(RemoteWebDriver driver) {
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
		driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(SCRIPT_TIMEOUT));
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(IMPLICIT_WAIT));
	}
}
