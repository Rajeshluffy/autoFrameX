# Element Location Pattern Guide

## Overview

The autoFrameX framework uses an **exception-based pattern** for element location instead of returning `null`. This ensures that test failures are explicit and cannot be silently swallowed by null pointer exceptions.

## The Problem with Returning `null`

**Old Anti-Pattern:**
```java
WebElement button = page.locateElement(Locators.XPATH, "//button[@id='submit']");
button.click();  // ❌ NullPointerException if element not found
```

**Issues:**
- The error is implicit — you only discover it at the point of use, not at location
- Stack traces point to `.click()`, hiding the root cause (element not found)
- Subtle bugs: returning `null` can be missed in conditional logic
- No opportunity to log/report the failure at the right place

## The Solution: Exception-Based Location

**New Pattern:**
```java
try {
    WebElement button = page.locateElement(Locators.XPATH, "//button[@id='submit']");
    button.click();
} catch (ElementNotFoundException e) {
    logger.error("Submit button not found: " + e.getMessage());
    fail("Test failed: expected element was missing from the page");
}
```

**Benefits:**
- Error is **explicit** — you can see it in the method signature
- Stack trace points to the actual failure (element not found), not a downstream NullPointerException
- Callers must explicitly handle or acknowledge the failure
- Consistent with Selenium WebDriver's own `NoSuchElementException` pattern

## Exception Class

The `ElementNotFoundException` is a checked-like exception (though unchecked for convenience) defined in:

```
src/main/java/com/framework/selenium/exception/ElementNotFoundException.java
```

### Constructors

```java
// With just a message
throw new ElementNotFoundException("Element not found - XPATH: //button[@id='submit']");

// With root cause (useful for debugging)
throw new ElementNotFoundException("Element not found", originalException);
```

## Usage Patterns

### Pattern 1: Basic Retry

```java
WebElement element = null;
for (int attempt = 0; attempt < 3; attempt++) {
    try {
        element = page.locateElement(Locators.CSS, ".submit-btn");
        break;  // Success
    } catch (ElementNotFoundException e) {
        if (attempt < 2) {
            page.pause(500);  // Wait before retry
        } else {
            throw e;  // Give up after 3 attempts
        }
    }
}
```

### Pattern 2: Optional Element Check

```java
WebElement optionalElement = null;
try {
    optionalElement = page.locateElement(Locators.XPATH, "//optional/element");
} catch (ElementNotFoundException e) {
    logger.info("Optional element not found (this is OK): " + e.getMessage());
    // Continue without it
}

if (optionalElement != null) {
    optionalElement.click();
}
```

### Pattern 3: Fallback Locators

Use the three-argument overload to try multiple locators:

```java
// Try CLASS_NAME first, then fall back to CSS
WebElement element = page.locateElement(
    Locators.CLASS_NAME, "primary-button",
    Locators.CSS, "button.fallback"
);
// Throws ElementNotFoundException only if both fail
```

**Note:** This is preferred over manual try-catch when you have a clear primary + fallback.

### Pattern 4: Direct Use in Fluent API

```java
// If you know the element exists, use it directly
page.locateElement(Locators.ID, "username")
    .sendKeys("myuser");

// The framework will throw if it doesn't exist
```

## Best Practices

### ✅ DO

1. **Let exceptions propagate in test failures:**
   ```java
   WebElement submit = page.locateElement(Locators.ID, "submit");
   submit.click();  // Let ElementNotFoundException fail the test
   ```

2. **Catch only when recovery is possible:**
   ```java
   try {
       element = page.locateElement(Locators.XPATH, path);
   } catch (ElementNotFoundException e) {
       logger.info("Element not found, using backup strategy");
       // Do something else
   }
   ```

3. **Include context in catch blocks:**
   ```java
   } catch (ElementNotFoundException e) {
       logger.error("Could not find delete button on item #" + itemId + ": " + e);
       fail("Delete button missing");
   }
   ```

### ❌ DON'T

1. **Silently ignore ElementNotFoundException:**
   ```java
   try {
       page.locateElement(Locators.XPATH, "//button");
   } catch (ElementNotFoundException e) {
       // ❌ Silent failure — test will pass even though element is missing
   }
   ```

2. **Use null checks after locateElement:**
   ```java
   WebElement elem = page.locateElement(locator, value);
   if (elem != null) {  // ❌ locateElement never returns null
       elem.click();
   }
   ```

3. **Wrap in generic RuntimeException:**
   ```java
   try {
       element = page.locateElement(locator, value);
   } catch (Exception e) {
       throw new RuntimeException(e);  // ❌ Lost context
   }
   ```

## Page Object Example

Here's a complete page object using the new pattern:

```java
public class LoginPage extends BasePage {

    public void login(String username, String password) throws ElementNotFoundException {
        // These throw if elements not found — we let the exception propagate
        WebElement usernameField = locateElement(Locators.ID, "username");
        WebElement passwordField = locateElement(Locators.ID, "password");
        WebElement submitButton = locateElement(Locators.ID, "submit");

        clearAndType(usernameField, username);
        clearAndType(passwordField, password);
        click(submitButton);
    }

    public void loginWithRetry(String username, String password) {
        // Retry pattern for flaky elements
        try {
            login(username, password);
        } catch (ElementNotFoundException e) {
            reportStep("Login failed on first attempt, retrying: " + e.getMessage(), "warning", false);
            pause(500);
            try {
                login(username, password);
            } catch (ElementNotFoundException e2) {
                fail("Login form elements not found after retry: " + e2.getMessage());
            }
        }
    }

    @Override
    public boolean isLoaded() {
        try {
            locateElement(Locators.ID, "username").isDisplayed();
            return true;
        } catch (ElementNotFoundException e) {
            return false;  // Quietly check if page loaded
        }
    }
}
```

## Migration from Null Returns

If you have existing code that checks for null:

**Before:**
```java
WebElement elem = page.locateElement(locator, value);
if (elem != null) {
    elem.click();
} else {
    logger.warn("Element not found");
}
```

**After:**
```java
try {
    page.locateElement(locator, value).click();
} catch (ElementNotFoundException e) {
    logger.warn("Element not found: " + e.getMessage());
}
```

## Summary Table

| Scenario | Pattern | Code |
|----------|---------|------|
| Element must exist | Let exception propagate | `page.locateElement(loc, val).click();` |
| Recovery possible | Try-catch with recovery | `try { ... } catch (ElementNotFoundException e) { ... }` |
| Optional element | Catch and continue | `try { elem = ...; } catch (ElementNotFoundException e) { ... }` |
| Multiple locators | Use 3-arg overload | `page.locateElement(loc1, val1, loc2, val2)` |
| Check if loaded | Catch in isLoaded() | `try { locate(...); return true; } catch (...) { return false; }` |

## Links

- **Exception class:** `com.framework.selenium.exception.ElementNotFoundException`
- **Implementation:** `com.framework.selenium.api.base.SeleniumBase#locateElement()`
- **Interface contract:** `com.framework.selenium.api.design.Browser`
- **Base page class:** `com.framework.selenium.api.base.BasePage`
