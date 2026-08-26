# Browser Layer

The browser layer is the foundation of autoFrameX's WebDriver management. It spans three tiers: the `Browser` interface contract, the `SeleniumBase` implementation, and the factory + object pool that create and recycle driver instances.

---

## Architecture Overview

```
TestNG Test
    │
    ▼
SeleniumBase  (implements Browser + Element)
    │  calls getDriverManager().getDriver()
    ▼
WebDriverPoolFactory  (object pool — acquire / release)
    │  delegates creation to
    ▼
BrowserFactory  (Strategy pattern)
    │  dispatches via
    ▼
BrowserType  (Enum Factory)
    │  instantiates
    ▼
ChromeBrowser / FireFoxBrowser / EdgeBrowser / RemoteGridBrowser
```

---

## 1. Browser Interface (`com.framework.selenium.api.design.Browser`)

`Browser` is the contract every test interacts with. It groups capabilities into five areas:

| Area | Key Methods |
|---|---|
| Element Discovery | `locateElement(Locators, String)`, `locateElement(String)`, `locateElement(…, fallback…)`, `locateElements(…)` |
| Wait Mechanisms | `waitForClickable`, `waitForVisibility`, `waitForApperance`, `waitForDisapperance`, `setImplicitWait`, `resetImplicitWait` |
| JavaScript & Actions | `executeJs`, `clickWithJs` |
| Alert Handling | `switchToAlert`, `acceptAlert`, `dismissAlert`, `getAlertText`, `typeAlert` |
| Window & Frame | `switchToWindow(int)`, `switchToWindowByTitle`, `switchToWindowByUrl`, `switchToFrame(int/WebElement/String)`, `switchToFrameUsingXPath`, `defaultContent` |
| Verification & Utility | `verifyUrl`, `verifyPartialUrl`, `verifyTitle`, `takeSnap`, `close`, `quit` |

### Fallback Locator Pattern

```java
// Tries locatorType1/value1 first; falls back to locatorType2/value2 if not found
WebElement el = locateElement(Locators.ID, "submit-btn", Locators.XPATH, "//button[@type='submit']");
```

This is the dual-locator overload — useful when an element has inconsistent attributes across environments.

---

## 2. SeleniumBase (`com.framework.selenium.api.base.SeleniumBase`)

`SeleniumBase` is the concrete implementation. Tests extend it (via `ProjectSpecificMethods`) and never touch WebDriver directly.

```java
public class SeleniumBase extends Reporter implements Browser, Element { … }
```

Driver access is always through the pool:

```java
private RemoteWebDriver getDriver() {
    return getDriverManager().getDriver(); // thread-local, from WebDriverPoolFactory
}
```

Throwing `IllegalStateException` here (rather than returning null) ensures a missing `@BeforeMethod` setup surfaces immediately as a clear error rather than a NullPointerException deep in test code.

---

## 3. BrowserType — Enum Factory (`design.patterns.factory.browser.BrowserType`)

`BrowserType` is both the browser selector and the factory dispatcher. Each enum constant holds a `Browser` implementation and delegates `launchBrowser()` to it.

```java
public enum BrowserType {
    // Local
    CHROME(ChromeBrowser.getInstance()),
    FIREFOX(FireFoxBrowser.getInstance()),
    EDGE(EdgeBrowser.getInstance()),

    // Selenium Grid
    GRID_CHROME(new RemoteGridBrowser("chrome")),
    GRID_FIREFOX(new RemoteGridBrowser("firefox")),
    GRID_EDGE(new RemoteGridBrowser("edge"));
}
```

Switch between local and grid execution by changing the TestNG parameter or environment variable — no test code changes required:

```xml
<!-- testng.xml -->
<parameter name="browser" value="GRID_CHROME"/>
```

```bash
# or via environment variable
BROWSER=GRID_CHROME mvn test
```

`isRemote()` returns `true` for all `GRID_*` types, which the pool uses to skip certain local-only setup steps.

---

## 4. BrowserFactory (`design.patterns.factory.browser.BrowserFactory`)

`BrowserFactory` implements the Strategy pattern for driver creation. The pool calls it whenever a new driver is needed.

```java
public RemoteWebDriver createDriver(BrowserType browserType, PoolConfig config) {
    RemoteWebDriver driver = browserType.launchBrowser(); // delegates to Enum Factory
    configureTimeouts(driver);
    return driver;
}
```

Timeouts are read lazily (via instance methods, not static fields) to avoid NPE when `BrowserFactory` is referenced before `ConfigManager` is initialized during TestNG context startup.

| Timeout | Config Key | Applied via |
|---|---|---|
| Page load | `pageLoadTimeout` | `driver.manage().timeouts().pageLoadTimeout(…)` |
| Script | `scriptTimeout` | `driver.manage().timeouts().scriptTimeout(…)` |
| Implicit wait | `implicit` | `driver.manage().timeouts().implicitlyWait(…)` |

