package com.framework.cucumber.api.base;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.openqa.selenium.remote.RemoteWebDriver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.config.data.ConfigManager;
import com.framework.config.data.ProjectConfig;
import com.framework.selenium.api.base.SeleniumBase;

import design.patterns.object.pool.DriverPoolManager;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/**
 * Framework base for Cucumber step-definition classes that need a WebDriver.
 *
 * <h3>Usage</h3>
 * <p>Step classes that drive the browser extend this class. PicoContainer
 * injects the shared {@link CucumberScenarioContext} so state flows between
 * {@code @When} and {@code @Then} classes without static fields.
 *
 * <pre>
 * public class LoginSteps extends CucumberProjectBase {
 *
 *     private final MyScenarioContext ctx;
 *
 *     public LoginSteps(MyScenarioContext ctx) {
 *         this.ctx = ctx;
 *     }
 *
 *     {@literal @}When("I log in as {string}")
 *     public void login(String user) {
 *         new LoginPage().enterUsername(user).clickLogin();
 *     }
 * }
 * </pre>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   {@literal @}Before setUp()    — acquires driver from pool, navigates to app URL
 *   Scenario steps run
 *   {@literal @}After  tearDown() — captures screenshot on failure, returns driver to pool
 * </pre>
 *
 * <h3>API-only scenarios</h3>
 * <p>Step classes that only call REST APIs and never touch the browser do NOT
 * need to extend this class. Use {@link CucumberScenarioContext} directly and
 * let PicoContainer inject it.
 *
 * @see CucumberScenarioContext
 */
public abstract class CucumberProjectBase extends SeleniumBase {

    private static final Logger logger = LoggerFactory.getLogger(CucumberProjectBase.class);

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Acquires a WebDriver from the pool and navigates to the configured app URL.
     * Runs before every scenario that involves this step class.
     *
     * @param scenario the current Cucumber scenario (auto-injected)
     */
    @Before(order = 100)
    public void setUp(Scenario scenario) {
        logger.info("▶ @Before [Cucumber]: {}", scenario.getName());

        // Bind this thread to its config/pool context. No ITestContext is available
        // in a pure Cucumber hook, so resolution falls through to env var/system
        // property/default.
        String contextId = ConfigManager.resolveContextId(null);
        ConfigManager.bindContext(contextId);
        DriverPoolManager.bindContext(contextId);

        ConcurrentMap<String, String> params = new ConcurrentHashMap<>();
        params.put("scenarioName", scenario.getName());
        params.put("scenarioId",   scenario.getId());

        // API capture (when enabled for this project or suite) starts through the pool's
        // before-navigation hook, so the first page load is recorded too. The report folder is
        // reports/<run>/apicapture/<feature>/<scenario>_line<N>_<NN>/ - the scenario's line number
        // tells the rows of a Scenario Outline apart, and NN counts repeated runs.
        ProjectConfig config = ConfigManager.getInstance().getConfig();
        Consumer<RemoteWebDriver> captureStarter = null;
        if (config.isApiCaptureEnabled()) {
            String feature = featureNameOf(scenario.getUri());
            String dataKey = "line" + scenario.getLine();
            boolean mask = config.isApiCaptureMaskSecrets();
            captureStarter = browser -> {
                try {
                    startApiCapture(feature, scenario.getName(), dataKey, mask, browser);
                } catch (Exception e) {
                    logger.warn("API capture could not start for scenario {}: {}", scenario.getName(), e.getMessage());
                }
            };
        }

        getDriverManager().setupDriver(null, params, captureStarter);

        openAppUrl(getDriverManager().getDriver(), ConfigManager.getInstance().getConfig().getAppUrl(), scenario.getName());

        logger.info("Driver ready for scenario: {}", scenario.getName());
    }

    /**
     * Opens the application URL in the scenario's browser. A blank URL means "nothing to open".
     *
     * <p>A failure now fails the scenario. It used to be logged as a warning and the scenario went on against a
     * blank page, so its first step failed with an unrelated "element not found", or a step that asserted
     * nothing about the page passed on an app that was down. The {@code @After} hook still runs and returns
     * the driver.
     *
     * @throws com.framework.exception.FrameworkException if the page cannot be opened
     */
    static void openAppUrl(RemoteWebDriver driver, String url, String scenarioName) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            driver.get(url);
            logger.info("Navigated to: {}", url);
        } catch (RuntimeException e) {
            throw new com.framework.exception.FrameworkException("Scenario '" + scenarioName
                    + "' could not open the application URL " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns the driver to the pool (or destroys it on failure).
     * Captures a screenshot and attaches it to the Cucumber report on failure.
     *
     * @param scenario the current Cucumber scenario (auto-injected)
     */
    @After(order = 100)
    public void tearDown(Scenario scenario) {
        logger.info("@After [Cucumber]: {} [{}]", scenario.getName(),
                scenario.isFailed() ? "FAILED" : "PASSED");

        try {
            if (scenario.isFailed()) {
                byte[] screenshot = (byte[]) getDriverManager().getDriver()
                        .getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
                scenario.attach(screenshot, "image/png", "failure-screenshot");
                logger.info("Screenshot attached for failed scenario: {}", scenario.getName());
            }
        } catch (Exception e) {
            logger.warn("Could not capture screenshot for scenario {}: {}",
                    scenario.getName(), e.getMessage());
        }

        // Write the API capture while the browser is still reachable; teardown may quit it.
        try {
            stopApiCapture();
        } catch (Exception e) {
            logger.warn("API capture stop failed for scenario {}: {}", scenario.getName(), e.getMessage());
        }

        try {
            getDriverManager().teardownDriver(null, !scenario.isFailed());
        } catch (Exception e) {
            logger.error("Driver teardown error for scenario {}: {}",
                    scenario.getName(), e.getMessage());
        }
    }

    // =========================================================================
    // DRIVER ACCESS
    // =========================================================================

    /**
     * Returns the {@link org.openqa.selenium.remote.RemoteWebDriver} bound to
     * the current thread. Safe to call from any step method.
     */
    protected org.openqa.selenium.remote.RemoteWebDriver getDriver() {
        return getDriverManager().getDriver();
    }

    /**
     * Resolves the {@link DriverPoolManager} bound to the calling thread's
     * context. Deliberately not cached on an instance field — see
     * {@code SeleniumBase.getDriverManager()}'s Javadoc for why (framework-3.1
     * architecture review, finding F5).
     */
    @Override
    protected DriverPoolManager getDriverManager() {
        return DriverPoolManager.getInstance();
    }

    /**
     * Name of a feature file without its extension, taken from the scenario's URI:
     * {@code file:///C:/proj/features/login.feature} and {@code classpath:features/login.feature}
     * both give {@code login}. Used as the capture folder name; {@code UnnamedFeature} if absent.
     */
    static String featureNameOf(URI uri) {
        if (uri == null) {
            return "UnnamedFeature";
        }
        String text = uri.toString();
        int query = text.indexOf('?');
        if (query >= 0) {
            text = text.substring(0, query);
        }
        String name = text.substring(Math.max(text.lastIndexOf('/'), text.lastIndexOf(':')) + 1);
        int extension = name.lastIndexOf(".feature");
        if (extension > 0) {
            name = name.substring(0, extension);
        }
        return name.isBlank() ? "UnnamedFeature" : name;
    }
}
