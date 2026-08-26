# autoFrameX — Interview Guide

A structured reference for explaining the framework in technical interviews.
Use the sections below to answer questions at any depth — from a 30-second elevator pitch to a 30-minute deep dive.

---

## 1. Elevator Pitch (30 seconds)

> "autoFrameX is an enterprise test automation framework I built from scratch using Java 17, Selenium 4, and TestNG 7. It's distributed as a shared Maven JAR so multiple product teams can consume it without duplicating infrastructure code. It covers the full testing spectrum — UI, API, performance, and security — and includes built-in observability, structured logging, and CI/CD integration out of the box."

---

## 2. Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17+ |
| Build | Maven | 3.9+ |
| UI Automation | Selenium WebDriver | 4.33.0 |
| Test Runner | TestNG | 7.7.0 |
| BDD | Cucumber + PicoContainer | 7.21.1 |
| API Testing | REST Assured | 5.5.0 |
| Reporting | ExtentReports | 5.1.2 |
| Logging | SLF4J + Logback (JSON) | 2.0.5 / 1.4.7 |
| Test Data | Apache POI (Excel), JavaFaker | 5.2.4 / 1.0.2 |
| Config | Owner library | 1.0.12 |
| Database | MySQL via JDBC | 9.6.0 |
| Security | OWASP ZAP (REST API) | — |
| Mock Server | WireMock | 3.9.1 |
| Screen Recording | Monte Screen Recorder | 0.7.0 |
| CI/CD | Jenkins + GitHub Actions | — |
| Containerization | Docker + docker-compose | — |
| Code Quality | JaCoCo + SonarQube | 0.8.12 / 3.11.0 |

---

## 3. Framework Architecture

### 3.1 High-Level Design

```
autoFrameX (shared JAR)
│
├── Selenium Core          → SeleniumBase, BasePage, Browser/Element interfaces
├── TestNG Lifecycle       → ProjectSpecificMethods, RetryEngine, TestAnnotationTransformer
├── Configuration System   → ConfigManager, ProjectDirector, Owner-based properties
├── Object Pool            → DriverPoolManager, BrowserFactory, BrowserType enum
├── API Layer              → RestAssuredBase, ApiClient/ResponseAPI interfaces
├── Cucumber BDD           → CucumberProjectBase, PicoContainer DI, ScenarioHooks
├── Observability          → TestEventCollector (NDJSON), FlakyTestTracker, CorrelationContext
├── Performance            → PerformanceTestBase, ApiPerformanceUtils, PagePerformanceUtils
├── Security               → SecurityTestBase, ZapSecurityUtils, SecretDetector, InputSanitizer
├── Database               → DBManager, DatabaseConnectionFactory, MySQLConnection
└── Utilities              → WaitUtils, DataLibrary, EncryptionUtils, ValidationUtils, FakerDataFactory
```

### 3.2 Package Map

| Package | Key Classes | Responsibility |
|---|---|---|
| `com.framework.selenium.api.base` | SeleniumBase, BasePage | WebDriver wrapper, Page Object base |
| `com.framework.selenium.api.design` | Browser, Element, Locators | Interfaces defining the interaction contract |
| `com.framework.testng.api.base` | ProjectSpecificMethods, RetryEngine | TestNG lifecycle, retry logic |
| `com.framework.config.data` | ConfigManager, ProjectDirector | Multi-project configuration routing |
| `design.patterns.object.pool` | DriverPoolManager, BrowserFactory | Thread-safe WebDriver pool |
| `design.patterns.factory.browser` | BrowserType, ChromeBrowser, EdgeBrowser | Browser creation via enum factory |
| `design.patterns.database.*` | DBManager, MySQLConnection | Database abstraction and connection pooling |
| `com.api.rest.assured.base` | RestAssuredBase, RestAssuredResponseBase | Typed REST API client |
| `com.framework.cucumber.api.base` | CucumberProjectBase, CucumberScenarioContext | BDD lifecycle and shared state |
| `com.framework.observability` | TestEventCollector, FlakyTestTracker, CorrelationContext | NDJSON event stream, flaky detection |
| `com.framework.performance` | PerformanceTestBase, ApiPerformanceUtils | Load tests, P95/P99, Navigation Timing |
| `com.framework.security` | SecurityTestBase, ZapSecurityUtils | OWASP ZAP integration, secret detection |
| `com.framework.utils` | WaitUtils, DataLibrary, EncryptionUtils, Reporter | Shared utilities |

---

## 4. API Layer Design

