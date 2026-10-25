package com.framework.cucumber.api.base;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.config.data.ConfigManager;
import com.framework.config.data.ProjectConfig;

import design.patterns.object.pool.DriverPoolManager;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/**
 * The browser lifecycle of a Cucumber scenario: acquire a driver from the pool and open the app before it, and
 * screenshot, API capture and returning the driver after it.
 *
 * <p>This is a concrete class on purpose. Cucumber-JVM will not load a step class that extends a class defining
 * hooks, so the hooks cannot sit on {@link CucumberProjectBase} (which step classes extend). Add this package to
 * the runner's glue and Cucumber instantiates this class itself, once per scenario:
 *
 * <pre>
 * {@literal @}CucumberOptions(glue = {"my.step.defs", "com.framework.cucumber.api.base"})
 * </pre>
 *
 * <p>It extends {@link CucumberProjectBase} only to reach the API-capture methods; the capture state is held per
 * thread, so the step classes' own {@code captureApis(...)} calls and these hooks see the same capture.
 */
public class CucumberHooks extends CucumberProjectBase {

    private static final Logger logger = LoggerFactory.getLogger(CucumberHooks.class);

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

        // Under TestNG, ProjectSpecificMethods initializes the pool in @BeforeTest. A Cucumber run has no such
        // lifecycle, and setupDriver would fail with "Pool not initialized". The pool's shutdown hook closes it
        // when the JVM exits.
        DriverPoolManager pool = DriverPoolManager.getInstance();
        if (!pool.isPoolInitialized()) {
            pool.initializePool(null);
        }

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
}
