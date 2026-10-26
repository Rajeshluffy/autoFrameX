package com.framework.cucumber.api.base;

import java.net.URI;

import org.openqa.selenium.remote.RemoteWebDriver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.selenium.api.base.SeleniumBase;

import design.patterns.object.pool.DriverPoolManager;

/**
 * Base for Cucumber step-definition classes that drive the browser: it gives them the Selenium actions of
 * {@link SeleniumBase} ({@code click}, {@code type}, {@code captureApis}, ...) and {@link #getDriver()}.
 *
 * <h3>Usage</h3>
 * <pre>
 * public class LoginSteps extends CucumberProjectBase {
 *
 *     {@literal @}When("I log in as {string}")
 *     public void login(String user) {
 *         new LoginPage().enterUsername(user).clickLogin();
 *     }
 * }
 * </pre>
 *
 * <h3>Lifecycle</h3>
 * <p>This class defines <b>no hooks</b>, on purpose: Cucumber-JVM refuses to load a step class that extends a class
 * with hooks ("You're not allowed to extend classes that define Step Definitions or hooks"). The lifecycle lives
 * in {@link CucumberHooks}, which Cucumber finds when this package is added to the runner's glue:
 * <pre>
 * {@literal @}CucumberOptions(glue = {"my.step.defs", "com.framework.cucumber.api.base"})
 * </pre>
 * <pre>
 *   {@literal @}Before CucumberHooks.setUp()    — acquires the driver from the pool, opens the app URL
 *   Scenario steps run
 *   {@literal @}After  CucumberHooks.tearDown() — screenshot on failure, API capture, returns the driver
 * </pre>
 * Without that glue entry no browser is acquired.
 *
 * <h3>API-only scenarios</h3>
 * <p>Step classes that only call REST APIs and never touch the browser do not need to extend this class. Use
 * {@link CucumberScenarioContext} and let PicoContainer inject it, and tag the scenario {@code @api}: the hooks
 * acquire a browser for every scenario that is not tagged {@code @api}.
 *
 * @see CucumberHooks
 * @see CucumberScenarioContext
 */
public abstract class CucumberProjectBase extends SeleniumBase {

    private static final Logger logger = LoggerFactory.getLogger(CucumberProjectBase.class);

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

    // =========================================================================
    // DRIVER ACCESS
    // =========================================================================

    /**
     * Returns the {@link org.openqa.selenium.remote.RemoteWebDriver} bound to
     * the current thread. Safe to call from any step method.
     */
    protected RemoteWebDriver getDriver() {
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
