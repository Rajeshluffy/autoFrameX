package design.patterns.factory.browser;

import java.net.MalformedURLException;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.config.data.ConfigManager;

/**
 * Routes driver creation through a Selenium Grid hub.
 *
 * <p>Used by the {@code GRID_CHROME}, {@code GRID_FIREFOX}, and
 * {@code GRID_EDGE} {@link BrowserType} enum constants.  The hub URL is
 * read from {@code ConfigManager} at the moment a driver is requested, so
 * it respects runtime overrides ({@code -DgridHubUrl=...},
 * {@code GRID_HUB_URL} env var, TestNG parameter) set up before test
 * execution begins.
 *
 * <h3>Grid compatibility</h3>
 * <ul>
 *   <li>Selenium Grid 4 standalone/hub: {@code http://host:4444}</li>
 *   <li>Selenium Grid 3 (legacy): {@code http://host:4444/wd/hub}</li>
 * </ul>
 *
 * @author Framework Team
 * @version 1.0
 */
public class RemoteGridBrowser implements Browser {

    private static final Logger logger = LoggerFactory.getLogger(RemoteGridBrowser.class);

    /** Lower-case browser name used to select the right capability options. */
    private final String browserName;

    public RemoteGridBrowser(String browserName) {
        this.browserName = browserName.toLowerCase();
    }

    @Override
    public RemoteWebDriver launchBrowser() {
        return launchBrowser(null);
    }

    @Override
    public RemoteWebDriver launchBrowser(Capabilities capabilities) {
        String hubUrl = ConfigManager.getInstance().getConfig().getGridHubUrl();
        try {
            MutableCapabilities options = buildOptions();
            if (capabilities != null) {
                options.merge(capabilities);
            }
            logger.info("Connecting to Selenium Grid: " + hubUrl + " [browser=" + browserName + "]");
            return new RemoteWebDriver(new URL(hubUrl), options);
        } catch (MalformedURLException e) {
            throw new RuntimeException(
                "Invalid Selenium Grid hub URL: '" + hubUrl + "'. "
                + "Set autoFrameX.grid.hub.url in frameworkConfig.properties "
                + "or pass -DgridHubUrl=<url> at runtime.", e);
        }
    }

    private MutableCapabilities buildOptions() {
        switch (browserName) {
            case "firefox":           return new FirefoxOptions();
            case "edge":
            case "microsoftedge":     return new EdgeOptions();
            case "chrome":
            default:                  return new ChromeOptions();
        }
    }
}