The API layer is split into interfaces and implementation — consuming tests never import REST Assured directly.

### Interfaces (`com.api.design`)
| Interface | Key Methods |
|---|---|
| `ApiClient` | `get()`, `post()`, `put()`, `delete()` — typed HTTP methods |
| `ResponseAPI` | `getStatusCode()`, `getBodyAs()`, `getJsonPath()`, `verifySchema()` |

### Implementation (`com.api.rest.assured.base`)
| Class | Purpose |
|---|---|
| `RestAssuredBase` | Implements ApiClient; wraps REST Assured for type-safe requests |
| `RestAssuredResponseBase` | Implements ResponseAPI; JSON Schema validation via `verifySchema()` |
| `RestAssuredListener` | REST Assured Filter; auto-logs every request/response to Extent report with pretty-printing |
| `RequestAuthentication` | Handles BASIC, BEARER, and OAuth token auth; caches tokens with 3600s expiry, tracks expiry per-instance (thread-safe), supports `refreshOAuthToken()` |
| `ResponseContentType` | Enum constants for JSON, XML, HTML content types |

**Key design point:** `RequestAuthentication` manages OAuth token state at the instance level (not static), so parallel threads each have their own token lifecycle. Token expiry is checked with `Instant.now().isBefore(expiryInstant)` before every request.

---

## 5. Design Patterns — What, Where, and Why

This is the most common deep-dive area in interviews. Be ready to explain each pattern with a concrete example from the framework.

### Singleton
**Where:** ConfigManager, DriverPoolManager, TestEventCollector, Reporter  
**Why:** These hold shared state (config, driver pool, report instance) that must be initialized once and accessed globally across threads.  
**How:** Double-checked locking with `volatile` instance field; initialization is synchronized, reads are not.

### Factory (Enum Factory)
**Where:** `BrowserType` enum — Chrome, Firefox, Edge, RemoteGrid  
**Why:** Each enum constant knows how to create its own WebDriver. Adding a new browser means adding one enum constant, not modifying a switch statement.  
**Code shape:**
```java
public enum BrowserType {
    CHROME {
        @Override
        public WebDriver createDriver(BrowserConfig config) { ... }
    },
    FIREFOX { ... }
}
```

### Object Pool
**Where:** `DriverPoolManager`  
**Why:** Creating a new WebDriver takes 2–4 seconds. A pool pre-warms N drivers and lends them to tests, cutting per-test startup cost to near zero.  
**Key behaviors:**
- Pre-warmed pool (minPoolSize drivers created at suite start)
- Blocking borrow with timeout (no indefinite waits in parallel runs)
- State machine per driver: `IDLE → IN_USE → POISONED`
- Reuse cap: drivers retired after N reuses to prevent memory leaks

### Template Method
**Where:** `ProjectSpecificMethods` (TestNG lifecycle), `SeleniumBase` (element interactions)  
**Why:** The lifecycle steps (@BeforeSuite → @BeforeTest → @BeforeMethod → test → @AfterMethod → @AfterSuite) are fixed; only the test body varies. The base class defines the skeleton; subclasses fill in the blanks.

### Strategy
**Where:** `ProjectAppConfiguration` implementations  
**Why:** Each project (LeafTaps, AlfaDOCK, Alfa3DViewer) has different URLs, credentials, and timeouts. Each implements the same interface; the framework picks the right one at runtime via `configClass` in testng.xml.

### Builder
**Where:** `TestEvent`, `ProjectConfigBuilder`, `RequestSpecBuilder`  
**Why:** These objects have many optional fields. Builder pattern avoids telescoping constructors and makes construction readable.

### Facade
**Where:** `DBManager`, `DriverPoolManager`  
**Why:** Hides the complexity of connection pooling and driver lifecycle behind a simple `getConnection()` / `borrowDriver()` API.

### Observer / Drain Pattern
**Where:** `ResourceUsageAccumulator` → `TestEvent` in `Reporter.tearDownTest()`  
**Why:** Metrics accumulate during a test in a ThreadLocal map. At teardown, the map is *drained* (read then cleared) into the TestEvent before flushing to NDJSON. Drain (not copy) prevents stale data leaking into the next test on the same thread.

---

## 5. Thread Safety — How Parallel Tests Don't Interfere

This is a critical topic for any senior SDET interview.

