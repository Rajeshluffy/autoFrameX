package design.patterns.factory.browser;

import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Enum Factory for the framework's built-in browser types.
 *
 * <p>Local browsers ({@code CHROME}, {@code FIREFOX}, {@code EDGE}) launch a
 * driver process on the local machine.  Grid browsers ({@code GRID_CHROME},
 * {@code GRID_FIREFOX}, {@code GRID_EDGE}) connect to a Selenium Grid hub
 * whose URL is resolved via {@code ConfigManager} at driver-creation time.
 *
 * <p>Use {@code GRID_*} types to run tests on a remote Selenium Grid node
 * without changing any test code — just switch the TestNG parameter
 * {@code browser=GRID_CHROME} or set the environment variable
 * {@code BROWSER=GRID_CHROME}.
 *
 * <p>Kept as a small, stable, backward-compatible surface for any caller that
 * already holds a {@code BrowserType} constant (e.g. via
 * {@code BrowserFactory.createDriver(BrowserType, ...)}). The pool itself no
 * longer uses this enum as its Map key — {@link BrowserRegistry} is the
 * open, {@code String}-keyed mechanism for both built-in and custom browsers,
 * and supports registering any number of custom browsers (this enum's old
 * {@code CUSTOM} constant supported only one at a time and has been removed).
 *
 * @author Framework Team
 * @version 2.1
 */
public enum BrowserType {

    // ── Local browsers ────────────────────────────────────────────────────────
    CHROME(ChromeBrowser.getInstance()),
    FIREFOX(FireFoxBrowser.getInstance()),
    EDGE(EdgeBrowser.getInstance()),

    // ── Selenium Grid browsers ────────────────────────────────────────────────
    GRID_CHROME(new RemoteGridBrowser("chrome")),
    GRID_FIREFOX(new RemoteGridBrowser("firefox")),
    GRID_EDGE(new RemoteGridBrowser("edge"));

    private final Browser browser;

    BrowserType(Browser browser) {
        this.browser = browser;
    }

    public Browser getBrowser() {
        return browser;
    }

    public RemoteWebDriver launchBrowser() {
        return browser.launchBrowser();
    }

    /** Returns {@code true} when this type routes through Selenium Grid. */
    public boolean isRemote() {
        return this == GRID_CHROME || this == GRID_FIREFOX || this == GRID_EDGE;
    }
}
