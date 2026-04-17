package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import design.patterns.object.pool.PoolConfig;

/**
 * Factory interface for creating WebDriver instances.
 * Implements Factory pattern for driver creation abstraction.
 * 
 * @author Framework Team
 * @version 2.0
 */
public interface WebDriverFactoryInterface {
	/**
     * Creates a new WebDriver instance for the specified browser type.
     * 
     * @param browserType browser type (chrome, firefox, etc.)
     * @param config pool configuration for driver setup
     * @return configured RemoteWebDriver instance
     * @throws DriverCreationException if creation fails
     */
    RemoteWebDriver createDriver(BrowserType browserType, PoolConfig config);
	RemoteWebDriver createDriver(BrowserType browsertype);
	RemoteWebDriver createDriver(BrowserType browsertype,Capabilities capabilities);
	
}