| Mechanism | Used For |
|---|---|
| `ThreadLocal<WebDriver>` | Each thread gets its own driver — no sharing |
| `ThreadLocal<CorrelationContext>` | Each test has its own traceId, buildId |
| `ThreadLocal<ResourceUsageAccumulator>` | Metrics don't bleed between tests on the same thread |
| `ConcurrentHashMap` | ConfigManager cache, DataLibrary cache, Reporter node map |
| `AtomicInteger` / `AtomicReference` | Driver state machine (IDLE/IN_USE/POISONED) in pool |
| `synchronized` init block | Singleton initialization (double-checked locking) |
| Blocking queue with timeout | DriverPoolManager borrow — prevents deadlock under load |

**Key point to make in interviews:** ThreadLocal is not a silver bullet. The drain pattern in ResourceUsageAccumulator is necessary because ThreadLocal values persist across test methods on the same thread in a pool. Without draining, test N's metrics would appear in test N+1's event.

---

## 6. Multi-Project Configuration

**Problem:** Three product teams share one framework JAR but have different environments, credentials, and settings.

**Solution:** The `configClass` parameter in testng.xml points to a project-specific class that implements `ProjectAppConfiguration` (which extends both `FrameworkConfiguration` and `DatabaseConfiguration`).

```xml
<!-- LeafTaps testng.xml -->
<parameter name="configClass" value="com.leaftaps.config.LeafTapsConfig"/>

<!-- AlfaDOCK testng.xml -->
<parameter name="configClass" value="com.alfadock.config.AlfaDockConfig"/>
```

`ProjectDirector` reads this parameter and loads the matching `.properties` file. The framework never hard-codes a project name — it's fully data-driven.

---

## 7. Observability Platform

A differentiator that most frameworks don't have. Explain this to stand out.

### Components

| Component | What It Does |
|---|---|
| `CorrelationContext` | Assigns traceId, buildId, executionId to every test via ThreadLocal |
| `TestEvent` | Immutable POJO (24 fields) capturing test outcome, duration, failure category, resource usage |
| `TestEventCollector` | Async NDJSON writer — appends one JSON line per test to `logs/test-events.json` |
| `FlakyTestTracker` | Sliding-window flaky score (0.0–1.0); classifies tests as STABLE / FLAKY / QUARANTINED |
| `FailureCategorizer` | Maps exceptions to categories: ASSERTION / TIMEOUT / ELEMENT_NOT_FOUND / NETWORK / UNKNOWN |
| `ResourceUsageAccumulator` | Tracks memory, CPU, thread count per test; drained into TestEvent at teardown |

### Why NDJSON?
Each line is a valid JSON object. ELK Stack (Elasticsearch + Logstash + Kibana) and Splunk can ingest NDJSON directly — no parsing step needed. Teams can build dashboards on test health without any additional tooling.

### FlakyTestTracker — Sliding Window
Uses a configurable window (e.g., last 10 runs) rather than a simple pass/fail counter. A test that passes 7/10 times is FLAKY; 3/10 is QUARANTINED. This is more accurate than a binary flag because it captures intermittent failures that a simple counter would miss.

---

## 8. Performance Testing

**PerformanceTestBase** skips WebDriver entirely (overrides `preCondition`/`postCondition` to no-ops) — no browser is launched for performance tests.

### API Performance
```java
// Single call with SLA assertion
measureApi(() -> apiClient.get("/users"), 500); // fails if > 500ms

// Load test
LoadTestResult result = runLoadTest(() -> apiClient.get("/users"), 10, 100);
// 10 threads, 100 iterations each → P95, P99, throughput
```

### Page Performance
Uses the browser's **Navigation Timing API** via JavaScript execution — no third-party tool needed:
```java
measurePageLoad("https://app.example.com/dashboard", 3000); // SLA: 3s
```
Captures: DNS lookup, TCP connect, TTFB, DOM interactive, DOM complete, load event.

---

## 9. Security Testing

**SecurityTestBase** integrates OWASP ZAP without the ZAP Java client — uses RestAssured to call the ZAP REST API directly. This keeps the dependency tree lean.

### What it does
- `@BeforeSuite` — verifies ZAP daemon is reachable
- `@AfterSuite` — generates HTML report to `reports/zap/`
- `ZapSecurityUtils` — starts passive/active scans, retrieves alerts by risk level
- `SecretDetector` — regex patterns to find hardcoded API keys, passwords, tokens in logs and responses
- `InputSanitizer` — detects SQL injection and XSS patterns; sanitizes values before logging

### Why ZAP via REST, not the Java client?
The ZAP Java client adds ~50MB to the dependency tree and has transitive conflicts with Selenium. Calling the REST API via RestAssured achieves the same result with zero additional dependencies.

