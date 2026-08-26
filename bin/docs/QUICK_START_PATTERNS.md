# autoFrameX Quick Start: Core Patterns

**For new developers:** Read this file first (5 minutes), then dive into the full pattern guides.

---

## The Three Core Patterns

### 1️⃣ Exception-Based Element Location

**The Rule:** If an element isn't found, it throws `ElementNotFoundException`. No null checks needed.

```java
// ✅ DO THIS
try {
    WebElement button = page.locateElement(Locators.XPATH, "//button[@id='submit']");
    click(button);
} catch (ElementNotFoundException e) {
    fail("Submit button missing from page");
}

// ❌ DON'T DO THIS
WebElement button = page.locateElement(Locators.XPATH, "//button[@id='submit']");
if (button != null) {  // ❌ locateElement never returns null
    click(button);
}
```

**Why:** Makes failures explicit. Stack trace points to root cause, not a downstream NPE.

**Full Guide:** `ELEMENT_LOCATION_PATTERN.md`

---

### 2️⃣ Scenario-Scoped Dependency Injection

**The Rule:** Use PicoContainer contexts to share state between step classes. One context per scenario.

```java
// Step 1: Create context class
public class UserScenarioContext {
    public final UserService userService = new UserService();
    public String userId;  // Shared state
}

// Step 2: Inject into step classes
public class UserCreationSteps {
    private final UserScenarioContext ctx;
    
    public UserCreationSteps(UserScenarioContext ctx) {
        this.ctx = ctx;
    }
    
    @When("I create a user")
    public void createUser() {
        ctx.userId = ctx.userService.createUser("john");
    }
}

public class UserVerificationSteps {
    private final UserScenarioContext ctx;
    
    public UserVerificationSteps(UserScenarioContext ctx) {
        this.ctx = ctx;  // ← SAME instance as UserCreationSteps
    }
    
    @Then("the user should exist")
    public void verifyUser() {
        UserDetails user = ctx.userService.getUser(ctx.userId);
        Assert.assertNotNull(user);
    }
}

// Step 3: Maven dependency
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-picocontainer</artifactId>
    <version>7.21.1</version>
</dependency>
```

**Why:** No static fields (thread-safe) + fresh context per scenario = clean parallel execution.

**Full Guide:** `CUCUMBER_DEPENDENCY_INJECTION_PATTERN.md`

---

### 3️⃣ Page Object Pattern with BasePage

**The Rule:** Create one page class per page, extend `BasePage`, implement `isLoaded()`.

```java
public class LoginPage extends BasePage {

    // Locators
    @FindBy(id = "username")
    private WebElement usernameField;
    
    @FindBy(id = "password")
    private WebElement passwordField;
    
    @FindBy(xpath = "//button[text()='Login']")
    private WebElement loginButton;

    // Constructor (required)
    public LoginPage() {
        super();  // Initializes @FindBy fields
    }

    // Page readiness check (required)
    @Override
    public boolean isLoaded() {
        return usernameField.isDisplayed() && 
               passwordField.isDisplayed();
    }

    // Action methods (your own)
    public void login(String user, String pass) {
        clearAndType(usernameField, user);
        clearAndType(passwordField, pass);
        click(loginButton);
    }
}

// Use in test
@Test
public void testLogin() {
    LoginPage page = new LoginPage();
    Assert.assertTrue("Should load", page.isLoaded());  // Verify readiness
    page.login("john", "pass123");  // Perform action
}
```

**Why:** Centralized locators (change once, updates everywhere) + readable action methods.

**Full Guide:** `PAGE_OBJECT_PATTERN.md`

---

## Quick Reference Table

