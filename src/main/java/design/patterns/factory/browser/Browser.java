package design.patterns.factory.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * 
 * @author rajesh
 *
 */
public interface Browser {
	
	/**
	 * 
	 * @return
	 */
	RemoteWebDriver launchBrowser();
	
	/**
	 * 
	 * @param capabilities
	 * @return
	 */
	RemoteWebDriver launchBrowser(Capabilities capabilities);

	


}
