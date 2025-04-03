package design.patterns.factory.browser;

import java.time.Duration;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;


public class BrowserFactory implements WebDriverFactoryInterface{

	private RemoteWebDriver driver;
	/**
	 * 
	 * @param browser
	 * @return
	 */
	public RemoteWebDriver createDriver(BrowserType browsertype) {
		switch (browsertype) {
		case CHROME:
			driver = new ChromeBrowser().launchBrowser();
			return driver;
		case FIREFOX:
			driver= new FireFoxBrowser().launchBrowser();
			return driver;

		default:
			driver= new ChromeBrowser().launchBrowser();
			return driver;
		}
		 
	}


	@Override
	public RemoteWebDriver createDriver(BrowserType browsertype, Capabilities capabilities) {
		switch (browsertype) {
		case CHROME:
			driver = new ChromeBrowser().launchBrowser();
			return driver;

		case FIREFOX:
			driver= new FireFoxBrowser().launchBrowser();
			return driver;

		default:
			driver= new ChromeBrowser().launchBrowser();
			return driver;
		}
	}


	@Override
	public void setupDriver(String url) {
		driver.get(url);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(30));
		driver.manage().window().maximize();
	}

}
