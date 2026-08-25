package com.framework.cucumber.api.base;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.config.data.ConfigManager;
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

        getDriverManager().setupDriver(null, params);

        try {
            String url = ConfigManager.getInstance().getConfig().getAppUrl();
            if (url != null && !url.isBlank()) {
                getDriverManager().getDriver().get(url);
                logger.info("Navigated to: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Could not navigate to app URL after driver setup: {}", e.getMessage());
        }

        logger.info("Driver ready for scenario: {}", scenario.getName());
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
}
