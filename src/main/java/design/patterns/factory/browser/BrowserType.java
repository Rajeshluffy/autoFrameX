package design.patterns.factory.browser;

/**
 * 
 * @author rajesh
 *
 */
import org.openqa.selenium.remote.RemoteWebDriver;

public enum BrowserType {
	CHROME(ChromeBrowser.getInstance()),
	FIREFOX(FireFoxBrowser.getInstance());

	private final Browser browser;

	BrowserType(Browser browser) {
		this.browser = browser;
	}

	public Browser getBrowser() {
		return browser;
	}

	public RemoteWebDriver launchBrowser() {
		return browser.launchBrowser();
	}
}
