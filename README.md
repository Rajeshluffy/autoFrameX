# autoFrameX

A shared, enterprise-grade test automation framework built on Selenium 4, TestNG, and REST Assured. Designed to be consumed as a base layer by multiple project teams — each project plugs in its own configuration class and extends the provided base classes without touching framework internals.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Running Tests](#running-tests)
- [Framework Modules](#framework-modules)
- [Automatic Performance Capture](#automatic-performance-capture)
- [Automatic API Capture](#automatic-api-capture)
- [CI/CD](#cicd)
- [Code Quality](#code-quality)

---

## Features

- **WebDriver Object Pool** — pre-warmed driver pool with configurable size, idle timeout, and per-driver reuse cap; eliminates cold-start latency in parallel runs
- **Thread-safe parallel execution** — `ThreadLocal` driver binding; safe for `parallel="methods"` and `parallel="classes"`
- **Auto-retry** — `RetryEngine` + `TestAnnotationTransformer` wires configurable retries onto every `@Test` without per-method annotation. Tests in an API group (`api`, `performance`) are not retried unless they opt in with `@Retry(limit = n)`, because a retry can repeat a call that is not safe to repeat
- **Multi-browser support** — Chrome, Firefox, Edge, and Selenium Grid via `BrowserFactory`; browser selected at runtime
- **Cucumber + PicoContainer DI** — BDD support with constructor-injected `ScenarioContext`; no static shared state between step classes
- **REST Assured API layer** — typed request/response base classes with JSON Schema validation and a custom listener for Extent reporting
- **Automatic performance capture** — every click/type, page-load wait, and API call is timed and written into the Extent report on its own, with zero test-code changes; see [Automatic Performance Capture](#automatic-performance-capture)
- **Automatic API capture** — every API request the browser sends during a test (XHR/fetch/navigations) is recorded over the Chrome DevTools Protocol and saved as Postman-importable cURL files under `reports/<run>/apicapture/`, one folder per DataProvider row and invocation; see [Automatic API Capture](#automatic-api-capture)
- **Performance testing** — `PerformanceTestBase` with `measureApi`, `measurePageLoad`, and `runLoadTest` (P95/P99 stats) for explicit, SLA-asserting measurements on top of the automatic capture above
- **Security testing** — `SecurityTestBase` integrating OWASP ZAP for passive/active scans with auto-generated HTML reports
- **Observability** — async NDJSON event stream (`TestEventCollector`), flaky test tracker, failure categorizer, and resource usage accumulator
- **Extent Reports** — thread-safe HTML reporting with screenshot capture on failure and video recording support
- **Structured logging** — SLF4J + Logback with Logstash JSON encoder for ELK Stack ingestion
- **Data utilities** — Excel/YAML data library, JavaFaker integration, AES encryption utils, input validation

---

## Tech Stack

| Layer | Library | Version |
|---|---|---|
| Browser automation | Selenium Java | 4.33.0 |
| Test runner | TestNG | 7.7.0 |
| BDD | Cucumber JVM + PicoContainer | 7.21.1 |
| API testing | REST Assured | 5.5.0 |
| Reporting | ExtentReports | 5.1.2 |
| Data | Apache POI, JavaFaker, SnakeYAML | 5.4.1 / 1.0.2 / 2.6 |
| JSON | Jackson Databind, Gson | 2.18.0 / 2.11.0 |
| Logging | SLF4J + Logback + Logstash encoder | 2.0.17 / 1.5.18 / 8.1 |
| Database | MySQL Connector/J | 9.6.0 |
| Security | OWASP ZAP (via ZapSecurityUtils) | — |
| Video | WebDriver screenshots + FFmpeg (system binary) | — |
| Build | Maven (3.8.8 or newer, enforced) | 3.8.8+ |
| Java | Java (17 or newer to build; compiled with `--release 17`) | 17 |

---

## Project Structure

autoFrameX is an 8-module Maven reactor — each module owns a
distinct concern, so a consuming team only pulls in the dependencies of the
modules it actually needs (e.g. an API-only team depends on `autoframex-core`
+ `autoframex-api`, not the whole tree).

```
autoFrameX/
├── pom.xml                                # Reactor parent — packaging=pom, <modules>, <dependencyManagement>
│
├── autoframex-core/                       # Leaf module — no framework dependencies
│   └── src/main/java/
│       ├── com/framework/config/data/     # Config system (Owner-based, multi-project)
│       ├── com/framework/exception/       # FrameworkException hierarchy, Categorized
│       ├── com/framework/observability/   # TestEventCollector, FlakyTestTracker, FailureCategorizer
│       └── com/framework/utils/           # Reporter, DataLibrary, EncryptionUtils, RetryUtils, ...
│
├── autoframex-selenium/                   # depends on: core
│   ├── data/accounts.xlsx                 # Data-provider example fixture
│   ├── testng-ci.xml, testng-ci-retry.xml,
│   │   testng-parallel-smoke.xml          # Suites that only touch core+selenium classes
│   └── src/main/java/
│       ├── com/framework/selenium/        # SeleniumBase, BasePage, action classes, exceptions
│       ├── com/framework/testng/api/base/ # ProjectSpecificMethods, RetryEngine
│       ├── com/framework/utils/           # The 5 Selenium-dependent utils (WaitUtils, ScreenshotUtils, ...)
│       └── design/patterns/
│           ├── factory/browser/           # BrowserFactory, BrowserRegistry, Chrome/Firefox/Edge/Remote
│           └── object/pool/               # DriverPoolManager, WebDriverPoolFactory
│
├── autoframex-api/                        # depends on: core
│   └── src/main/java/com/api/
│       ├── design/                        # ApiClient, ResponseAPI interfaces
│       └── rest/assured/base/             # Request/response base classes, listener
│
├── autoframex-database/                   # depends on: core
│   └── src/main/java/design/patterns/database/  # AbstractDatabaseConnection, MySQLConnection, DBManager
│
├── autoframex-cucumber/                    # depends on: core, selenium, api
│   └── src/main/java/com/framework/cucumber/api/base/  # CucumberProjectBase, ScenarioContext
│
├── autoframex-performance/                 # depends on: core, selenium, api
│   └── src/main/java/com/framework/performance/  # PerformanceTestBase, ApiPerformanceUtils, ...
│
├── autoframex-security/                    # depends on: core, selenium, api
│   └── src/main/java/com/framework/security/     # SecurityTestBase, ZapSecurityUtils, InputSanitizer
│
├── autoframex-testkit/                     # depends on: all of the above (test scope)
│   └── testng.xml                          # Master aggregate suite (spans every module via <packages>)
│
├── Jenkinsfile                             # Declarative pipeline
├── Dockerfile / docker-compose.yml         # Containerised execution
├── .github/workflows/                      # ci / regression / sonar / dependency-check / secret-scan
└── CHANGELOG.md, RELEASING.md, CONTRIBUTING.md
```

Suite XML files, test fixtures (`data/accounts.xlsx`), and Cucumber
`.feature` files each live inside the module whose classpath they need —
Surefire resolves a suite file relative to the invoking module's own basedir,
not the reactor root.

---

## Getting Started

**Prerequisites**

- Java 17+ (the build compiles for Java 17 whatever newer JDK you use)
- Maven 3.8.8+ (the build fails early on an older Maven or JDK)
- Chrome / Firefox / Edge installed (or a running Selenium Grid)

**Clone and build**

```bash
git clone https://github.com/Rajeshluffy/autoFrameX.git
cd autoFrameX
mvn clean install -DskipTests
```

`install` (not just `compile`) is required even for a first build: this is a
multi-module reactor, so downstream modules (e.g. `autoframex-selenium`)
resolve upstream ones (`autoframex-core`) from the local `~/.m2` repo, not
just from in-memory reactor state.

---

## Configuration

All defaults live in `autoframex-core/src/main/resources/frameworkConfig.properties`. Override any value from the command line or `testng.xml` parameters.

```properties
# Browser
autoFrameX.browser.name=chrome
autoFrameX.browser.isheadless=false

# Waits (seconds)
autoFrameX.implicit.wait.time=10
autoFrameX.explicit.wait.time=20
autoFrameX.page.load.timeout=30

# Retry
autoFrameX.test.retry.max.limit=1
autoFrameX.element.max.retry.attempts=3
# API-style tests (TestNG groups below) are not retried unless they ask: @Retry(limit = n) always wins
autoFrameX.test.retry.api.max.limit=0
autoFrameX.test.retry.api.groups=api,performance

# Driver pool
autoFrameX.pool.max.size=5
autoFrameX.pool.min.size=2
# true (default): quit the browser after every passed test, so each test gets a new one.
# false: return it to the pool and reuse it (faster, less isolation). A failed test always discards its browser.
autoFrameX.pool.close.after.each=true
autoFrameX.pool.borrow.timeout.seconds=30
autoFrameX.pool.max.reuse.count=75

# Selenium Grid
autoFrameX.grid.enabled=false
autoFrameX.grid.hub.url=http://localhost:4444/wd/hub

# API capture (Chrome DevTools -> Postman cURL files)
autoFrameX.api.capture.enabled=false   # opt-in: set true in the child project's own properties file or suite XML
autoFrameX.api.capture.mask.secrets=false
```

**Multi-project config routing**

Each `testng.xml` passes a `configClass` parameter pointing to a `ProjectAppConfiguration` implementation. The `ProjectDirector` reads that class and loads the matching properties file — so multiple projects can share the same framework JAR with isolated configs.

```xml
<parameter name="configClass" value="com.myapp.config.data.MyAppConfiguration"/>
```

### Safer defaults

A few settings used to be hard-wired and are now decisions you can see and override. Each key can be given as a suite `<parameter>`, an environment variable or a `-D` property, in that order of priority.

| Setting | Default | Notes |
|---|---|---|
| `autoFrameX.test.retry.api.max.limit` (`retryApiLimit`) | `0` | Retries for tests in the groups below. `@Retry(limit = n)` on a method or class always wins. |
| `autoFrameX.test.retry.api.groups` (`retryApiGroups`) | `api,performance` | TestNG groups that count as API tests. |
| `noSandbox` / `NO_SANDBOX` | on in CI and containers only | Chrome and Edge `--no-sandbox`. CI is detected from `CI`, `JENKINS_URL`, `GITHUB_ACTIONS`, `KUBERNETES_SERVICE_HOST`, `container` or `/.dockerenv`. |
| `acceptInsecureCerts` / `ACCEPT_INSECURE_CERTS` | on except for production | Accept invalid TLS certificates. Production is `environment=prod` or `production`. |
| `relaxedDownloads` / `RELAXED_DOWNLOADS` | on except for production | Chrome download protection switched off for download tests. |

Configuration values follow one rule everywhere: a TestNG parameter beats an environment variable, which beats a `-D` property, which beats the default, and a value that is **blank, or a `${...}` placeholder nothing filled in, counts as not set** and falls through to the next source. Booleans must be `true` or `false`; anything else throws instead of quietly meaning `false`. An unknown environment, an empty production URL, and a missing or empty data file throw with the key or file named.

Other behaviour that changed with the same review:

- **OAuth:** a token request that is rejected now throws with the HTTP status and the server's `error` / `error_description`; it used to return `null` and let later calls go out without credentials. The cache lasts as long as the server's `expires_in` (minus 30 s), one hour if it sends none. `setOAuth` no longer also sets up Basic auth with the user's password, and when Basic, OAuth and a bearer token are all configured only one is applied: bearer, then OAuth, then Basic.
- **Database:** `DBManager` gives each thread its own connection (reopened if stale), keys managers by JDBC URL and user rather than by configuration class, and throws a `FrameworkException` with the `SQLException` as its cause when a connection or query fails. `executeQuery` no longer returns `null` on error. Call `DBManager.closeAll()` at the end of a suite; it also runs when the JVM exits. Pass values as parameters, not spliced into the SQL: `db.executeQuery("SELECT * FROM people WHERE name = ?", "O'Brien")` and `executeUpdate(sql, params...)` use prepared statements (a name with a quote breaks a built-up statement, and a value from outside can change it). A statement is cancelled after `db.query.timeout.seconds` (default 60, `0` = no limit), MySQL connections time out after 10 s unless the URL sets `connectTimeout`, and a password in a JDBC URL is shown as `***` in logs and error messages.
- **Excel data files** are opened read-only, so a data-provider run no longer rewrites `accounts.xlsx` and leaves it modified in git.

---

## Running Tests

Every suite file now lives inside the module that owns its classes, so `mvn
test` needs a `-pl <module>` to know where to look (Surefire resolves the
suite path relative to that module's own basedir, not the reactor root).

**Default suite** (`testng.xml`, spans every module — lives in `autoframex-testkit`)

```bash
mvn install -DskipTests -Djacoco.skip=true   # once, or after any core/selenium/... change
mvn test -pl autoframex-testkit
```

**Override browser / environment / headless at runtime**

```bash
mvn test -pl autoframex-testkit -Dbrowser=firefox -Denv=qa -Dheadless=true
```

**Run a specific suite** (`testng-ci.xml` lives in `autoframex-selenium`)

```bash
mvn test -pl autoframex-selenium -Dtestng.suite.file=testng-ci.xml
```

**Parallel execution**

```bash
mvn test -pl autoframex-selenium -Dtestng.suite.file=testng-ci.xml -DthreadCount=4
```

**Run via Docker**

```bash
docker-compose up --build
```

The Docker image's `MODULE`/`SUITE_FILE` env vars default to
`autoframex-testkit`/`testng.xml` — override both together if you want a
different module's suite (see `docker-compose.yml`).

---

## Framework Modules

### Selenium Layer

`SeleniumBase` implements `Browser` and `Element` interfaces and provides a robust wrapper around WebDriver — explicit waits on every interaction, stale element retry, screenshot on failure, and centralised Extent reporting. `BasePage` is the Page Object base class.

Selenium behaviour worth knowing:

- `switchToWindow(index)` waits (up to the short wait time) for a window that is still opening; `switchToWindowByTitle` / `switchToWindowByUrl` return whether a window matched, and put the browser back on its original window when none did.
- A two-locator lookup (primary and fallback) polls both together for one implicit-wait period, instead of waiting out the whole period on the primary first.
- On Selenium Grid, `sendKeys(path)` on a file input uploads the file from the test machine (the session has a `LocalFileDetector`). If `sendKeys` is refused on a remote session the step fails with the reason: the OS-dialog fallback only works against a local browser.
- The video recorder skips a frame while an alert is open; a screenshot would dismiss it.

### TestNG Lifecycle

`ProjectSpecificMethods` manages the full TestNG lifecycle:

```
@BeforeSuite  → report init
@BeforeTest   → driver pool init
@BeforeClass  → extent test node
@BeforeMethod → acquire driver from pool
@Test         → your test
@AfterMethod  → return driver to pool, capture failure screenshot/video
@AfterSuite   → flush report, shut down pool
```

### Driver Pool

`DriverPoolManager` + `WebDriverPoolFactory` maintain a bounded pool of `RemoteWebDriver` instances. Drivers are pre-warmed at startup, reused across tests up to a configurable reuse cap, and retired automatically. Borrow timeout prevents indefinite blocking in parallel runs.

### Cucumber

Step classes that drive the browser extend `CucumberProjectBase` (it gives them the Selenium actions and `getDriver()`). The lifecycle is in `CucumberHooks`, a separate class Cucumber loads from the runner's glue, because Cucumber refuses to load a step class that extends a class defining hooks. Add the package to the glue, or no browser is acquired:

```java
@CucumberOptions(glue = {"my.step.defs", "com.framework.cucumber.api.base"})
```

The hooks acquire a driver from the pool and open the app URL before each scenario (they fail the scenario if the URL cannot be opened), and take a failure screenshot, write the API capture and return the driver after it. They run for every scenario except those tagged `@api`: tag an API-only scenario `@api` and no browser is acquired for it (a tag expression on the hooks themselves; `tags` on the runner would drop those scenarios from the run instead). They include [API capture](#automatic-api-capture): with `apiCaptureEnabled` on, each scenario's browser calls are recorded from the very first page load into `reports/<run>/apicapture/<feature>/<scenario>_line<N>_<NN>/` (the scenario's line number tells the rows of a Scenario Outline apart). `CucumberScenarioContext` is a PicoContainer-injected shared state object — step definition classes receive it via constructor injection, eliminating static fields.

### REST Assured API Layer

`RestAssuredBase` provides typed GET/POST/PUT/DELETE helpers. `RestAssuredResponseBase` adds JSON Schema validation via `verifySchema()`. `RestAssuredListener` logs every request/response to the Extent report automatically.

### Performance Module

`PerformanceTestBase` extends `ProjectSpecificMethods` and skips WebDriver acquisition for API-only tests. Key helpers:

- `measureApi(name, supplier, slaMs)` — times a single API call and **fails the test** when the SLA is breached or the call returned an HTTP error status
- `measurePageLoad(pageName, slaMs)` — reads Navigation Timing API from a live page; fails when the SLA is breached or the timing is unavailable
- `runLoadTest(supplier, threads, durationSeconds)` — every worker thread hammers `supplier` until `durationSeconds` elapses; returns P95/P99 latency stats (a run with no successful call is a failure, not a pass)

To tune thresholds without failing builds, run with `-Dperformance.sla.enforce=false`: breaches are then only logged.

`PerformanceTestBase` starts no browser (and does not even start the browser pool) for API tests. A page test asks for one with `openBrowser(url)`, then calls `measurePageLoad` / `getPageTimingMetrics`; the browser goes back to the pool when the test ends. A load test's result keeps up to five distinct failure reasons (`HTTP 401`, `ConnectException: Connection refused`) next to the failure rate.

These are explicit, opt-in, and SLA-asserting — call them yourself when you need a hard pass/fail threshold on one specific call. For passive, always-on timing visibility across every UI/API operation with no code changes, see [Automatic Performance Capture](#automatic-performance-capture) below.

### Security Module

`SecurityTestBase` integrates OWASP ZAP:

- `@BeforeSuite` verifies ZAP is reachable (a warning, not a failure, if it is not)
- Start ZAP with an API key and give the tests the same one (`-Dzap.api.key=...` or `ZAP_API_KEY`); it is sent as an `X-ZAP-API-Key` header, never in a URL. `-config api.disablekey=true` still works but lets anyone who can reach the port control ZAP
- A security suite starts no browser: `SecurityTestBase` overrides the inherited `@BeforeTest` that would pre-warm Chrome
- `InputSanitizer.containsSqlInjection` / `containsXss` report statement-shaped SQL and real markup and event-handler payloads, not the bare words "select" or "update"; `sanitize` also escapes line breaks so a value cannot forge a log line
- `@AfterSuite` generates an HTML report into `reports/zap/`
- Helpers for passive scan, active scan, alert assertions, and secret masking via `SecretDetector`
- A security check never passes because nothing was checked: `runPassiveScan(url)` waits for the crawl and for ZAP's analysis queue to drain, and `assertNoCriticalAlerts(url)` re-checks the queue, refuses to pass when ZAP has recorded no traffic for the target, and treats any HTTP error, ZAP error body or unexpected reply as a failure rather than as "no alerts"
- `startPassiveScan(url)` in `SecurityTestBase` now waits as well (up to 5 minutes; `runPassiveScan(url, seconds)` sets the budget). It used to return as soon as the crawl was accepted
- `autoframex-security` has its own unit suite (`testng.xml`) that runs against a fake ZAP server, so it needs no ZAP install. Its replies are modelled on ZAP's documented API and have not been compared with a live ZAP

### Observability

- `TestEventCollector` — singleton async NDJSON writer (`logs/test-events.json`), ELK-ready
- `FlakyTestTracker` — tracks pass/fail history per test to surface flaky tests
- `FailureCategorizer` — classifies failures by type (timeout, assertion, infra, etc.)
- `CorrelationContext` — propagates a correlation ID across threads for distributed tracing

### Utilities

Split across `autoframex-core` (no Selenium dependency) and
`autoframex-selenium` (needs a live `WebDriver`):

| Class | Purpose | Module |
|---|---|---|
| `Reporter` | Thread-safe Extent report wrapper | core |
| `DataLibrary` | Excel + CSV/TSV test data reader with cache | core |
| `EncryptionUtils` | AES-256 encrypt/decrypt for credentials | core |
| `LogUtils` | SLF4J structured logging helpers | core |
| `RetryUtils` | Programmatic retry with backoff | core |
| `PerfClock` | Stopwatch helper behind automatic performance capture | core |
| `WaitUtils` | Fluent/explicit wait helpers | selenium |
| `ValidationUtils` | Common assertion helpers | selenium |
| `ScreenshotUtils` | Full-page and element screenshots | selenium |
| `ScreenshotStore` | Per-thread screenshot bytes for report embedding | selenium |
| `VideoRecorder` | Failure-only video capture — WebDriver screenshots assembled via FFmpeg | selenium |

---

## Automatic Performance Capture

Every UI element interaction, page-load wait, and API call is timed automatically and written into the Extent report as it happens — no test-author code changes needed. This is separate from `PerformanceTestBase`'s explicit helpers above: automatic capture is pure observability (what happened, on every call), the explicit helpers are for asserting a hard SLA on one call you've picked out.

**What gets captured, and where it shows up:**

| Operation | Instrumented in | Example report line |
|---|---|---|
| Click | `ClickActions.click()` | `Clicked element: Login (wait: 34ms, click: 294ms, total: 328ms)` |
| Type | `TypeActions.clearAndType()` / `typeAndTab()` / `typeAndEnter()` | `Typed text: demo (wait: 81ms, type: 108ms, total: 189ms)` |
| Element visibility check | `ElementInspectionActions.verifyDisplayed()` | `Element is displayed: a (render/visibility wait: 44ms)` |
| Page load | `WaitActions.waitForPageAndApiReady()` — already called by every page object after a navigating action | `Page and all API calls are fully loaded (page load: 328ms)` |
| API call (every verb) | `RestAssuredListener` — the REST Assured `Filter` every `RestAssuredBase` request already passes through | `API POST https://.../incident — 1411ms (status 201)` |

`wait`/`render` time is how long the framework's own explicit wait spent before the element became clickable/visible — a real signal for slow-rendering UI, not just network latency.

**Concurrent load tests get this too.** `ApiPerformanceUtils.runLoadTest()`'s worker threads propagate the calling thread's report node via `Reporter.captureCurrentNode()`/`runWithNode()` — without this, a plain `ThreadLocal` wouldn't carry over to pool-created threads, and every call made during a load test would silently vanish from the report. Confirmed against a real API: a 10-thread/30s load test went from 0 of 176 calls reported to 180 of 180 after the fix.

**Scope:** this times the API calls the test itself makes via `RestAssuredBase`. The raw XHR/fetch traffic the app fires on its own in the browser is covered separately, by [Automatic API Capture](#automatic-api-capture) below.

---

## Automatic API Capture

Every API request the browser sends while a test runs — XHR, `fetch`, document navigations, WebSockets — is recorded over the Chrome DevTools Protocol (`Network` domain) and written into the run's report folder as Postman-importable cURL commands. No test-code changes: `ProjectSpecificMethods` starts the capture in `@BeforeMethod` once the driver is bound and flushes it in `@AfterMethod` before the driver goes back to the pool.

**Folder layout** — one folder per DataProvider row *and* per invocation, so retries and repeated rows never overwrite each other:

```
reports/<run>/apicapture/
  TC001_VerifyLogin/                      <- @TestMetadata.name (class simple name as fallback)
    verifyLogin_ACC001_01/                <- <method>_<accountId>_<NN>; NN counts runs of this method in the JVM
      001_GET_login.txt                   <- exactly one cURL per file (Postman -> Import -> Raw text)
      002_POST_authenticate.txt
      collected_apis.txt                  <- every request in send order, with status + resource type
      01_GPN_loaded/                      <- optional bookmark folder, see captureApiSnapshot / captureApis below
    verifyLogin_ACC002_02/
      ...
```

`<accountId>` is the DataProvider row key (`AccountData.getAccountId()`); non-data-driven tests get `targeted`. Each invocation also logs one `INFO` step in the Extent report with the request count and folder.

**What is filtered out:** static assets (by CDP resource type — Stylesheet, Image, Font, Script, … — and by file extension as a fallback), CORS preflights, and `data:`/`blob:`/browser-internal URLs. Large POST bodies that Chrome leaves out of the event are fetched while the request is in flight; bodies that still can't be read (binary/multipart) are flagged in `collected_apis.txt`.

**Configuration** (`frameworkConfig.properties`, overridable via `-D`, env var, or suite `<parameter>` like every other key):

| Key | Default | Effect |
|---|---|---|
| `autoFrameX.api.capture.enabled` (`apiCaptureEnabled` / `API_CAPTURE_ENABLED`) | `false` | Opt-in per project. Set it to `true` in the child project's own properties file (searched before `frameworkConfig.properties`) or in a suite XML with `<parameter name="apiCaptureEnabled" value="true"/>`. `false` opens no CDP session at all. |
| `autoFrameX.api.capture.mask.secrets` (`apiCaptureMaskSecrets` / `API_CAPTURE_MASK_SECRETS`) | `false` | Write `***` for Authorization/Cookie-style headers and credential-looking body fields. Off by default because the files exist to be replayed; turn it on for reports that leave the team. |

**Browser support:** Chromium-based browsers only (Chrome, Edge). Firefox has no CDP, so the capture is skipped with an `INFO` log and the test runs normally. On Selenium Grid the driver is augmented to reach the node's CDP endpoint. Any failure to attach is logged and never fails a test. The implementation (`ApiCaptureActions`) deliberately does not use `driver.getDevTools()`: Selenium bundles CDP packages only for the three Chrome releases current at its own release and hands back a no-op stub for anything much newer (Chrome 155 on Selenium 4.33, for example), and it follows only one page. Instead the class opens its own WebSocket to the browser and speaks the long-stable `Target`/`Network` protocol directly, so a Chrome or Selenium upgrade never breaks the build or the capture. Verified end-to-end on Chrome 155 with Selenium 4.33.

**Every window and iframe is followed.** The capture uses browser-level auto-attach: a window the application opens (for example GPN launched from the AlfaDOCK home page) is held paused by Chrome until recording is switched on for it and only then released, so even its very first request is captured and no test code has to know the window exists. Cross-origin iframes are followed the same way. CORS preflight `OPTIONS` calls are not recorded, and a request that never gets a response keeps status `-` with Chrome's reason (for example `net::ERR_ABORTED`) written under it in `collected_apis.txt`.

**Bookmarks: `captureApiSnapshot` and `captureApis`.** Recording is continuous and automatic; these two methods (on `SeleniumBase`, so tests, page objects and Cucumber steps all have them, the same way they have `click`) only cut a labelled bookmark in the recording. Each bookmark waits until the page is quiet (nothing in flight and nothing new started for 500 ms, at most 15 s), then saves every request and response since the previous bookmark into its own numbered folder and keeps recording.

```java
gpnPageFinder();                                    // ends with captureApiSnapshot("GPN_loaded")
captureApis("filter_clicked", () -> click(filter));  // run the action, wait for quiet, bookmark
List<CapturedApi> calls = captureApis("save_clicked", () -> click(save));
click(other); captureApiSnapshot("other_clicked"); // same thing in two steps
```

`captureApis` keeps only the calls made from the moment the action starts, so one action's late response cannot leak into the next, and it returns them so a test can assert on them (for example that the save call answered 200). The wait is counted from the end of the action, so a call fired shortly after a click (a debounce under 500 ms) is still caught; for a longer debounce use the overload `captureApis(phase, action, quietMillis, maxWaitMillis)`. If the action throws, the calls so far are still saved and the exception is rethrown. When capture is not running, both methods do nothing (the action still runs), so they are safe in shared code.

```
apicapture/<testcase>/<method>_<row>_<run>/
  collected_apis.txt             the full timeline, every request in order
  001_POST_save.txt              one cURL per request (Postman -> Import -> Raw text)
  001_POST_save_response.txt     status, headers and body of the answer
  01_GPN_loaded/                 bookmark folders, numbered in the order taken
  02_filter_clicked/
  03_save_clicked/
```

**Responses.** Every request keeps the response status and headers. The body is saved for XHR/fetch calls (not documents, images or no-content answers), cut at 500,000 characters, and binary bodies are skipped with a note. With `apiCaptureMaskSecrets` on, `Set-Cookie`-style headers and credential-looking body fields are written as `***`. A test records at most 5,000 requests.

**Known limits.** Capture starts the moment the browser is acquired, before it opens the start page, so the first page load is recorded. Calls made before a browser exists (`@BeforeSuite`, `@BeforeClass`) are not. A call fired after the page has been quiet for longer than the wait lands in the next bookmark instead. A request cancelled by a page navigation has no response; its file says why when Chrome reports it. WebSockets and event streams are recorded but never hold up a bookmark. Requests made by background workers are not recorded.

---

## CI/CD

The `Jenkinsfile` defines a declarative pipeline with parameterised browser, environment, headless flag, module, suite file, and thread count. Stages: Checkout → Inject Configs → Build & Install → Test → Reports → (optional) SonarQube.

GitHub Actions workflows are in `.github/workflows/`:

| Workflow | Trigger | What it runs |
|---|---|---|
| `ci.yml` | Push / PR to any branch | **Every module**: `mvn clean install` runs each module's own default suite, then the quality gates (below). Then the parallel smoke suite and the retry-engine check on `autoframex-selenium`, and a Cucumber lifecycle smoke (`autoframex-cucumber/testng-smoke.xml`: the hooks, a step class and the pool with a real headless Chrome, no network). This is the merge gate. |
| `regression.yml` | Manual dispatch | Chosen via the `module` input (default `autoframex-testkit`) |
| `sonar.yml` | Push / PR to main | Every module's tests with coverage, then Sonar analysis at the reactor root; fails if the Sonar quality gate fails. Skipped, with a notice, when the `SONAR_TOKEN` / `SONAR_HOST_URL` secrets are not set (or on a fork's pull request) |
| `dependency-check.yml` | Weekly + manual dispatch | Reactor root (scans every module) |
| `secret-scan.yml` | Push / PR | gitleaks over the pushed commits (`.gitleaks.toml`) |
| `Jenkinsfile.api` (Jenkins) | Per job | One module's suite with Maven, no Docker; see `autoframex-api/README.md` |

There are no ZAP-scan or load-test workflows here: a scan or load test needs a test class that
targets a real application, and this repo only provides the base classes (`SecurityTestBase`,
`PerformanceTestBase`). Run those from the project that owns the application under test.
The two workflows that pretended to (`security.yml`, `performance.yml`) selected a TestNG
group that no test declared, so they went green without running anything, and were removed.

---

## Code Quality

SonarQube analysis and JaCoCo coverage are wired into the Maven build. Run
`sonar:sonar` at the reactor root (not `-pl`-scoped) so it analyzes every
module's source:

```bash
mvn sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.login=<token>
```

Coverage reports are generated per-module during `mvn test` via the JaCoCo
plugin (`<module>/target/site/jacoco/jacoco.xml`) and consumed by SonarQube —
only modules whose tests actually ran in a given invocation will show
non-zero coverage; there's no cross-module `jacoco:report-aggregate` step yet.

### Quality gates (`mvn verify` / `mvn install`)

These fail the build; they are not reports:

| Gate | Fails when | Notes |
|---|---|---|
| Build environment | Maven older than 3.8.8, JDK older than 17, or any plugin without a pinned version | `maven-enforcer-plugin` in the root pom, runs in `validate`; compiles with `--release 17` |
| Tests | any test of any module fails | each module runs its own default suite |
| JaCoCo line coverage | a module drops below its floor, `jacoco.minimum.line` in that module's pom | floors sit a few points under today's coverage (api 88%, core 67%, performance 70%, security 60%, database 43%, selenium 40%, cucumber 27%). Raise a floor when coverage rises. |
| PMD | a priority 1 or 2 violation | priority 3 to 5 are listed but do not fail; raise `pmd.gate.priority` to 3 once they are cleared (`-Dpmd.gate.priority=3` shows what that would fail on) |
| SpotBugs | a High-priority finding | `-Dspotbugs.gate.threshold=Medium` also gates Medium findings (one is known: `AbstractDatabaseConnection.connect()`) |

Not gates: Checkstyle (its Google config does not match this code's tabs/4-space style, so it
reports about 15,000 findings that say nothing about quality) and OWASP Dependency-Check
(weekly, fails only at CVSS 9 or above).

For a deliberately partial run (`-Dtest=...`, one suite) a module covers less than its floor by
design: add `-Djacoco.skip=true`.

---

## Secrets

Never commit a credential. Pass credentials from the environment or the CI secret store (the JMeter plan,
for example, has no default password or API key, see `performance/jmeter/README.md`).

What guards this now:

- `.gitignore` blocks the credential files this project has used (`alfaDOCKConfig.properties`,
  `leafTapConfig.properties`, `.env*`, key and keystore files) and generated JMeter results.
- `secret-scan.yml` runs gitleaks on the commits of every push and pull request (`.gitleaks.toml`; test
  sources are allowlisted because they hold deliberately fake secrets).
- Failure messages written to `logs/test-events.json` are masked before they are written.

**What is already in git history.** Deleting a file from the latest commit does not remove it from history.
Credentials for AlfaDOCK, Leaftaps and the Leaftaps database were committed in `alfaDOCKConfig.properties` and
`leafTapConfig.properties` (added in `46f55e5`, removed in `5caa185`) and are still reachable from remote
branches such as `stableversion0.1` and `frameWork`. The JMeter result files, which record an ERP key in
their request URLs, were tracked until they were untracked and ignored. To close this:

1. **Rotate** those credentials and the ERP key. This is the step that makes them safe; anything else only
   hides a value that has already been exposed.
2. Optionally **rewrite history** so they stop being downloadable (this has not been done; it needs a force
   push of every branch, and everyone must re-clone):

   ```bash
   git clone --mirror <repository-url> autoFrameX-mirror.git && cd autoFrameX-mirror.git
   git filter-repo --invert-paths \
     --path src/main/resources/alfaDOCKConfig.properties \
     --path src/main/resources/leafTapConfig.properties \
     --path src/test/resources/alfaDOCKConfig.properties \
     --path-glob 'performance/jmeter/results/*'
   git push --force --mirror
   ```

## Contributing and releasing

[CONTRIBUTING.md](CONTRIBUTING.md) covers adding a project and changing the framework; [CHANGELOG.md](CHANGELOG.md) lists what changed (with *behaviour change* marked); [RELEASING.md](RELEASING.md) is the release procedure.

## Dependencies

Versions are managed in the root `pom.xml`. Dependabot opens weekly pull requests for Maven dependencies
(related libraries grouped) and for the GitHub Actions the workflows use (`.github/dependabot.yml`).

Two libraries are deliberately kept off the classpath: `snakeyaml:android:1.23` (pulled in by Faker; the real
SnakeYAML 2.x jar is used instead) and Lombok (declared at compile scope by ExtentReports, but only needed to
compile it). OWASP Dependency-Check runs weekly and fails only at CVSS 9 or above; lower
`failBuildOnCVSS` once a first scan is clean.