---

## 10. Cucumber BDD Integration

### Dependency Injection with PicoContainer
Step definition classes don't use static fields. Instead, PicoContainer injects shared state objects:

```java
public class UiSteps {
    private final UiScenarioContext context; // injected by PicoContainer

    public UiSteps(UiScenarioContext context) {
        this.context = context;
    }
}
```

`CucumberScenarioContext` holds the driver, page objects, and scenario-scoped data. It's created fresh per scenario and shared across all step classes in that scenario.

### ScenarioHooks Bridge
`ScenarioHooks` needs to call `DriverPoolManager` for driver lifecycle, but PicoContainer would create a circular injection if DriverPoolManager were injected directly. The solution: `scenarioPlaceholder()` — a Method handle bridge that calls DriverPoolManager without injecting it into the PicoContainer graph.

---

## 11. CI/CD Pipeline

### GitHub Actions
| Workflow | Trigger | What Runs |
|---|---|---|
| `ci.yml` | Push / PR | Browser-free smoke suite (`testng-ci.xml`) — fast, no Chrome needed |
| `regression.yml` | Manual dispatch | Full suite — installs Chrome, runs all tests |

### Jenkins
- Parameterized pipeline with `configClass`, `browser`, `env` parameters
- Quality gate: build fails if code coverage drops below 50% (JaCoCo)
- Notification stubs for Slack/email

### Docker
```dockerfile
FROM maven:3.9-eclipse-temurin-17
# Chrome installed for headless execution
```
`docker-compose.yml` mounts `reports/`, `logs/`, and `surefire-reports/` as volumes so artifacts survive container exit. Selenium Grid config is included but commented out — teams enable it when they need distributed execution.

---

## 12. Utilities — Deep Dive

### RetryEngine & TestAnnotationTransformer
`RetryEngine` implements TestNG's `IRetryAnalyzer`. Key behaviors beyond simple retry count:
- **Deterministic failure detection:** if the same source line fails twice in a row, retrying stops — it's a code bug, not a flake
- **Data-driven tracking:** retry key includes a `paramHash` so each row of a data-driven test is tracked independently
- **ConcurrentHashMap** stores per-invocation state so parallel tests don't share retry counters

`TestAnnotationTransformer` wires `RetryEngine` to every `@Test` method automatically via a single-line override:
```java
annotation.setRetryAnalyzer(RetryEngine.class);
```
Registered via `META-INF/services/org.testng.ITestNGListener` — no testng.xml listener entry needed.

### @TestMetadata Annotation
```java
@TestMetadata(name = "Login smoke", category = "ui", allRows = false)
```
- `allRows = false` (default) — only the first Excel row runs; used for smoke tests
- `allRows = true` — every row becomes a separate test invocation; used for regression data-driven runs

### ValidationUtils — Hard and Soft Assertions
Hard assertions (`assertEquals`, `assertTrue`) fail immediately. Soft assertions collect all failures and report them together at the end:
```java
ValidationUtils.SoftAssert soft = new ValidationUtils.SoftAssert();
soft.assertEquals(actual, expected, "field mismatch");
soft.assertTextContains(body, "Welcome");
soft.assertResponseCode(response, 200);
soft.assertAll(); // throws if any failure was collected
```
`getFailures()` and `isPassing()` allow programmatic inspection before `assertAll()`.

### ScreenshotUtils — Failure Evidence Bundle
`captureFailureEvidence()` bundles three artifacts into a `FailureEvidence` value object:
1. **Viewport screenshot** — PNG of the visible area
2. **DOM snapshot** — full page source saved as `.html`
3. **Browser console logs** — `LogType.BROWSER` entries captured via WebDriver logging API

Full-page screenshots use scroll-and-stitch: the page is scrolled by viewport height increments and each slice is stitched into one image.

### RetryUtils — CircuitBreaker
`RetryUtils` wraps any `Supplier<T>` with exponential backoff and a CircuitBreaker:

**Backoff schedule:** 500ms → 1000ms → 2000ms → 4000ms → 8000ms (cap)

**CircuitBreaker states:**
- `CLOSED` — normal; calls pass through
- `OPEN` — failure threshold exceeded; calls throw `CircuitOpenException` immediately (no attempt)
- `HALF_OPEN` — after `resetTimeoutMs` (default 30s), one trial call is allowed; success → CLOSED, failure → OPEN

