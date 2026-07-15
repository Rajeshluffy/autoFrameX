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
| Video | WebDriver screenshots + FFmpeg (system binary) | — |
| Build | Maven | 3.x |
| Java | Java | 17 |

---

## Project Structure

As of TD-20, autoFrameX is an 8-module Maven reactor — each module owns a
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
└── .github/workflows/                      # ci / regression / performance / security / sonar / dependency-check
```

Suite XML files, test fixtures (`data/accounts.xlsx`), and Cucumber
`.feature` files each live inside the module whose classpath they need —
Surefire resolves a suite file relative to the invoking module's own basedir,
not the reactor root.

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
mvn test -pl autoframex-testkit -Dbrowser=firefox -Denv=staging -Dheadless=true
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

Split across `autoframex-core` (no Selenium dependency) and
`autoframex-selenium` (needs a live `WebDriver`) per TD-20:

| Class | Purpose | Module |
|---|---|---|
| `Reporter` | Thread-safe Extent report wrapper | core |
| `DataLibrary` | Excel + CSV/TSV test data reader with cache | core |
| `EncryptionUtils` | AES-256 encrypt/decrypt for credentials | core |
| `LogUtils` | SLF4J structured logging helpers | core |
| `RetryUtils` | Programmatic retry with backoff | core |
| `WaitUtils` | Fluent/explicit wait helpers | selenium |
| `ValidationUtils` | Common assertion helpers | selenium |
| `ScreenshotUtils` | Full-page and element screenshots | selenium |
| `ScreenshotStore` | Per-thread screenshot bytes for report embedding | selenium |
| `VideoRecorder` | Failure-only video capture — WebDriver screenshots assembled via FFmpeg | selenium |

---

## CI/CD

The `Jenkinsfile` defines a declarative pipeline with parameterised browser, environment, headless flag, module, suite file, and thread count. Stages: Checkout → Inject Configs → Build & Install → Test → Reports → (optional) SonarQube.

GitHub Actions workflows are in `.github/workflows/` — each `mvn` step now
installs the full reactor first, then targets the one module that owns the
suite it's running via `-pl` (see each file's own comments for why):

| Workflow | Trigger | Module targeted |
|---|---|---|
| `ci.yml` | Push / PR to any branch | `autoframex-selenium` |
| `regression.yml` | Manual dispatch | Chosen via the `module` input (default `autoframex-testkit`) |
| `performance.yml` | Manual dispatch | `autoframex-selenium` |
| `security.yml` | Manual dispatch | `autoframex-selenium` |
| `sonar.yml` | Push / PR to main | `autoframex-selenium` (test step); reactor root (Sonar analysis) |
| `dependency-check.yml` | Weekly + manual dispatch | Reactor root (scans every module) |

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
