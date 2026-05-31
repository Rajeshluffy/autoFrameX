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
- [CI/CD](#cicd)
- [Code Quality](#code-quality)

---

## Features

- **WebDriver Object Pool** — pre-warmed driver pool with configurable size, idle timeout, and per-driver reuse cap; eliminates cold-start latency in parallel runs
- **Thread-safe parallel execution** — `ThreadLocal` driver binding; safe for `parallel="methods"` and `parallel="classes"`
- **Auto-retry** — `RetryEngine` + `TestAnnotationTransformer` wires configurable retries onto every `@Test` without per-method annotation
- **Multi-browser support** — Chrome, Firefox, Edge, and Selenium Grid via `BrowserFactory`; browser selected at runtime
- **Cucumber + PicoContainer DI** — BDD support with constructor-injected `ScenarioContext`; no static shared state between step classes
- **REST Assured API layer** — typed request/response base classes with JSON Schema validation and a custom listener for Extent reporting
- **Performance testing** — `PerformanceTestBase` with `measureApi`, `measurePageLoad`, and `runLoadTest` (P95/P99 stats)
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
| Data | Apache POI, JavaFaker, SnakeYAML | 5.2.4 / 1.0.2 / 2.6 |
| JSON | Jackson Databind, Gson | 2.18.0 / 2.11.0 |
| Logging | SLF4J + Logback + Logstash encoder | 2.0.5 / 1.4.7 / 7.4 |
| Database | MySQL Connector/J | 9.6.0 |
| Security | OWASP ZAP (via ZapSecurityUtils) | — |
| Video | Monte Screen Recorder | 0.7.7.0 |
| Build | Maven | 3.x |
| Java | Java | 16 |

---

## Project Structure

```
autoFrameX/
├── src/main/java/
│   ├── com/api/                          # REST Assured API layer
│   │   ├── design/                       # ApiClient, ResponseAPI interfaces
│   │   └── rest/assured/base/            # Request/response base classes, listener
│   ├── com/framework/
│   │   ├── config/data/                  # Config system (Owner-based, multi-project)
│   │   ├── cucumber/api/base/            # CucumberProjectBase, ScenarioContext
│   │   ├── observability/                # TestEventCollector, FlakyTestTracker, FailureCategorizer
│   │   ├── performance/                  # PerformanceTestBase, PagePerformanceUtils, ApiPerformanceUtils
│   │   ├── security/                     # SecurityTestBase, ZapSecurityUtils, InputSanitizer
│   │   ├── selenium/
│   │   │   ├── api/base/                 # SeleniumBase, BasePage
│   │   │   ├── api/design/               # Browser, Element, Locators interfaces
│   │   │   └── exception/               # ElementNotFoundException
│   │   ├── testng/api/base/              # ProjectSpecificMethods, RetryEngine, TestMetadata
│   │   └── utils/                        # Reporter, DataLibrary, WaitUtils, EncryptionUtils, ...
│   └── design/patterns/
│       ├── database/                     # AbstractDatabaseConnection, MySQLConnection, DBManager
│       ├── factory/browser/              # BrowserFactory, BrowserType, Chrome/Firefox/Edge/Remote
│       └── object/pool/                  # DriverPoolManager, WebDriverPoolFactory
├── src/main/resources/
│   ├── frameworkConfig.properties        # All tunable framework defaults
│   └── logback.xml                       # Logback config (console + JSON file appender)
├── src/test/java/
│   ├── runners/                          # CucumberRunner
│   └── step/defs/                        # UI, API, combined step definitions + hooks
├── src/test/resources/features/          # Cucumber .feature files
├── testng.xml                            # Master suite (unit + cucumber + perf + security)
├── testng-ci.xml                         # CI-optimised suite (headless, parallel)
├── Jenkinsfile                           # Declarative pipeline
├── Dockerfile / docker-compose.yml       # Containerised execution
└── pom.xml
```

---

## Getting Started

**Prerequisites**

- Java 16+
- Maven 3.6+
- Chrome / Firefox / Edge installed (or a running Selenium Grid)

**Clone and build**

```bash
git clone https://github.com/Rajeshluffy/autoFrameX.git
cd autoFrameX
mvn clean compile
```

---

## Configuration

All defaults live in `src/main/resources/frameworkConfig.properties`. Override any value from the command line or `testng.xml` parameters.

```properties
# Browser
autoFrameX.browser.name=chrome
autoFrameX.browser.isheadless=false

# Waits (seconds)
autoFrameX.implicit.wait.time=10
autoFrameX.explicit.wait.time=20
autoFrameX.page.load.timeout=30

# Retry
autoFrameX.test.retry.max.limit=4
autoFrameX.element.max.retry.attempts=3

# Driver pool
autoFrameX.pool.max.size=5
autoFrameX.pool.min.size=2
autoFrameX.pool.borrow.timeout.seconds=30
autoFrameX.pool.max.reuse.count=75

# Selenium Grid
autoFrameX.grid.enabled=false
autoFrameX.grid.hub.url=http://localhost:4444/wd/hub
```

**Multi-project config routing**

Each `testng.xml` passes a `configClass` parameter pointing to a `ProjectAppConfiguration` implementation. The `ProjectDirector` reads that class and loads the matching properties file — so multiple projects can share the same framework JAR with isolated configs.

```xml
<parameter name="configClass" value="com.myapp.config.data.MyAppConfiguration"/>
```

---

## Running Tests

**Default suite**

```bash
mvn test
```

**Override browser / environment / headless at runtime**

```bash
mvn test -Dbrowser=firefox -Denv=staging -Dheadless=true
```

**Run a specific suite**

```bash
mvn test -Dtestng.suite.file=testng-ci.xml
```

**Parallel execution**

```bash
mvn test -Dtestng.suite.file=testng-ci.xml -DthreadCount=4
```

**Run via Docker**

```bash
docker-compose up --build
```

---

## Framework Modules

### Selenium Layer

`SeleniumBase` implements `Browser` and `Element` interfaces and provides a robust wrapper around WebDriver — explicit waits on every interaction, stale element retry, screenshot on failure, and centralised Extent reporting. `BasePage` is the Page Object base class.

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

`CucumberProjectBase` wires Cucumber into the TestNG lifecycle. `CucumberScenarioContext` is a PicoContainer-injected shared state object — step definition classes receive it via constructor injection, eliminating static fields.

### REST Assured API Layer

`RestAssuredBase` provides typed GET/POST/PUT/DELETE helpers. `RestAssuredResponseBase` adds JSON Schema validation via `verifySchema()`. `RestAssuredListener` logs every request/response to the Extent report automatically.

### Performance Module

`PerformanceTestBase` extends `ProjectSpecificMethods` and skips WebDriver acquisition for API-only tests. Key helpers:

- `measureApi(supplier, slaMs)` — times a single API call, warns on SLA breach
- `measurePageLoad(url, slaMs)` — reads Navigation Timing API from a live page
- `runLoadTest(supplier, threads, iterations)` — concurrent load test returning P95/P99 stats

### Security Module

`SecurityTestBase` integrates OWASP ZAP:

- `@BeforeSuite` verifies ZAP is reachable
- `@AfterSuite` generates an HTML report into `reports/zap/`
- Helpers for passive scan, active scan, alert assertions, and secret masking via `SecretDetector`

### Observability

- `TestEventCollector` — singleton async NDJSON writer (`logs/test-events.json`), ELK-ready
- `FlakyTestTracker` — tracks pass/fail history per test to surface flaky tests
- `FailureCategorizer` — classifies failures by type (timeout, assertion, infra, etc.)
- `CorrelationContext` — propagates a correlation ID across threads for distributed tracing

### Utilities

| Class | Purpose |
|---|---|
| `Reporter` | Thread-safe Extent report wrapper |
| `WaitUtils` | Fluent/explicit wait helpers |
| `DataLibrary` | Excel + YAML test data reader |
| `EncryptionUtils` | AES-256 encrypt/decrypt for credentials |
| `ValidationUtils` | Common assertion helpers |
| `ScreenshotUtils` | Full-page and element screenshots |
| `VideoRecorder` | Monte-based AVI screen recording |
| `LogUtils` | SLF4J structured logging helpers |
| `RetryUtils` | Programmatic retry with backoff |

---

## CI/CD

The `Jenkinsfile` defines a declarative pipeline with parameterised browser, environment, headless flag, suite file, and thread count. Stages: Checkout → Build → Test → Reports → (optional) SonarQube.

GitHub Actions workflows are in `.github/workflows/`:

| Workflow | Trigger |
|---|---|
| `ci.yml` | Push / PR to main |
| `regression.yml` | Scheduled nightly |
| `performance.yml` | Manual dispatch |
| `security.yml` | Manual dispatch |
| `sonar.yml` | Push to main |

---

## Code Quality

SonarQube analysis and JaCoCo coverage are wired into the Maven build:

```bash
mvn sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.login=<token>
```

Coverage reports are generated automatically during `mvn test` via the JaCoCo plugin and consumed by SonarQube.