Overloads: `retryVoid()` for no-return operations, `tryRetry()` (non-throwing, returns `Optional`).

### EncryptionUtils — AES-256/GCM
- Algorithm: `AES/GCM/NoPadding` with 128-bit tag length
- A fresh 12-byte random IV is generated per encryption; IV is prepended to the ciphertext before Base64 encoding
- Key resolution priority: system property → environment variable → fallback default
- `maskSensitiveValues(text)` applies regex patterns (`PASSWORD_PATTERN`, `BEARER_PATTERN`) and replaces matches showing only first/last 2 characters: `pa****rd`

### LogUtils — Structured Logging Helpers
All log lines are prefixed with `[T-{threadId}]` so parallel test output is traceable in a single log file.

Key methods:
- `startTimer(name)` / `stopTimer(name)` — measures elapsed time for any named operation
- `stopTimerWithSla(name, maxMs)` — logs a WARNING if elapsed exceeds SLA but does **not** throw; non-blocking SLA check
- `logErrorContext(message, Map<String,Object> metadata)` — emits structured key-value pairs alongside the error message for ELK ingestion

### VideoRecorder
Records test execution as AVI video using Monte Screen Recorder:
- 15 fps, TechSmith lossless codec, 60-second keyframe interval, audio disabled
- Output: `reports/videos/{testName}.avi`
- Detects headless environments and silently skips recording — no test failure if a display isn't available
- Lifecycle: `startRecording()` in `@BeforeMethod`, `stopRecording()` in `@AfterMethod`

### WireMock Mock Server
`WireMockManager` (test-scoped) provides isolated API testing without hitting real endpoints:
```java
WireMockManager.startServer(port);
WireMockManager.stubGet("/api/users", 200, responseBody);
WireMockManager.stubPost("/api/login", 201, responseBody);
WireMockManager.stopServer();
```
Used in Cucumber API scenarios to test error paths and edge cases that are hard to reproduce against a live environment.

---

## 13. Logging Stack — Deep Dive

| Component | Role |
|---|---|
| `SLF4J 2.0.5` | Logging API — all framework code uses this, never a concrete logger |
| `Logback 1.4.7` | SLF4J implementation — handles actual log output |
| `jul-to-slf4j 2.0.5` | Bridges `java.util.logging` (used by Selenium internals) into SLF4J |
| `logstash-logback-encoder 7.4` | Formats FILE appender output as structured JSON for ELK/Splunk |

### Appender configuration (logback.xml)
- **CONSOLE** — synchronous, plain text — immediate feedback during local runs
- **FILE** — JSON/Logstash format — structured logs for ingestion
- **ASYNC** — wraps FILE only (not CONSOLE); queue size 1024; non-blocking so slow disk I/O doesn't stall tests

### JUL bridge
`SLF4JBridgeHandler.install()` is called exactly once in `Reporter.startReport()`. It must not be called from a static initializer — in parallel fork mode each fork calls `startReport()` once, which is correct.

---

## 14. Database Layer

Three-layer abstraction: interface → abstract base → concrete implementation.

```
DatabaseConnection (interface)
    └── AbstractDatabaseConnection (abstract)
            └── MySQLConnection (concrete)
```

**AbstractDatabaseConnection** uses `CachedRowSet` to disconnect JDBC resources immediately after query execution — the `Statement` and `ResultSet` are closed inside a try-with-resources block, and the populated `CachedRowSet` is returned to the caller. This means callers never hold open JDBC connections.

**DatabaseConnectionFactory** creates connections by type (MySQL, extensible to others).

**DBManager** is a facade that manages multiple named connections keyed by config class — a test can call `DBManager.getConnection("leaftaps")` without knowing the underlying JDBC details.

---

## 15. Projects Consuming the Framework

| Project | Domain | Status |
|---|---|---|
| LeafTaps | CRM / SFA application | Active — full test suite |
| AlfaDOCK | Document management | Active — full test suite |
| Alfa3DViewer | 3D viewer application | Page objects built, test cases pending |

Each project has its own testng.xml pointing to its `configClass`. The framework JAR is the same binary for all three.

---

## 16. Common Interview Questions — Prepared Answers

**Q: Why did you build a custom framework instead of using an existing one?**  
A: Existing frameworks like Serenity or Gauge solve specific problems but don't cover the full spectrum — UI, API, performance, security, and observability in one coherent package. Building it ourselves meant we could enforce consistent patterns (ThreadLocal safety, drain pattern, correlation IDs) across all teams without fighting framework opinions.

