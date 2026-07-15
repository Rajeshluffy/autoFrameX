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
 * {@code GRID_EDGE} {@link BrowserType} enum constants, and by
 * {@link BrowserRegistry}'s built-in providers of the same names.
 *
 * <p>Two constructors, two different sources for the hub URL:
 * <ul>
 *   <li>{@link #RemoteGridBrowser(String, String)} — hub URL supplied directly
 *       (used by {@link BrowserRegistry}'s providers, which receive it from
 *       {@code PoolConfig.getGridHubUrl()} at driver-creation time). This is
 *       the path every real test suite in this repo goes through, and it
 *       never touches {@code ConfigManager}.</li>
 *   <li>{@link #RemoteGridBrowser(String)} — no URL supplied; falls back to
 *       reading {@code ConfigManager} lazily inside {@link #launchBrowser(Capabilities)}.
 *       Kept only for backward compatibility with a caller holding a raw
 *       {@code BrowserType} enum constant (whose {@code GRID_*} constants are
 *       still bound with this 1-arg constructor) and calling
 *       {@code .launchBrowser()} directly, bypassing the pool.</li>
 * </ul>
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

    /** Hub URL supplied at construction time; {@code null} means "resolve lazily via ConfigManager." */
    private final String gridHubUrl;

    public RemoteGridBrowser(String browserName) {
        this.browserName = browserName.toLowerCase();
        this.gridHubUrl = null;
    }

    /** Preferred constructor — hub URL supplied directly, no {@code ConfigManager} dependency. */
    public RemoteGridBrowser(String browserName, String gridHubUrl) {
        this.browserName = browserName.toLowerCase();
        this.gridHubUrl = gridHubUrl;
    }

    @Override
    public RemoteWebDriver launchBrowser() {
        return launchBrowser(null);
    }

    @Override
    public RemoteWebDriver launchBrowser(Capabilities capabilities) {
        String hubUrl = gridHubUrl != null ? gridHubUrl
                : ConfigManager.getInstance().getConfig().getGridHubUrl();
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
