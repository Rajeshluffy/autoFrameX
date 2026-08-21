package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChromeBrowser implements Browser {

	private static final Logger logger = LoggerFactory.getLogger(ChromeBrowser.class);

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
		prefs.put("profile.password_manager_leak_detection", false); // Suppress "Change your password" breach popup
		prefs.put("profile.default_content_setting_values.notifications", 2); // 2 means Block
		
		// Per-thread download directory: under parallel execution, concurrent Chrome
		// instances sharing one folder would collide on identical downloaded filenames.
		// Each pooled driver is bound to one thread for its lifetime (DriverPoolManager's
		// ThreadLocal<DriverContext>), so keying on thread id keeps this collision-free
		// without needing a per-test unique id at options-build time.
		String downloadPath = System.getProperty("user.dir") + File.separator + "downloads"
				+ File.separator + Thread.currentThread().getId();
		new File(downloadPath).mkdirs();
		prefs.put("download.default_directory", downloadPath);
		prefs.put("download.prompt_for_download", false); // Automatically download without prompting
		prefs.put("download.directory_upgrade", true);
		prefs.put("safebrowsing.enabled", true);
		prefs.put("safebrowsing.disable_download_protection", true); // Bypasses Chrome's dangerous file warnings
		prefs.put("profile.default_content_settings.popups", 0); // Disable Save As popups
		
		// Handle Multiple Downloads permission for different Chrome versions
		prefs.put("profile.default_content_setting_values.automatic_downloads", 1); 
		prefs.put("profile.default_content_settings.automatic_downloads", 1); 
		prefs.put("profile.content_settings.exceptions.automatic_downloads.*.setting", 1);

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
		options.addArguments("--disable-gpu");
		// No fixed --remote-debugging-port: chromedriver auto-assigns a free port per
		// instance. A hardcoded port made concurrent pool instances (parallel="classes"/
		// "instances") collide on the same DevTools port, causing SessionNotCreatedException
		// ("disconnected: unable to connect to renderer") for every instance but the first.
		options.addArguments("--remote-allow-origins=*"); // Fixes ConnectionFailedException in newer Selenium versions
		// --guest removed: guest mode bypasses setExperimentalOption("prefs") settings,
		// which breaks download.prompt_for_download and automatic_downloads preferences

		// Headless mode from environment
		if (isHeadlessMode()) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
		}

		// Disable password breach detection popup ("Change your password — found in data breach")
		options.addArguments("--disable-features=PasswordLeakDetection");

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