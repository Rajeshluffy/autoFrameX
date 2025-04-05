package autoFrameX;

import java.util.logging.Logger;

import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserType;
import design.patterns.factory.browser.WebDriverFactoryInterface;
import design.patterns.object.pool.WebDriverPoolFactory;

public class SimpleTest {
	private static final Logger logger = Logger.getLogger(SimpleTest.class.getName());

	public static void main(String[] args) {

		// Initialize >> Factory
		WebDriverFactoryInterface factory = new BrowserFactory();

		// Initialize >> Object Pool
		WebDriverPoolFactory pool = new WebDriverPoolFactory(factory);

		// Applications
		String url1 = "https://google.com";
		String url2 = "https://leafground.com";

		// Chrome Options
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-fullscreen");
		options.addArguments("--headless");

		ChromeOptions options3 = new ChromeOptions();
		options3.addArguments("--headless");
		options3.addArguments("--start-fullscreen");

		FirefoxOptions options1 = new FirefoxOptions();
		//options1.addArguments("");

		// test 1 >> chrome >> url1 
		// test 2 >> chrome >> url2
		// test 3 >> firefox >> url1
		// test 3 >> firefox >> url2

		testOne(pool, BrowserType.CHROME, url1);
		testTwo(pool, BrowserType.CHROME, url2);
		testThree(pool, BrowserType.FIREFOX, url1);
		testFour(pool,BrowserType.FIREFOX, url2);


	}
	
	private static void testFour(WebDriverPoolFactory pool, BrowserType browser, String url2) {
		logger.info("The test 4 started");
		RemoteWebDriver driver = pool.getDriverFactory(browser,url2);
		
		logger.info("Title >> "+driver.getTitle());
		pool.releaseDrver(driver);
		logger.info("The test 4 completed");
	}

	private static void testThree(WebDriverPoolFactory pool, BrowserType browser, String url1) {
		logger.info("The test 3 started");
		RemoteWebDriver driver = pool.getDriverFactory(browser,url1);
		logger.info("Title >> "+driver.getTitle());
		pool.releaseDrver(driver);
		logger.info("The test 3 completed");
	}
	
	private static void testTwo(WebDriverPoolFactory pool, BrowserType browser, String url2) {
		logger.info("The test 2 started");
		RemoteWebDriver driver = pool.getDriverFactory(browser,url2);
		logger.info("Title >> "+driver.getTitle());
		pool.releaseDrver(driver);
		logger.info("The test 2 completed");

	}

	private static void testOne(WebDriverPoolFactory pool, BrowserType browser, String url1) {

		logger.info("The test 1 started");
		RemoteWebDriver driver = pool.getDriverFactory(browser,url1);
		logger.info("Title >> "+driver.getTitle());
		pool.releaseDrver(driver);
		logger.info("The test 1 completed");


	}
}
