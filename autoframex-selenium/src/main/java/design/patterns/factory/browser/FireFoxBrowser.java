package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FireFoxBrowser implements Browser {

	private static final Logger logger = LoggerFactory.getLogger(FireFoxBrowser.class);

	private static final FireFoxBrowser INSTANCE = new FireFoxBrowser(null);

	/** The pool's headless setting; {@code null} = not configured, so {@code -Dheadless} / {@code HEADLESS} decide. */
	private final Boolean configuredHeadless;

	private FireFoxBrowser(Boolean configuredHeadless) {
		this.configuredHeadless = configuredHeadless;
	}

	public static FireFoxBrowser getInstance() {
		return INSTANCE;
	}

	/** A provider that follows the pool's headless setting; the shared instance when the pool has none. */
	public static FireFoxBrowser forConfig(design.patterns.object.pool.PoolConfig config) {
		return config == null || config.getHeadless() == null ? INSTANCE : new FireFoxBrowser(config.getHeadless());
	}

	@Override
	public RemoteWebDriver launchBrowser() {
		return new FirefoxDriver(getBrowserOptions());
	}

	@Override
	public RemoteWebDriver launchBrowser(Capabilities capabilities) {
		FirefoxOptions options = getBrowserOptions();
		if (capabilities != null) {
			options.merge(capabilities);
		}
		return new FirefoxDriver(options);
	}

	/** Package-private (not {@code private}) so same-package unit tests can inspect
	 * the built {@link FirefoxOptions} without launching a real Firefox process. */
	FirefoxOptions getBrowserOptions() {
		// 1. Initialize Options first
		FirefoxOptions options = new FirefoxOptions();

		// 2. Define Preferences (To disable password saves and notifications)
		options.addPreference("signon.rememberSignons", false);
		options.addPreference("dom.webnotifications.enabled", false);
		options.addPreference("dom.disable_open_during_load", false); // disable popup blocker

//		// 3. Performance/Behavior optimizations
//		options.addArguments("-private"); // Start in private browsing for clean session

		// 4. Headless mode from environment
		if (isHeadlessMode()) {
			options.addArguments("-headless");
			options.addArguments("--width=1920");
			options.addArguments("--height=1080");
		}

		// 5. Security
		options.setAcceptInsecureCerts(BrowserSecurityPolicy.acceptInsecureCerts()); // not for production runs

		return options;
	}

	/**
	 * Checks if headless mode should be enabled.
	 * 
	 * @return true if headless mode enabled
	 */
	private boolean isHeadlessMode() {
		if (configuredHeadless != null) {
			return configuredHeadless;
		}
		String headless = System.getProperty("headless",
				System.getenv("HEADLESS"));
		return "true".equalsIgnoreCase(headless);
	}

}