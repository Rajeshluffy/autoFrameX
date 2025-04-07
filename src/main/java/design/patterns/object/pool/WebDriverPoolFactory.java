package design.patterns.object.pool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import org.openqa.selenium.remote.RemoteWebDriver;
import design.patterns.factory.browser.BrowserType;
import design.patterns.factory.browser.WebDriverFactoryInterface;

public class WebDriverPoolFactory  {

	private static final Logger logger = Logger.getLogger(WebDriverPoolFactory.class.getName());


	//Factory Object for Browser factory
	private WebDriverFactoryInterface driverFactory;

	//Store the browser Type and Queue
	private ConcurrentMap<BrowserType ,BlockingQueue<RemoteWebDriver>> driverPool;

	// Map to track : WebDriver , BrowserType
	private ConcurrentMap<RemoteWebDriver ,BrowserType> driverToBrowserKey;


	//Constructor
	public WebDriverPoolFactory(WebDriverFactoryInterface factory) {
		this.driverFactory=factory;
		driverPool = new ConcurrentHashMap<BrowserType ,BlockingQueue<RemoteWebDriver>>();
		driverToBrowserKey=new ConcurrentHashMap<RemoteWebDriver,BrowserType>();

		//  for every browser type, create new Blocking Queue
		for (BrowserType browserType : BrowserType.values()) {
			driverPool.put(browserType, new LinkedBlockingQueue<RemoteWebDriver>());
		}
	}


	public RemoteWebDriver getDriverFactory(BrowserType browserType,String url) {

		BlockingQueue<RemoteWebDriver> queue = driverPool.get(browserType);
		RemoteWebDriver driver = queue.poll();
		if(driver==null) {
			driver = driverFactory.createDriver(browserType);
			driverToBrowserKey.put(driver, browserType);	
		}else {
			logger.info("Reusing the existing driver "+driver.hashCode());
		}
		driverFactory.setupDriver(url);

		return driver;
	}

	public void releaseDriver(RemoteWebDriver driver) {
		BrowserType browserType = driverToBrowserKey.get(driver);
		if(browserType !=null) {
			BlockingQueue<RemoteWebDriver> queue = driverPool.get(browserType);
			queue.offer(driver);
		}else {
			driver.quit();
		}
	}


}
