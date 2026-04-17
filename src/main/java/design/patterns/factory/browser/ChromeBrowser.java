package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

public class ChromeBrowser implements Browser {

	private static final Logger logger = Logger.getLogger(ChromeBrowser.class.getName());

	private static final ChromeBrowser INSTANCE = new ChromeBrowser();

	private ChromeBrowser() {
		// Private constructor to prevent instantiation
	}

	public static ChromeBrowser getInstance() {
		return INSTANCE;
	}

	/**
	 * 
	 */
	@Override
	public RemoteWebDriver launchBrowser() {
		return new ChromeDriver(getBrowserOptions());
	}

	@Override
	public RemoteWebDriver launchBrowser(Capabilities capabilities) {
		ChromeOptions options = getBrowserOptions();
		if (capabilities != null) {
			options.merge(capabilities);
		}
		return new ChromeDriver(options);
	}

	private ChromeOptions getBrowserOptions() {
		// 1. Initialize Options first
		ChromeOptions options = new ChromeOptions();

		// 2. Define Preferences (To disable popups, password saves, and notifications)
		Map<String, Object> prefs = new HashMap<>();
		prefs.put("credentials_enable_service", false);
		prefs.put("profile.password_manager_enabled", false);
		prefs.put("profile.default_content_setting_values.notifications", 2); // 2 means Block
		options.setExperimentalOption("prefs", prefs);

		// 3. Remove "Chrome is being controlled by automated test software" bar
		options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
		options.setExperimentalOption("useAutomationExtension", false);

		// Performance optimizations
		options.addArguments("--disable-extensions");
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--disable-infobars");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--remote-allow-origins=*"); // Fixes ConnectionFailedException in newer Selenium versions
		options.addArguments("--guest"); // Log in as guest (prevents profile conflicts)

		// Headless mode from environment
		if (isHeadlessMode()) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
		}

		// Logging
		options.addArguments("--log-level=3");
		options.setAcceptInsecureCerts(true);
		options.addArguments("--start-maximized");
		options.addArguments("--disable-notifications");
		// options.addArguments("--incognito");

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