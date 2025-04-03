package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;


import java.util.logging.*;

public class ChromeBrowser implements Browser{

	private static final Logger logger =Logger.getLogger(ChromeBrowser.class.getName());

	/**
	 * 
	 */
	@Override
	public RemoteWebDriver launchBrowser() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-maximized"); 
		options.addArguments("--disable-notifications"); 
		options.addArguments("--incognito");
		return new ChromeDriver(options);	
	}

	@Override
	public RemoteWebDriver launchBrowser(Capabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}





}