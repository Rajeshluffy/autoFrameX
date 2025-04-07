package design.patterns.factory.browser;

import java.time.Duration;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import cucumber.api.testng.AbstractTestNGCucumberTests;
import design.patterns.object.pool.WebDriverPoolFactory;


public class BrowserFactory extends AbstractTestNGCucumberTests implements WebDriverFactoryInterface{

	public RemoteWebDriver driver ;
	private static final ThreadLocal<RemoteWebDriver> remoteWebdriver = new ThreadLocal<RemoteWebDriver>();

	private static final ThreadLocal<WebDriverWait> wait = new  ThreadLocal<WebDriverWait>();

	public void setWait() {
		wait.set(new WebDriverWait(getDriver(), Duration.ofSeconds(10)));
	}


	public WebDriverWait getWait() {
		return wait.get();
	}	


	/**
	 * 
	 * @param browser
	 * @return
	 */
	public RemoteWebDriver createDriver(BrowserType browsertype) {
		switch (browsertype) {
		case CHROME:
			driver = new ChromeBrowser().launchBrowser();
			remoteWebdriver.set(driver);
			return driver;
		case FIREFOX:
			RemoteWebDriver driver = new FireFoxBrowser().launchBrowser();
			driver= new FireFoxBrowser().launchBrowser();
			remoteWebdriver.set(driver);
			return driver;

		default:
			driver = new ChromeBrowser().launchBrowser();
			remoteWebdriver.set(driver);
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

	public RemoteWebDriver  getDriver() {

		return remoteWebdriver.get();


	}


	@Override
	public void setupDriver(String url) {
		driver.get(url);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(30));
		driver.manage().window().maximize();
	}




}