---

## 5. ChromeBrowser (`design.patterns.factory.browser.ChromeBrowser`)

Singleton. Builds `ChromeOptions` with a fixed set of arguments tuned for CI and local runs:

| Option | Purpose |
|---|---|
| `--disable-dev-shm-usage` | Prevents shared memory crashes in Docker/Linux |
| `--no-sandbox` | Required in containerized environments |
| `--remote-allow-origins=*` | Fixes `ConnectionFailedException` in Selenium 4+ |
| `--guest` | Prevents profile conflicts between parallel runs |
| `excludeSwitches: enable-automation` | Removes the "controlled by automation" banner |
| `credentials_enable_service: false` | Suppresses password save prompts |
| `--headless=new` | Activated when `-Dheadless=true` or `HEADLESS=true` |

Headless mode is resolved at driver-creation time from system property then environment variable:

```java
String headless = System.getProperty("headless", System.getenv("HEADLESS"));
return "true".equalsIgnoreCase(headless);
```

---

## 6. WebDriverPoolFactory (`design.patterns.object.pool.WebDriverPoolFactory`)

Production-grade, thread-safe object pool. Tests never call `new ChromeDriver()` — they borrow from the pool and return when done.

### Pool Lifecycle

```
acquire(browserType, url)
    ├─ poll IDLE queue (non-blocking)
    ├─ create new driver if below maxPoolSize
    └─ blocking poll with timeout if pool is full
         └─ throws DriverAcquisitionException after borrowTimeoutSeconds

release(driver, poisoned)
    ├─ poisoned=true  → mark POISONED, async quit(), never re-pooled
    └─ poisoned=false → dismiss alerts, close extra windows, return to IDLE queue
```

### Driver State Machine

```
IDLE ──acquire──► IN_USE ──release(ok)──► IDLE
                     │
                     └──release(fail)──► POISONED ──async quit──► gone
```

Double-borrow is detected immediately via `AtomicReference.compareAndSet` — an `IllegalStateException` is thrown rather than silently handing the same driver to two threads.

### Key Configuration (`PoolConfig`)

| Property | Default | Description |
|---|---|---|
| `minPoolSize` | 1 | Drivers pre-warmed at startup |
| `maxPoolSize` | 5 | Hard cap; excess requests block |
| `borrowTimeoutSeconds` | 30 | Max wait when pool is full |
| `maxReuseCount` | 75 | Driver retired after N uses (prevents memory/fd leaks) |
| `maxIdleMinutes` | — | Idle TTL; cleanup runs every 5 minutes |
| `stateResetEnabled` | true | Clears cookies + localStorage + sessionStorage on reuse |
| `healthCheckEnabled` | true | `getCurrentUrl()` called on borrow (~50 ms) |

### State Reset Order

When a driver is reused, state is cleared in this order to avoid cross-origin cookie issues:

1. `driver.manage().deleteAllCookies()`
2. `window.localStorage.clear()`
3. `window.sessionStorage.clear()`
4. Navigate to target URL (clean page load)

Cookies are cleared *after* navigating to the target domain so the deletion applies to the correct origin.

### Async Quit

`driver.quit()` (2–8 s) runs on a dedicated `DriverPool-Quit` daemon thread, not the test thread. This keeps reported test duration accurate and prevents teardown from blocking the next test from starting.

### Pre-warming

`minPoolSize` drivers are created eagerly at pool construction so the first test never pays the browser-launch cost.

---

## 7. Locators Enum (`com.framework.selenium.api.design.Locators`)

Used as the first argument to `locateElement` / `locateElements`:

```java
locateElement(Locators.XPATH, "//input[@name='email']");
locateElement(Locators.CSS,   "input.email-field");
locateElement(Locators.ID,    "email");
```

Supported strategies: `ID`, `NAME`, `CLASS`, `XPATH`, `CSS`, `LINK_TEXT`, `PARTIAL_LINK_TEXT`, `TAG`.

---

## Usage Example

```java
// In a Page Object (extends ProjectSpecificMethods → SeleniumBase)
public void login(String user, String pass) {
    WebElement username = locateElement(Locators.ID, "username");
    waitForVisibility(username);
    username.sendKeys(user);

    locateElement(Locators.ID, "password").sendKeys(pass);
    clickWithJs(locateElement(Locators.XPATH, "//button[@type='submit']"));
}
```

The test never references `WebDriver`, `ChromeDriver`, or the pool directly — all of that is handled by the framework.

---

## Running Headless

```bash
# System property
mvn test -Dheadless=true

# Environment variable
HEADLESS=true mvn test
```

## Running on Selenium Grid

```bash
mvn test -Dbrowser=GRID_CHROME -DgridUrl=http://selenium-hub:4444/wd/hub
```