**Q: How do you handle parallel test execution?**  
A: Three layers. First, ThreadLocal binds each thread to its own WebDriver — no sharing. Second, DriverPoolManager pre-warms a pool so threads don't wait for browser startup. Third, all shared state (config cache, report nodes, test events) uses ConcurrentHashMap or atomic operations. The drain pattern in ResourceUsageAccumulator ensures metrics don't bleed between tests on the same thread.

**Q: How do you manage test data?**  
A: DataLibrary reads Excel and CSV files with a parse cache — the file is read once per suite, not once per test. For dynamic data, FakerDataFactory generates realistic random values using JavaFaker with a ThreadLocal instance so parallel tests don't share Faker state. Sensitive data like credentials is stored AES-256/GCM encrypted and decrypted at runtime by EncryptionUtils.

**Q: How do you detect flaky tests?**  
A: FlakyTestTracker uses a sliding window over the last N runs (configurable). Each test gets a score from 0.0 to 1.0 — STABLE, FLAKY, or QUARANTINED. This is more accurate than a simple retry counter because it captures intermittent failures over time, not just within a single run. Quarantined tests are excluded from the blocking CI gate.

**Q: How does the configuration system support multiple projects?**  
A: The `configClass` parameter in testng.xml names a class that implements `ProjectAppConfiguration`. `ProjectDirector` resolves that class name to the matching `.properties` file. The framework never hard-codes a project name. Adding a new project means creating one config class and one properties file — no framework code changes.

**Q: What design patterns did you apply and why?**  
A: The most interesting ones are the Enum Factory (BrowserType — each enum constant creates its own driver, open/closed principle), the Object Pool (DriverPoolManager with IDLE/IN_USE/POISONED state machine), and the Observer/Drain pattern (ResourceUsageAccumulator — drained not copied to prevent stale data leaking across tests on the same thread). Every pattern solves a real problem, not applied for its own sake.

**Q: How do you integrate security testing without slowing down CI?**  
A: SecurityTestBase tests run in a separate suite. The CI workflow (`testng-ci.xml`) is browser-free and runs in under 2 minutes. Security scans run on-demand or nightly. ZAP is called via its REST API using RestAssured — no heavy ZAP Java client dependency, which would add ~50MB and transitive conflicts.

**Q: How does retry work and how do you avoid retrying genuine bugs?**  
A: RetryEngine implements `IRetryAnalyzer`. It tracks failures by invocation key (test name + param hash for data-driven tests). If the same source line fails twice in a row, it stops retrying — that's a deterministic failure, not a flake. TestAnnotationTransformer wires RetryEngine to every `@Test` automatically via `META-INF/services`, so no per-test annotation is needed.

**Q: How do you handle authentication in API tests?**  
A: RequestAuthentication manages BASIC, BEARER, and OAuth. For OAuth, it caches the token per instance (not static — thread-safe by design) and checks expiry with `Instant.now().isBefore(expiryInstant)` before each request. If expired, `refreshOAuthToken()` is called automatically. Token validity is 3600 seconds by default.

**Q: How do you ensure test isolation in Cucumber scenarios?**  
A: PicoContainer creates a fresh `CucumberScenarioContext` per scenario and injects it into all step classes that need it. No static fields anywhere in step definitions. ScenarioHooks manages driver lifecycle via a Method handle bridge to DriverPoolManager — this avoids circular injection in the PicoContainer graph.

**Q: What does your logging setup look like and why?**  
A: SLF4J as the API throughout, Logback as the implementation. Three appenders: synchronous CONSOLE for immediate local feedback, JSON FILE (logstash-logback-encoder) for ELK/Splunk ingestion, and an ASYNC wrapper around FILE only so slow disk I/O never stalls test threads. A JUL bridge routes Selenium's internal java.util.logging into the same pipeline. Every log line is prefixed with `[T-{threadId}]` via LogUtils so parallel output is traceable.

---

## 17. Numbers to Remember

| Metric | Value |
|---|---|
| Main source files | ~40 classes across 10 packages |
| Test source files | ~15 classes (step defs, runners, hooks) |
| Dependencies | 25+ production, 5 test-scoped |
| TestEvent fields | 24 fields per event |
| Logging appenders | 3 (CONSOLE sync, FILE JSON, ASYNC wrapper) |
| Projects consuming JAR | 3 (LeafTaps, AlfaDOCK, Alfa3DViewer) |
| Java version | 17 (records, sealed classes available) |
| Selenium version | 4.33.0 (BiDi protocol support) |
