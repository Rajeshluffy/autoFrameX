# Page Object Pattern Guide

## Overview

The autoFrameX framework provides a **`BasePage` abstract class** that implements the Page Object Pattern, enabling maintainable, reusable, and DRY (Don't Repeat Yourself) test code.

## What is the Page Object Pattern?

The Page Object Pattern is a test automation best practice that:

1. **Encapsulates page elements** into a separate class
2. **Hides locator strategies** from test code
3. **Provides action methods** (e.g., `login()`, `submitForm()`)
4. **Reduces maintenance** by centralizing locator changes

### Before Page Objects (❌ Do Not Use)

```java
@Test
public void testLogin() {
    driver.findElement(By.id("username")).sendKeys("john");
    driver.findElement(By.id("password")).sendKeys("pass123");
    driver.findElement(By.xpath("//button[text()='Login']")).click();
    Assert.assertEquals("Dashboard", driver.getTitle());
}

@Test
public void testForgotPassword() {
    driver.findElement(By.xpath("//a[text()='Forgot Password']")).click();  // ❌ Same locator scattered
    // ...
}
```

**Problems:**
- Locators hardcoded in test methods
- If "Forgot Password" link changes, you must update ALL tests
- No reusability between tests
- Tests become bloated and hard to read

### After Page Objects (✅ Recommended)

```java
public class LoginPage extends BasePage {
    
    public void login(String username, String password) {
        locateElement(Locators.ID, "username").sendKeys(username);
        locateElement(Locators.ID, "password").sendKeys(password);
        locateElement(Locators.XPATH, "//button[text()='Login']").click();
    }
    
    public void clickForgotPassword() {
        locateElement(Locators.XPATH, "//a[text()='Forgot Password']").click();
    }
}

@Test
public void testLogin() {
    LoginPage page = new LoginPage();
    page.login("john", "pass123");
    page.verifyTitle("Dashboard");
}

@Test
public void testForgotPassword() {
    LoginPage page = new LoginPage();
    page.clickForgotPassword();  // ✅ Locator change happens in one place
}
```

**Benefits:**
- Locators centralized in page classes
- Test code is cleaner and more readable
- Reusable across multiple tests
- Easy to maintain when UI changes

## BasePage Architecture

### Class Hierarchy

```
Reporter (Abstract)
    ↓
SeleniumBase (Implements Browser & Element interfaces)
    ↓
BasePage (Abstract, thread-safe driver access)
    ↓
LoginPage, DashboardPage, etc. (Concrete implementations)
```

### The BasePage Class

Located in: `src/main/java/com/framework/selenium/api/base/BasePage.java`

```java
public abstract class BasePage extends SeleniumBase {

    /**
     * Initializes @FindBy / @FindBys fields via PageFactory.
     * Must be called from every concrete page-object constructor.
     */
    protected BasePage() {
        PageFactory.initElements(DriverPoolManager.getInstance().getDriver(), this);
    }

    /**
     * Returns true when the page has fully loaded.
     * Implement this in every page class.
     */
    public abstract boolean isLoaded();

    /**
     * Returns the WebDriver instance for the current thread.
     */
    protected RemoteWebDriver driver() {
        return DriverPoolManager.getInstance().getDriver();
    }

    /**
     * Navigation helpers
     */
    protected void navigate(String url) { ... }
    protected String currentUrl() { ... }
    protected String pageTitle() { ... }
}
```

## Creating a Page Object

### Step 1: Extend BasePage

```java
package com.myapp.pages;

import com.framework.selenium.api.base.BasePage;
import com.framework.selenium.api.design.Locators;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class LoginPage extends BasePage {

    // Define locators using Selenium's @FindBy annotation
    @FindBy(id = "username")
    private WebElement usernameField;

    @FindBy(id = "password")
    private WebElement passwordField;

    @FindBy(xpath = "//button[text()='Login']")
    private WebElement loginButton;

    @FindBy(xpath = "//a[text()='Forgot Password']")
    private WebElement forgotPasswordLink;

    // PageFactory initialization
    public LoginPage() {
        super();  // Calls PageFactory.initElements()
    }

    @Override
    public boolean isLoaded() {
        // Return true when the login form is visible
        return usernameField.isDisplayed() && 
               passwordField.isDisplayed() && 
               loginButton.isDisplayed();
    }

    // Action methods
    public void login(String username, String password) {
        clearAndType(usernameField, username);
        clearAndType(passwordField, password);
        click(loginButton);
    }

    public void clickForgotPassword() {
        click(forgotPasswordLink);
    }

    public String getErrorMessage() {
        return locateElement(Locators.XPATH, "//div[@class='error']").getText();
    }
}
```

### Step 2: Use in Tests

```java
public class LoginTest extends ProjectSpecificMethods {

    @Test
    public void testSuccessfulLogin() {
        LoginPage page = new LoginPage();
        
        // Verify page loaded
        Assert.assertTrue("Login page should be loaded", page.isLoaded());
        
        // Perform action
        page.login("john@example.com", "password123");
        
        // Verify result
        Assert.assertEquals("Dashboard", driver.getTitle());
    }

    @Test
    public void testInvalidLogin() {
        LoginPage page = new LoginPage();
        page.login("invalid@example.com", "wrong");
        
        // Verify error shown
        Assert.assertTrue("Error should be displayed", 
            page.getErrorMessage().contains("Invalid credentials"));
    }

    @Test
    public void testForgotPasswordFlow() {
        LoginPage loginPage = new LoginPage();
        loginPage.clickForgotPassword();
        
        ForgotPasswordPage forgotPage = new ForgotPasswordPage();
        Assert.assertTrue("Should navigate to forgot password", forgotPage.isLoaded());
    }
}
```

## Advanced Patterns

### Pattern 1: PageFactory with @FindBy vs locateElement()

**Using @FindBy (Lazy initialization):**
```java
public class ProductPage extends BasePage {
    
    @FindBy(id = "add-to-cart")
    private WebElement addToCartButton;
    
    public void addToCart() {
        click(addToCartButton);  // Element located on first use
    }
}
```

**Using locateElement() (Dynamic lookup):**
```java
public class ProductPage extends BasePage {
    
    public void addProductToCart(String productId) {
        // Dynamic XPath — different products have different locators
        WebElement addBtn = locateElement(
            Locators.XPATH, 
            "//product[@id='" + productId + "']//button[@class='add-to-cart']"
        );
        click(addBtn);
    }
}
```

**When to use each:**
- **@FindBy:** Static elements (username field, login button, header)
- **locateElement():** Dynamic elements (product list items, table rows)

### Pattern 2: Page Navigation

```java
public class DashboardPage extends BasePage {

    @FindBy(xpath = "//nav//a[text()='Orders']")
    private WebElement ordersLink;

    @Override
    public boolean isLoaded() {
        return verifyTitle("Dashboard");
    }

    // Return the next page in the flow
    public OrdersPage goToOrders() {
        click(ordersLink);
        OrdersPage page = new OrdersPage();
        Assert.assertTrue("Orders page should load", page.isLoaded());
        return page;
    }
}
```

**Usage:**
```java
@Test
public void testOrderFlow() {
    LoginPage login = new LoginPage();
    login.login("user", "pass");
    
    DashboardPage dashboard = new DashboardPage();
    Assert.assertTrue(dashboard.isLoaded());
    
    OrdersPage orders = dashboard.goToOrders();  // Page-to-page navigation
    Assert.assertTrue(orders.isLoaded());
}
```

### Pattern 3: Handling Dynamic Elements

```java
public class ShoppingCartPage extends BasePage {

    private static final String REMOVE_ITEM_XPATH = 
        "//div[@class='cart-item' and contains(., '%s')]//button[@class='remove']";

    @Override
    public boolean isLoaded() {
        return locateElement(Locators.CLASS_NAME, "cart-summary").isDisplayed();
    }

    public void removeItem(String itemName) throws ElementNotFoundException {
        String xpath = String.format(REMOVE_ITEM_XPATH, itemName);
        WebElement removeBtn = locateElement(Locators.XPATH, xpath);
        click(removeBtn);
    }

    public void removeItemWithFallback(String itemName) {
        try {
            removeItem(itemName);
        } catch (ElementNotFoundException e) {
            // Try alternative selector
            WebElement fallbackBtn = locateElement(
                Locators.XPATH, "//button[text()='Remove']",
                Locators.CLASS_NAME, "delete-btn"  // Fallback
            );
            click(fallbackBtn);
        }
    }
}
```

### Pattern 4: Waiting for Dynamic Content

```java
public class AjaxDataPage extends BasePage {

    @Override
    public boolean isLoaded() {
        // Wait for data to load via AJAX
        WebElement data = waitForVisibility(
            locateElement(Locators.CLASS_NAME, "data-loaded")
        );
        return data != null && data.isDisplayed();
    }

    public String getLoadedData() {
        WebElement dataElement = locateElement(Locators.CLASS_NAME, "dynamic-data");
        return waitForVisibility(dataElement).getText();
    }

    public void waitForDataRefresh() {
        waitForDisapperance(locateElement(Locators.CLASS_NAME, "loading-spinner"));
        Assert.assertTrue("Data should load", isLoaded());
    }
}
```

### Pattern 5: Inherited Page Objects

```java
// Base page with common elements
public class ApplicationPage extends BasePage {

    @FindBy(xpath = "//header//span[@class='user-name']")
    protected WebElement userNameDisplay;

    @FindBy(xpath = "//nav//button[@class='logout']")
    protected WebElement logoutButton;

    public String getLoggedInUser() {
        return getElementText(userNameDisplay);
    }

    public void logout() {
        click(logoutButton);
    }
}

// Specific page inheriting common functionality
public class DashboardPage extends ApplicationPage {

    @FindBy(id = "dashboard-heading")
    private WebElement dashboardHeading;

    @Override
    public boolean isLoaded() {
        return dashboardHeading.isDisplayed();
    }

    public void viewProfile() {
        // Inherited method
        String username = getLoggedInUser();
        
        // Page-specific action
        click(locateElement(Locators.XPATH, "//a[contains(@href, '" + username + "')]"));
    }
}
```

## Best Practices

### ✅ DO

1. **One page class per UI page/view:**
   ```java
   LoginPage, DashboardPage, OrdersPage, ProductDetailPage
   ```

2. **Use @FindBy for static elements:**
   ```java
   @FindBy(id = "submit-button")
   private WebElement submitButton;
   ```

3. **Return the next page in a flow:**
   ```java
   public DashboardPage login(String user, String pass) {
       // ... perform login ...
       return new DashboardPage();
   }
   ```

4. **Implement isLoaded() to verify page readiness:**
   ```java
   @Override
   public boolean isLoaded() {
       return headerElement.isDisplayed() && 
              mainContent.isDisplayed();
   }
   ```

5. **Use action methods with clear names:**
   ```java
   public void fillAndSubmitForm(String data) { }  // ✅ Clear
   public void click() { }  // ❌ Too generic
   ```

### ❌ DON'T

1. **Create generic page classes:**
   ```java
   public class GenericPage extends BasePage { }  // ❌ Won't work
   ```

2. **Use test logic in page objects:**
   ```java
   public void login() {
       // ❌ Don't put test assertions here
       Assert.assertEquals("Welcome", driver.getTitle());
   }
   ```

3. **Return WebElement from action methods:**
   ```java
   public WebElement clickButton() {  // ❌ Exposes implementation
       return locateElement(Locators.ID, "btn");
   }
   ```

4. **Put conditional logic in page objects:**
   ```java
   public void fillForm(String scenario) {
       if (scenario.equals("admin")) {  // ❌ Test logic, not page logic
           // Fill admin form
       }
   }
   ```

5. **Use hardcoded wait times:**
   ```java
   Thread.sleep(5000);  // ❌ Use WaitUtils instead
   ```

## Complete Example: Multi-Page Flow

```java
// Feature: User Registration Flow
public class RegistrationFlow {

    @Test
    public void testCompleteRegistration() {
        // Step 1: Navigate and verify
        HomePage homePage = new HomePage();
        Assert.assertTrue("Home page should load", homePage.isLoaded());

        // Step 2: Start registration
        RegistrationStep1Page step1 = homePage.clickRegisterButton();
        Assert.assertTrue("Step 1 should load", step1.isLoaded());

        // Step 3: Fill personal info
        RegistrationStep2Page step2 = step1.fillPersonalInfo(
            "John", "Doe", "john@example.com"
        );
        Assert.assertTrue("Step 2 should load", step2.isLoaded());

        // Step 4: Fill address
        RegistrationStep3Page step3 = step2.fillAddressInfo(
            "123 Main St", "Springfield", "IL", "62701"
        );
        Assert.assertTrue("Step 3 should load", step3.isLoaded());

        // Step 5: Confirm and submit
        ConfirmationPage confirmation = step3.submitRegistration();
        Assert.assertTrue("Confirmation should show", confirmation.isLoaded());
        Assert.assertTrue("Success message should display", 
            confirmation.getSuccessMessage().contains("Welcome, John"));
    }
}
```

## Troubleshooting

### Problem: "NullPointerException in page object"

**Cause:** @FindBy element is null (element doesn't exist).

**Solution:** Use `locateElement()` and handle exception:
```java
try {
    WebElement elem = locateElement(Locators.ID, "optional-element");
} catch (ElementNotFoundException e) {
    // Handle missing element
}
```

### Problem: "StaleElementReferenceException"

**Cause:** Page reloaded, @FindBy element references old DOM.

**Solution:** Use `locateElement()` instead of cached @FindBy:
```java
// ❌ Cached, can become stale
@FindBy(id = "dynamic-element")
private WebElement element;

// ✅ Fresh lookup each time
public void interact() {
    WebElement element = locateElement(Locators.ID, "dynamic-element");
    click(element);
}
```

### Problem: "Page not loading within timeout"

**Cause:** `isLoaded()` returns false before page is ready.

**Solution:** Add waits in `isLoaded()`:
```java
@Override
public boolean isLoaded() {
    try {
        WebElement mainContent = waitForVisibility(
            locateElement(Locators.ID, "main-content")
        );
        return mainContent != null;
    } catch (ElementNotFoundException e) {
        return false;
    }
}
```

## Links

- **BasePage class:** `com.framework.selenium.api.base.BasePage`
- **SeleniumBase class:** `com.framework.selenium.api.base.SeleniumBase`
- **Element location pattern:** See `ELEMENT_LOCATION_PATTERN.md`
- **Wait utilities:** See `WaitUtils` in framework
