package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.util.logging.*;

public class FireFoxBrowser implements Browser {

	private static final Logger logger = Logger.getLogger(FireFoxBrowser.class.getName());

	private static final FireFoxBrowser INSTANCE = new FireFoxBrowser();

	private FireFoxBrowser() {
		// Private constructor to prevent instantiation
	}

	public static FireFoxBrowser getInstance() {
		return INSTANCE;
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

	private FirefoxOptions getBrowserOptions() {
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
		options.setAcceptInsecureCerts(true);

		return options;
	}

	/**
	 * Checks if headless mode should be enabled.
	 * 
	 * @return true if headless mode enabled
	 */
	private boolean isHeadlessMode() {
		String headless = System.getProperty("headless",
				System.getenv("HEADLESS"));
		return "true".equalsIgnoreCase(headless);
	}

}