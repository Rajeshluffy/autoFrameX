package design.patterns.factory.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Singleton Edge browser factory.
 *
 * <p>Mirrors the Chrome and Firefox implementations: each instance is a
 * singleton because browser options are stateless and constructing new
 * instances per-driver is unnecessary.  The Enum Factory in
 * {@link BrowserType#EDGE} holds the single shared instance.
 *
 * <p>Headless mode is controlled by the {@code headless} JVM property or
 * {@code HEADLESS} environment variable, consistent with the other browsers.
 *
 * @author Framework Team
 * @version 1.0
 */
public class EdgeBrowser implements Browser {

    private static final Logger logger = LoggerFactory.getLogger(EdgeBrowser.class);

    private static final EdgeBrowser INSTANCE = new EdgeBrowser();

    private EdgeBrowser() {}

    public static EdgeBrowser getInstance() {
        return INSTANCE;
    }

    @Override
    public RemoteWebDriver launchBrowser() {
        return new EdgeDriver(getBrowserOptions());
    }

    @Override
    public RemoteWebDriver launchBrowser(Capabilities capabilities) {
        EdgeOptions options = getBrowserOptions();
        if (capabilities != null) {
            options.merge(capabilities);
        }
        return new EdgeDriver(options);
    }

    /** Package-private (not {@code private}) so same-package unit tests can inspect
     * the built {@link EdgeOptions} without launching a real Edge process. */
    EdgeOptions getBrowserOptions() {
        EdgeOptions options = new EdgeOptions();

        options.addArguments("--disable-extensions");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--log-level=3");
        options.setAcceptInsecureCerts(true);

        if (isHeadlessMode()) {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
        }

        return options;
    }

    private boolean isHeadlessMode() {
        String headless = System.getProperty("headless", System.getenv("HEADLESS"));
        return "true".equalsIgnoreCase(headless);
    }
}
