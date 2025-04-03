package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.RemoteWebDriver;


import java.util.logging.*;

public class FireFoxBrowser implements Browser{

	private static final Logger logger =Logger.getLogger(FireFoxBrowser.class.getName());

	@Override
	public RemoteWebDriver launchBrowser() {
		return new FirefoxDriver();	
	}


	
	@Override
	public RemoteWebDriver launchBrowser(Capabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}


}