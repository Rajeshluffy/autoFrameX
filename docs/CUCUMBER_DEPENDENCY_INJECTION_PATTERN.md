# Cucumber Dependency Injection Pattern Guide

## Overview

The autoFrameX framework uses **Cucumber's PicoContainer** to implement dependency injection in step definitions. This pattern eliminates static fields and ensures clean state isolation between parallel scenario executions.

## The Problem with Static Fields

**Anti-Pattern (❌ Do Not Use):**
```java
public class OrderSteps {
    
    // ❌ PROBLEM: Static field shared across all scenarios
    private static OrderService orderService = new OrderService();
    private static String orderId;  // Shared state!
    
    @When("I place an order")
    public void placeOrder() {
        orderId = orderService.createOrder();  // Overwrites other scenario's orderId
    }
}
```

**Issues:**
- State leaks between parallel scenarios (test isolation failure)
- Hidden coupling between step classes
- Cleanup is fragile (who's responsible for resetting the orderId?)
- Debugging flaky tests becomes a nightmare under parallel execution

## The Solution: Scenario-Scoped Context

**Better Pattern (✅ Recommended):**

### 1. Create a Scenario Context Class

```java
package step.defs;

import com.myapp.api.services.OrderService;

/**
 * Shared scenario context injected via Cucumber PicoContainer.
 *
 * PicoContainer creates exactly one instance of this class per scenario and
 * injects it into every step definition class whose constructor requests it.
 */
public class OrderScenarioContext {

    /**
     * Service instance — shared between all step classes in a scenario.
     * PicoContainer calls the no-arg constructor automatically.
     */
    public final OrderService orderService = new OrderService();

    /**
     * Order ID created during the current scenario.
     * Written by @When step, read by @Then step and @After cleanup.
     */
    public String orderId;
}
```

### 2. Inject into Step Classes

```java
public class OrderCreationSteps {

    private final OrderScenarioContext ctx;

    // PicoContainer calls this constructor and injects the shared context
    public OrderCreationSteps(OrderScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("I place an order for {string}")
    public void placeOrder(String product) {
        ctx.orderId = ctx.orderService.createOrder(product);
    }

    @Then("the order should have ID")
    public void verifyOrderCreated() {
        Assert.assertNotNull("Order ID should not be null", ctx.orderId);
    }
}
```

### 3. Inject into Other Step Classes

```java
public class OrderVerificationSteps {

    private final OrderScenarioContext ctx;

    public OrderVerificationSteps(OrderScenarioContext ctx) {
        this.ctx = ctx;  // Receives SAME instance as OrderCreationSteps
    }

    @Then("the order status should be {string}")
    public void verifyOrderStatus(String expectedStatus) {
        String actualStatus = ctx.orderService.getOrderStatus(ctx.orderId);
        Assert.assertEquals(expectedStatus, actualStatus);
    }
}
```

### 4. Add Cleanup

```java
public class OrderCleanupSteps {

    private final OrderScenarioContext ctx;

    public OrderCleanupSteps(OrderScenarioContext ctx) {
        this.ctx = ctx;
    }

    @After
    public void cleanUp() {
        if (ctx.orderId != null) {
            ctx.orderService.deleteOrder(ctx.orderId);
        }
    }
}
```

## How PicoContainer Works

```
Scenario 1 (Thread A):
├─ PicoContainer creates OrderScenarioContext#1
├─ OrderCreationSteps(context#1)
├─ OrderVerificationSteps(context#1)  ← SAME instance
└─ OrderCleanupSteps(context#1)       ← SAME instance

Scenario 2 (Thread B):  [PARALLEL]
├─ PicoContainer creates OrderScenarioContext#2  ← DIFFERENT instance
├─ OrderCreationSteps(context#2)
├─ OrderVerificationSteps(context#2)  ← SAME instance (but different from Scenario 1)
└─ OrderCleanupSteps(context#2)       ← SAME instance (but different from Scenario 1)
```

**Result:** No state collision, clean parallel execution.

## PicoContainer Configuration

### Maven Dependency (in pom.xml)

```xml
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-picocontainer</artifactId>
    <version>7.21.1</version>  <!-- Match your Cucumber version -->
    <scope>test</scope>
</dependency>
```

### Cucumber Runner Configuration

```java
@CucumberOptions(
    features = {"classpath:features"},
    glue = {"step.defs"},  // Package containing step classes
    dryRun = false,
    plugin = {...}
)
public class CucumberRunner extends AbstractTestNGCucumberTests {
}
```

**Key:** The `glue` path must point to the package containing your step definition classes. Use `classpath:features` (not a filesystem-relative path like `src/test/resources/features`) for `features` — in the TD-20 multi-module reactor, `CucumberRunner` (in `autoframex-cucumber`) can also be invoked from `autoframex-testkit`'s aggregate suite, whose working directory differs from `autoframex-cucumber`'s own basedir; `classpath:` resolves correctly regardless of which module invoked it.

## Real-World Example from autoFrameX

The ServiceNow project demonstrates this pattern:

### Context Class
```java
// src/test/java/step/defs/som/IncidentScenarioContext.java
public class IncidentScenarioContext {
    public final IncidentSerivce incidentService = new IncidentSerivce();
    public String createdSysId;
}
```

### Step Class
```java
// src/test/java/step/defs/som/IncidentServiceSteps.java
public class IncidentServiceSteps {
    
    private final IncidentScenarioContext ctx;
    
    public IncidentServiceSteps(IncidentScenarioContext ctx) {
        this.ctx = ctx;
    }
    
    @When("I create an incident with description {string}")
    public void createIncident(String description) {
        IncidentRequestPayload payload = new IncidentRequestPayload();
        payload.setDescription(description);
        ctx.incidentService.createIncidentRecord(payload);
    }
    
    @Then("the incident should be created successfully")
    public void incidentCreatedSuccessfully() {
        ctx.incidentService.validateCreationResponse();
        ctx.createdSysId = ctx.incidentService.getLastCreatedSysId();
    }
    
    @After
    public void cleanUp() {
        if (ctx.createdSysId != null) {
            ctx.incidentService.deleteIncidentRecord(ctx.createdSysId);
        }
    }
}
```

## Advantages

| Aspect | Static Fields | PicoContainer Context |
|--------|---------------|----------------------|
| **State isolation** | ❌ Leaked between scenarios | ✅ Fresh instance per scenario |
| **Parallel safety** | ❌ Data races likely | ✅ Thread-safe by design |
| **Coupling** | ❌ Step classes tightly bound | ✅ Loosely coupled via constructor |
| **Testing** | ❌ Hard to mock/inject | ✅ Easy to mock in unit tests |
| **Cleanup** | ❌ Manual, fragile | ✅ Automatic with @After |
| **Readability** | ❌ Hidden dependencies | ✅ Explicit in constructor |

## Best Practices

### ✅ DO

1. **Create one context per service or domain:**
   ```java
   // Good: Domain-focused context
   public class UserManagementScenarioContext { ... }
   public class OrderManagementScenarioContext { ... }
   ```

2. **Mark public fields for shared state:**
   ```java
   public class MyContext {
       public final MyService myService = new MyService();
       public String createdItemId;  // Shared state
   }
   ```

3. **Use @After in a step class to clean up:**
   ```java
   @After
   public void cleanUp() {
       if (ctx.createdItemId != null) {
           ctx.myService.deleteItem(ctx.createdItemId);
       }
   }
   ```

4. **Inject context into every step class:**
   ```java
   public class MySteps {
       private final MyContext ctx;
       public MySteps(MyContext ctx) { this.ctx = ctx; }
   }
   ```

### ❌ DON'T

1. **Use static fields alongside PicoContainer:**
   ```java
   public class MixedSteps {
       private static String BAD_STATIC = null;  // ❌ Defeats DI purpose
       private final Context ctx;
       ...
   }
   ```

2. **Create services in the context constructor:**
   ```java
   public class BadContext {
       // ❌ Complex initialization hides dependencies
       public BadContext() {
           this.service = buildServiceWithComplexLogic();
       }
   }
   ```

3. **Share context between feature files:**
   ```java
   // ❌ PicoContainer creates fresh context per scenario, not per feature file
   // (If you need feature-level sharing, you need a different approach)
   ```

4. **Forget to declare the constructor:**
   ```java
   public class ForgottenSteps {
       private Context ctx;  // ❌ Field injection doesn't work
       // PicoContainer needs a constructor parameter
   }
   ```

## Scaling to Multiple Contexts

For large projects with multiple services, use multiple contexts:

```java
// User management context
public class UserScenarioContext {
    public final UserService userService = new UserService();
    public String createdUserId;
}

// Order management context
public class OrderScenarioContext {
    public final OrderService orderService = new OrderService();
    public String createdOrderId;
}

// Step class using both
public class UserOrderSteps {
    private final UserScenarioContext userCtx;
    private final OrderScenarioContext orderCtx;
    
    public UserOrderSteps(UserScenarioContext userCtx, OrderScenarioContext orderCtx) {
        this.userCtx = userCtx;
        this.orderCtx = orderCtx;
    }
    
    @When("user {string} creates an order")
    public void userCreatesOrder(String userName) {
        String userId = userCtx.userService.getUserIdByName(userName);
        orderCtx.createdOrderId = orderCtx.orderService.createOrderForUser(userId);
    }
}
```

**PicoContainer will inject both contexts into the step class automatically.**

## Troubleshooting

### Problem: "PicoContainer could not instantiate context class"

**Cause:** The context class doesn't have a no-arg constructor.

**Solution:**
```java
public class MyContext {
    // ✅ PicoContainer requires a no-arg constructor
    public MyContext() {
        this.service = new MyService();
    }
}
```

### Problem: "Glue path not found" error

**Cause:** The glue path in @CucumberOptions doesn't match your step class package.

**Solution:**
```java
@CucumberOptions(
    glue = {"step.defs.som", "step.defs.other"}  // Include all step packages
)
```

### Problem: Context state not shared between step classes

**Cause:** Step classes have different constructors accepting different context types.

**Solution:** Ensure all step classes request the SAME context:
```java
public class StepsA {
    public StepsA(SharedContext ctx) { this.ctx = ctx; }  // Correct
}

public class StepsB {
    public StepsB(SharedContext ctx) { this.ctx = ctx; }  // Correct
}
```

## Comparison: Before and After

### Before (Static Fields)
```java
public class LoginSteps {
    private static AuthService authService = new AuthService();  // ❌ Shared
    private static String sessionToken;  // ❌ Shared
    
    @When("I login")
    public void login() {
        sessionToken = authService.login("user", "pass");
    }
}

public class DashboardSteps {
    private static AuthService authService = LoginSteps.authService;  // ❌ Tightly coupled
    
    @Then("I should see dashboard")
    public void verifyDashboard() {
        // Uses sessionToken from LoginSteps — state leak!
    }
}
```

### After (PicoContainer)
```java
public class AuthScenarioContext {
    public final AuthService authService = new AuthService();  // ✅ Fresh per scenario
    public String sessionToken;
}

public class LoginSteps {
    private final AuthScenarioContext ctx;
    
    public LoginSteps(AuthScenarioContext ctx) {  // ✅ Constructor injection
        this.ctx = ctx;
    }
    
    @When("I login")
    public void login() {
        ctx.sessionToken = ctx.authService.login("user", "pass");
    }
}

public class DashboardSteps {
    private final AuthScenarioContext ctx;  // ✅ Loose coupling
    
    public DashboardSteps(AuthScenarioContext ctx) {
        this.ctx = ctx;
    }
    
    @Then("I should see dashboard")
    public void verifyDashboard() {
        // Uses ctx.sessionToken — clean state, no leaks!
    }
}
```

## Links

- **PicoContainer docs:** https://cucumber.io/docs/cucumber/cucumber-jvm/
- **Real example:** `D:\E Drive\Engineering\testleaf\workspace\serivcenow\src\test\java\step\defs\som\IncidentScenarioContext.java`
- **ServiceNow project:** Uses this pattern in all step classes
- **Framework:** autoFrameX (this project)