| Task | Pattern | Example |
|------|---------|---------|
| Find element | Exception-based | `WebElement e = page.locateElement(loc, val);` |
| Element missing? | Catch exception | `catch (ElementNotFoundException e) { ... }` |
| Share state in Cucumber | Context class | `public class MyContext { ... }` |
| Inject context | Constructor | `public MySteps(MyContext ctx) { this.ctx = ctx; }` |
| Create page class | Extend BasePage | `public class MyPage extends BasePage { ... }` |
| Verify page loaded | Implement isLoaded() | `@Override public boolean isLoaded() { ... }` |
| Define locators | @FindBy annotation | `@FindBy(id = "submit")` |
| Dynamic elements | locateElement() | For runtime XPath building |
| Multiple locators | 3-arg overload | `locateElement(loc1, val1, loc2, val2)` |

---

## Common Patterns

### Pattern A: Login → Dashboard Flow

```java
@Test
public void userLoginFlow() {
    // Page 1: Login
    LoginPage login = new LoginPage();
    login.login("user", "pass");
    
    // Page 2: Dashboard (page navigation)
    DashboardPage dashboard = new DashboardPage();
    Assert.assertTrue("Dashboard should load", dashboard.isLoaded());
    Assert.assertEquals("John", dashboard.getLoggedInUser());
}
```

### Pattern B: Retry on Element Not Found

```java
public void clickWithRetry(String xpath) throws ElementNotFoundException {
    for (int i = 0; i < 3; i++) {
        try {
            WebElement elem = locateElement(Locators.XPATH, xpath);
            click(elem);
            return;
        } catch (ElementNotFoundException e) {
            if (i < 2) {
                pause(500);  // Wait and retry
            } else {
                throw e;  // Fail after 3 attempts
            }
        }
    }
}
```

### Pattern C: Optional Element Handling

```java
public void handleOptionalMessage() {
    try {
        WebElement message = locateElement(Locators.CLASS_NAME, "success-message");
        String text = message.getText();
        logger.info("Success message: " + text);
    } catch (ElementNotFoundException e) {
        logger.info("No success message (this is OK)");
    }
}
```

### Pattern D: Cucumber Context Usage

```java
// Scenario: User can create and delete items
public class ItemSteps {
    private final ItemContext ctx;
    
    public ItemSteps(ItemContext ctx) {
        this.ctx = ctx;
    }
    
    @When("I create an item named {string}")
    public void createItem(String name) {
        ctx.itemId = ctx.itemService.create(name);
    }
    
    @Then("the item should exist")
    public void verifyExists() {
        Item item = ctx.itemService.get(ctx.itemId);
        Assert.assertNotNull(item);
    }
    
    @After
    public void cleanup() {
        if (ctx.itemId != null) {
            ctx.itemService.delete(ctx.itemId);
        }
    }
}
```

---

## File Structure

Framework source is split across an 8-module Maven reactor (TD-20) — the
classes below all live in `autoframex-selenium`:

```
autoFrameX/
├── autoframex-selenium/src/main/java/
│   ├── com/framework/selenium/
│   │   ├── api/base/
│   │   │   ├── SeleniumBase.java       ← locateElement() methods
│   │   │   └── BasePage.java           ← Extend this for page objects
│   │   ├── api/design/
│   │   │   └── Browser.java            ← locateElement() contract
│   │   └── exception/
│   │       └── ElementNotFoundException.java  ← Thrown when element not found
│   └── design/patterns/object/pool/
│       └── WebDriverPoolFactory.java   ← Driver management
│
├── docs/
│   ├── ELEMENT_LOCATION_PATTERN.md     ← Full element location guide
│   ├── CUCUMBER_DEPENDENCY_INJECTION_PATTERN.md  ← Full context guide
│   ├── PAGE_OBJECT_PATTERN.md          ← Full page object guide
│   ├── FRAMEWORK_IMPROVEMENT_ROADMAP.md  ← Future improvements
│   └── QUICK_START_PATTERNS.md         ← THIS FILE
```

---

## 5-Minute Onboarding Checklist

