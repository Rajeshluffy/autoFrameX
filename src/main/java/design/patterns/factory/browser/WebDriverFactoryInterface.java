package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

public interface WebDriverFactoryInterface {
	RemoteWebDriver createDriver(BrowserType browsertype);
	RemoteWebDriver createDriver(BrowserType browsertype,Capabilities capabilities);

	void setupDriver(String url);
	void tearDownDriver();
}
