package com.framework.selenium.api.base;

import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.PageFactory;

import design.patterns.object.pool.DriverPoolManager;

/**
 * Abstract base class for all Page Objects in the framework.
 *
 * Hierarchy: Reporter → SeleniumBase → BasePage → ConcretePageClass
 *
 * Usage:
 * <pre>
 * public class LoginPage extends BasePage {
 *
 *     {@literal @}FindBy(id = "username")
 *     private WebElement usernameField;
 *
 *     public LoginPage() { super(); }
 *
 *     {@literal @}Override
 *     public boolean isLoaded() { return usernameField.isDisplayed(); }
 *
 *     public void login(String user, String pass) {
 *         clearAndType(usernameField, user);
 *         clearAndType(passwordField, pass);
 *         click(submitButton);
 *     }
 * }
 * </pre>
 */
public abstract class BasePage extends SeleniumBase {

    // Initialises @FindBy / @FindBys fields via PageFactory.
    // Must be called from every concrete page-object constructor via super().
    protected BasePage() {
        PageFactory.initElements(DriverPoolManager.getInstance().getDriver(), this);
    }

    // =========================================================================
    // Core page contract
    // =========================================================================

    // Returns true when the page has fully loaded and its key element is visible.
    // Implement in every page class to act as a readiness guard before interaction.
    public abstract boolean isLoaded();

    // =========================================================================
    // Driver access
    // =========================================================================

    // Returns the WebDriver for the current thread.
    // Prefer inherited action methods (click, clearAndType, etc.) over raw driver access.
    protected RemoteWebDriver driver() {
        return DriverPoolManager.getInstance().getDriver();
    }

    // =========================================================================
    // Navigation helpers
    // =========================================================================

    protected void navigate(String url) {
        driver().get(url);
    }

    protected String currentUrl() {
        return driver().getCurrentUrl();
    }

    protected String pageTitle() {
        return driver().getTitle();
    }
}