- [ ] Read this file (QUICK_START_PATTERNS.md)
- [ ] Create first page object extending BasePage
- [ ] Understand the three exception-based locateElement() methods
- [ ] If using Cucumber: understand scenario context injection
- [ ] Run existing tests to see patterns in action

---

## When to Read Full Guides

| Situation | Read This Guide |
|-----------|-----------------|
| Creating a page object | `PAGE_OBJECT_PATTERN.md` |
| Handling missing elements | `ELEMENT_LOCATION_PATTERN.md` |
| Writing Cucumber steps | `CUCUMBER_DEPENDENCY_INJECTION_PATTERN.md` |
| Planning improvements | `FRAMEWORK_IMPROVEMENT_ROADMAP.md` |
| Stuck or confused | The full guide for your pattern |

---

## Common Questions

**Q: What if the element sometimes doesn't exist?**  
A: Use try-catch:
```java
try {
    element = page.locateElement(loc, val);
} catch (ElementNotFoundException e) {
    // Handle missing element
}
```

**Q: Can I use null checks?**  
A: No. `locateElement()` throws exceptions, never returns null.

**Q: Do I have to implement isLoaded()?**  
A: Yes. Every page must declare when it's fully loaded.

**Q: How do I share state between step classes?**  
A: Use scenario context class with PicoContainer injection (Cucumber only).

**Q: What if I'm not using Cucumber?**  
A: Skip the context pattern. Use page objects directly in your tests.

**Q: Can I use @FindBy and locateElement() together?**  
A: Yes. @FindBy for static elements, locateElement() for dynamic ones.

---

## Key Takeaways

1. **Element not found?** → Throws `ElementNotFoundException` (explicit failure)
2. **Sharing state in Cucumber?** → Use scenario context class (clean parallel execution)
3. **Creating page objects?** → Extend `BasePage` and implement `isLoaded()` (maintainable tests)

---

## Real-World Example

```java
// ServiceNow project (shows all patterns together)

// Pattern 1: Page Object
public class IncidentListPage extends BasePage {
    @FindBy(xpath = "//button[@class='new-incident']")
    private WebElement newIncidentBtn;
    
    @Override
    public boolean isLoaded() {
        return newIncidentBtn.isDisplayed();
    }
    
    public IncidentDetailPage createNewIncident() {
        click(newIncidentBtn);
        return new IncidentDetailPage();
    }
}

// Pattern 2: Scenario Context (Cucumber)
public class IncidentScenarioContext {
    public final IncidentService incidentService = new IncidentService();
    public String incidentId;
}

// Pattern 3: Exception Handling
public class IncidentSteps {
    private final IncidentScenarioContext ctx;
    
    public IncidentSteps(IncidentScenarioContext ctx) {
        this.ctx = ctx;
    }
    
    @When("I create an incident")
    public void createIncident() {
        try {
            ctx.incidentId = ctx.incidentService.create("Test Incident");
        } catch (Exception e) {
            fail("Could not create incident: " + e.getMessage());
        }
    }
    
    @Then("the incident should exist")
    public void verifyIncident() {
        try {
            Incident incident = ctx.incidentService.get(ctx.incidentId);
            Assert.assertNotNull("Incident not found", incident);
        } catch (IncidentNotFoundException e) {
            fail("Incident should exist: " + e.getMessage());
        }
    }
    
    @After
    public void cleanup() {
        if (ctx.incidentId != null) {
            ctx.incidentService.delete(ctx.incidentId);
        }
    }
}
```

---

## Next Steps

1. **If building UI tests:** Read `PAGE_OBJECT_PATTERN.md`
2. **If using Cucumber:** Read `CUCUMBER_DEPENDENCY_INJECTION_PATTERN.md`
3. **If struggling with elements:** Read `ELEMENT_LOCATION_PATTERN.md`
4. **If planning framework work:** Read `FRAMEWORK_IMPROVEMENT_ROADMAP.md`

**Happy automating! 🚀**
