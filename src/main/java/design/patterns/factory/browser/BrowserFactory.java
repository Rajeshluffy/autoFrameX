package design.patterns.factory.browser;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

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

	private static final Logger logger = LoggerFactory.getLogger(BrowserFactory.class);

	// -------------------------------------------------------------------------
	// Primary factory method used by the pool
	// -------------------------------------------------------------------------

	/**
	 * Creates a fully-configured {@link RemoteWebDriver} for the given browser
	 * id. Called by {@link design.patterns.object.pool.WebDriverPoolFactory}
	 * each time a new driver is needed — this is the real implementation;
	 * {@link #createDriver(BrowserType, PoolConfig)} bridges into it for
	 * backward compatibility.
	 *
	 * @param browserId the {@link BrowserRegistry} id that identifies the browser
	 *                  (built-in ids match {@code BrowserType.name()}, e.g. {@code "CHROME"})
	 * @param config    pool configuration, including the driver timeouts to
	 *                  apply (see {@link #configureTimeouts}) and anything a
	 *                  registered provider needs (e.g. the grid hub URL);
	 *                  {@code null} falls back to {@link PoolConfig}'s own defaults
	 * @return a new, maximized, timeout-configured WebDriver instance
	 */
	public RemoteWebDriver createDriver(String browserId, PoolConfig config) {
		logger.debug("Creating driver via BrowserRegistry: " + browserId);
		PoolConfig effective = config != null ? config : new PoolConfig.Builder().build();
		RemoteWebDriver driver = BrowserRegistry.resolve(browserId, effective).launchBrowser();
		configureTimeouts(driver, effective);
		return driver;
	}

	/**
	 * Backward-compatible bridge for a caller holding a {@link BrowserType}
	 * constant directly — delegates to {@link #createDriver(String, PoolConfig)}.
	 *
	 * @param browserType the enum value that identifies the browser
	 * @param config      pool configuration; {@code null} falls back to
	 *                    {@link PoolConfig}'s own defaults
	 * @return a new, maximized, timeout-configured WebDriver instance
	 */
	public RemoteWebDriver createDriver(BrowserType browserType, PoolConfig config) {
		return createDriver(browserType.name(), config);
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
		logger.warn("Capabilities-based driver creation not yet implemented; falling back to default.");
		return createDriver(browserType, (PoolConfig) null);
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	/**
	 * Applies standard timeouts to a freshly-created driver, read from
	 * {@code config} rather than reaching into {@code ConfigManager} directly —
	 * this package is meant to be generic, framework-agnostic infrastructure
	 * with no knowledge of {@code com.framework.config.data} (see this
	 * package's {@code package-info.java}).
	 *
	 * @param driver the driver to configure
	 * @param config pool config carrying the three timeout values; {@code null}
	 *               falls back to {@code new PoolConfig.Builder().build()}'s
	 *               own defaults (the two call sites that don't have a real
	 *               {@code PoolConfig} yet — see {@link WebDriverFactoryInterface})
	 */
	private void configureTimeouts(RemoteWebDriver driver, PoolConfig config) {
		PoolConfig effective = config != null ? config : new PoolConfig.Builder().build();
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(effective.getPageLoadTimeoutSeconds()));
		driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(effective.getScriptTimeoutSeconds()));
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(effective.getImplicitWaitSeconds()));
	}
}
