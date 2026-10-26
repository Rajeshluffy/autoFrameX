# Changelog

All notable changes to autoFrameX. The project has not cut a numbered release yet: everything below is in
`0.0.1-SNAPSHOT`. Entries marked **behaviour change** can make a suite that used to pass fail (or behave
differently); read those before you upgrade. See [RELEASING.md](RELEASING.md) for how a release is cut.

## Unreleased

### Honest assertions: things that used to pass without checking

- **Behaviour change: an SLA is enforced.** `PerformanceTestBase.measureApi`, `ValidationUtils.assertResponseTime`,
  `PagePerformanceUtils` and the load-test statistics fail the test with an `AssertionError` when the SLA is
  breached, when the call returned an HTTP error status, when the measurement is negative or missing, or when a
  load test had no successful call. Before, a breach was a log line and an error response met any SLA.
  `-Dperformance.sla.enforce=false` restores log-only behaviour while you tune thresholds.
- **Behaviour change: configuration values.** `ConfigResolver` treats a blank value, or a `${...}` placeholder
  that nothing filled in, as *unset* and falls through to the next source (TestNG parameter, environment
  variable, `-D`, default). Booleans are strict: `true` or `false` (any case) only; anything else throws instead
  of silently meaning `false`. An unknown environment, an empty production URL, and a missing or empty data file
  (`DataLibrary`, the account file) throw with a message naming the key or file.
- **Behaviour change: `EncryptionUtils`** throws when no key is configured, instead of returning `null`.
- `SecretDetector` checks a configuration map's keys by name as well as its values, so `db.password=hunter2`
  (a short value no pattern matches) is reported. Placeholders (`${DB_PASSWORD}`, `<...>`, `ENC(...)`, `***`),
  blanks and booleans are not findings.
- `ElementInspectionActions.verifyPartialAttribute` returns whether it matched (it returned nothing and
  logged). `getBackgroundColor` reads `background-color` (it read `color`).
- `TestEventCollector`: one event that cannot be written no longer ends the writer thread (every later event
  used to be dropped with a misleading "queue full" warning).
- **Behaviour change (Cucumber):** a scenario fails when the application URL cannot be opened. It used to log a
  warning and run against a blank page. A blank URL still means "nothing to open".

### Selenium

- **Behaviour change: window switching waits.** `switchToWindow(index)` waits (short wait time) for the window
  to open instead of failing at once with "index out of bounds". `switchToWindowByTitle` / `ByUrl` try each
  window, return whether one matched, and when none does put the browser back on the window it started on.
- **Two-locator lookups poll both locators together** with the implicit wait off, for one implicit period in
  total. They used to wait the whole implicit period on the primary before trying the fallback (up to twice
  that when both missed). New helper `ImplicitWait.without(driver, action)`; explicit waits no longer stack on
  top of the implicit one.
- **File upload on Selenium Grid.** The Grid session gets a `LocalFileDetector`, so `sendKeys(path)` uploads the
  file from the test machine to the node. When `sendKeys` is refused on a remote session the step now fails
  with the reason instead of driving a `Robot` on the test runner.
- The video recorder no longer takes a frame while an alert is open (a screenshot dismisses it under W3C, which
  made the test's own `acceptAlert()` fail).
- `BrowserFactory` quits a browser it launched if applying the timeouts then fails (it was left running).
- Pool health checks run on shared daemon threads (each used to leave a non-daemon thread behind when the
  driver hung).
- Pool: `maxReuseCount` is honoured (the counter restarted at 1 on every reuse); a second browser type is
  served in the same run; headless follows the suite parameter and the project setting, not only `-Dheadless`;
  the temporary screenshot file is deleted after it is copied.

### Build, CI and quality

- CI runs every module's tests. The two workflows that selected a TestNG group no test declared (`security.yml`,
  `performance.yml`) are removed.
- Quality gates fail the build: JaCoCo line-coverage floor per module, PMD priority 1 and 2, SpotBugs High.
- **Build now needs Maven 3.8.8+ and JDK 17+** (enforced), compiles with `--release 17` so a newer JDK cannot
  let a newer API into a Java 17 build, and every plugin version is pinned (enforced: an unpinned plugin fails
  the build).
- Dependency upgrades for known vulnerabilities, SnakeYAML override fixed, Dependabot added.
- `gitleaks` scans every push and pull request.

### Environment, layering and code hygiene

- **Behaviour change: a production run started with `env` is now treated as production by the browser safety policy.** The
  environment is chosen as `environment` or its alias `env`, and every workflow, suite XML and the Dockerfile use `env`, but the
  policy read only `environment`: with `-Denv=prod` (or an `env` suite parameter) it still saw "dev" and accepted invalid TLS
  certificates and switched off Chrome's download protection in production. One implementation now
  (`ConfigResolver.resolveEnvironment` / `isProduction`), used by the project configuration, the browser policy and the ZAP guard.
- **Layering:** `design.patterns..` (browser factories and the driver pool) read the project configuration directly in three
  classes. `BrowserSecurityPolicy` and `RemoteGridBrowser` no longer do (the policy gets a parameter source from
  `DriverPoolManager`, the legacy `RemoteGridBrowser(String)` constructor reads `gridHubUrl` / `GRID_HUB_URL`, else the default
  hub), and a new ArchUnit rule fails the build if any class there other than the `DriverPoolManager` adapter depends on
  `com.framework.config.data` (`ConfigResolver`, a stateless utility, excepted).
- **PMD gate raised from priority 2 to 4.** The 49 findings of priority 3 and 4 are fixed: unused imports, fields and parameters
  (including five write-only fields in `RequestSpecBuilder`; its `setOauthBasePath` stored its argument in a field nothing read and
  is now a documented, deprecated no-op), redundant `public` on interface methods, empty catch blocks, a stray semicolon.

### Dependency and plugin updates

Taken from the open Dependabot pull requests, each batch built with the whole reactor and the three real-Chrome smoke
suites before the next. Some needed more than the version number:

- **Cucumber 7.21.1 to 8.0.4 needs a JSON library and brings none.** Dependabot's own branch failed because the runner's JSON
  report could not be written ("Cucumber needs a JSON library to write reports"). It only passed here by accident, through
  Jackson 3 arriving transitively from `logstash-logback-encoder` 9. `autoframex-cucumber` now declares
  `tools.jackson.core:jackson-databind` (3.2.3, the version Cucumber is built against) itself. Recreated both ways: without the
  declaration and with Logstash 8.1 the smoke suite fails with that message; with it, it passes. Report files are valid.
- **Surefire stays below 3.6.** From 3.6.0 Surefire runs TestNG on the JUnit Platform and rejects `suiteXmlFiles`, and every
  module runs from a suite XML. Pinned to 3.5.4 (from 3.0.0-M7); Dependabot is told to ignore 3.6 and later.
- **SpotBugs plugin stays at 4.10.3.0.** 4.10.4.0 and later need Maven 3.8.9; this build supports 3.8.8. Dependabot is told to ignore them.
- Updated: Selenium 4.33.0 to 4.51.0, REST Assured 5.5.0 to 6.3.0, Apache POI 5.4.1 to 6.0.0, Logback 1.5.18 to 1.6.5, Logstash
  encoder 8.1 to 9.0, Jackson 2.18.0 to 2.22.4, SnakeYAML 2.6 to 2.7, commons-io 2.19.0 to 2.22.0, WireMock 3.9.1 to 3.13.2;
  the Maven plugins (compiler 3.16.0, enforcer 3.6.3, JaCoCo 0.8.15, PMD 3.28.0, Checkstyle 3.6.0, clean/resources/jar/install/deploy/site);
  and the GitHub Actions (checkout 7, setup-java 6, upload-artifact 7, setup-chrome 2, gitleaks-action 3).
- Not verified: OWASP Dependency-Check 10.0.4 to 13.0.0 (it is not part of the build and needs the NVD feed; run
  `mvn dependency-check:check` once before relying on the weekly workflow).

### CI

- `sonar.yml` is skipped, with a notice and a job summary, when the `SONAR_TOKEN` and `SONAR_HOST_URL` secrets are not both
  set (previously it ran the whole build and then failed on every push, and on every fork's pull request, because the
  analysis step had no token). Add both secrets to enable the analysis.

### Security and secrets

- Log lines, exception messages and test events mask credentials (JSON, `key=value`, `Authorization` headers);
  the masking regex no longer takes quadratic time on long lines (20,000 characters: 16 s down to 4 ms).
- JMeter result files are untracked and ignored; credential defaults are blank. The credentials that were
  committed earlier are still in git history: they must be rotated (see README, *Secrets*).

### Cucumber

- **Behaviour change: the lifecycle hooks moved to `CucumberHooks`.** `CucumberProjectBase` defined the hooks
  itself, and Cucumber-JVM refuses to load a step class that extends a class with hooks, so the documented
  `class LoginSteps extends CucumberProjectBase` could not run at all. Step classes still extend
  `CucumberProjectBase`; add `com.framework.cucumber.api.base` to the runner's glue so Cucumber finds
  `CucumberHooks`. Without that entry no browser is acquired.
- The hooks now initialize the driver pool (a plain Cucumber run has no `@BeforeTest` to do it), and the pool
  accepts a null `Method` (a scenario has none): `setupDriver(null, ...)` used to throw
  `NullPointerException`. New `DriverPoolManager.isPoolInitialized()`.
- Scenarios tagged `@api` get no browser: both hooks carry the tag expression `not @api`. The example
  `ScenarioHooks` (video and failure evidence) now sits on top of `CucumberHooks` (order 200) instead of
  acquiring a second browser, the example step classes extend `CucumberProjectBase`, and its contexts extend
  `CucumberScenarioContext`.
- CI runs a Cucumber lifecycle smoke (`autoframex-cucumber/testng-smoke.xml`, `CucumberSmokeRunner`): one
  scenario with a real headless Chrome and a `data:` page, no network, with `-DfailIfNoTests=true` so a runner
  that selects no scenario fails instead of passing with zero tests. A runner must not extend another runner:
  Cucumber merges the options of superclasses.

### Security

- **`SecurityTestBase` could not run.** Any subclass made TestNG stop the whole suite with "The following methods
  have cyclic dependencies": `generateZapReport` declared `dependsOnMethods = "endReport"`, while TestNG already runs a
  subclass's `@AfterSuite` before its superclass's. No test in the repository extended the class, so nobody saw it.
  The explicit dependency is gone, and `SecurityTestBaseLifecycleTest` is the first subclass, run inside the real TestNG
  lifecycle against a fake ZAP.
- A security suite no longer pre-warms two real Chrome browsers it never uses (the inherited `@BeforeTest`).
- **Reporter:** a test class with neither `@TestMetadata` nor `setValues()` got a default name but a `null` author and
  category, and `startTestCase()` threw `NullPointerException` in `@BeforeClass`, skipping every method of the class.
  Author and category now default to `unknown` and `uncategorized`.
- ZAP calls carry an API key when `zap.api.key` or `ZAP_API_KEY` is set (header, not URL). The ZAP check at suite start
  uses the same timeouts and error handling as every other call. New `ZapSecurityUtils.verifyReachable`.
- **Behaviour change: `InputSanitizer`.** It had no test and was wrong both ways: ordinary text containing "select",
  "update", "create" or "script" was reported as SQL injection, while payloads like `<details open ontoggle=...>`,
  `' OR 'a'='a` or `data:text/html` passed. Detection is now statement-shaped, so a project that relied on a
  bare keyword being flagged will see fewer reports and more real ones. `sanitize` escapes CR, LF and the Unicode line
  separators.
- `SecretDetector`: the connection-string patterns took 12 s on 50,000 characters of `jdbc:jdbc:...` (every start
  position scanned to the end); lengths are now bounded. A repeated group in the new `InputSanitizer` pattern
  overflowed the stack on 30,000 columns; it is bounded too, and both have hostile-input tests.

### Performance

- **`PerformanceTestBase` could not measure a page.** It skips browser acquisition for API tests, and gave a page test no
  way to get one: `measurePageLoad` failed with "No WebDriver available. Ensure @BeforeMethod executed", which it never
  would. New `openBrowser(url)` (starts the pool on first use, returns the browser to it when the test ends);
  `measurePageLoad` and `getPageTimingMetrics` say to call it when there is no browser.
- **Behaviour change:** an API performance suite no longer pre-warms two real Chrome browsers it never uses (the inherited
  `@BeforeTest`), so it no longer needs Chrome installed. A page test that relied on that must call `openBrowser`.
- **Navigation Timing reported about -1.8 trillion while a page was still loading** (`0 - navigationStart`), against a
  Javadoc that promised 0, in `getPageLoadTimeMs`, `getFullTimingMetrics` (which the automatic page-load report line also
  uses) and `getDomContentLoadedMs` (which carried its own unguarded copy). A timing not reached yet is now 0, never
  negative. Checked in real Chrome (`PageTimingSupportBrowserTest`).
- A load test keeps up to five distinct failure reasons, so "100% failed" says why (`LoadTestResult.getFailureSamples()`).
- CI runs a performance browser smoke (`autoframex-performance/testng-smoke.xml`), with `-DfailIfNoTests=true`.

### Database

- **Parameterised statements.** `executeQuery(sql, params...)` and `executeUpdate(sql, params...)` (on `DatabaseConnection`
  and `DBManager`) bind values with prepared statements. The module took only raw SQL text, so a value with a quote in
  it (`O'Brien`) broke the statement and a value from outside could change it. A parameter count that does not match the
  placeholders fails.
- **A statement that never answers no longer hangs the test**: it is cancelled after `db.query.timeout.seconds`
  (default 60; `0` disables; a non-numeric value fails loudly). MySQL connections get a 10 s connect timeout (the
  driver's own default is none) unless the URL sets `connectTimeout`.
- **Behaviour change:** `connect(url, ...)` on a connection that is already open to a *different* URL throws instead of
  silently answering with the first connection (the caller would have run statements against the wrong database).
- Every query paid two extra `isValid` round trips (the staleness check ran in `DBManager.getConnection` and again inside
  the connection); the execute paths now check locally.
- A password in a JDBC URL (`?password=...`, `user:password@host`) is shown as `***` in logs and in the
  "Could not connect" message (`JdbcUrls.redact`).
- The JDBC layer is now tested against an in-memory H2 database (new test-scope dependency, 2.3.232); before, only
  `DBManager` was tested, with fake connections.

### TestNG base class (`ProjectSpecificMethods`) and the API module

- **`ApiTestBase` (new, `autoframex-api`).** An API-only project has no Selenium and so no `ProjectSpecificMethods`;
  it had to subclass the abstract `Reporter` itself, implement its screenshot method and remember to write the cleanup
  hook (nothing said so). `ApiTestBase` provides the report and runs `ApiCleanup` after every test
  (`ApiTestBaseLifecycleTest` is the first API test to run inside the lifecycle with only core and api on the classpath).
- **Video recording can be switched off:** `-Dvideo.enabled=false` (env `VIDEO_ENABLED`, suite parameter
  `video.enabled`). It is on by default, as before. Every UI test used to take a screenshot twice a second whether or not
  anything would be kept (only a failed test keeps its video).
- **The data provider names an oversubscribed pool.** `fetchData` is `parallel = true`, TestNG runs it on 10 threads, the
  pool holds 5 browsers and a thread waits only the borrow timeout (30 s) for one, so with long UI tests rows 6 to 10
  failed with "Cannot acquire driver" and nothing said why. The run now logs a warning with both numbers and the setting
  to change (`data-provider-thread-count`).

### API module

- Shared token cache, `ApiRequest`, typed responses, `ApiAssertions`, safe-call retries, cURL repro, cleanup
  registry, unique data, `ApiAwait`, `ApiConfig`, and an API-only Jenkins pipeline (`Jenkinsfile.api`). See
  `autoframex-api/README.md`.

### Documentation

- `CONTRIBUTING.md` moved to the repository root (it lived in the git-ignored `docs/` folder) and no longer
  links to documents that do not exist. New `CHANGELOG.md` and `RELEASING.md`. README brought up to date.
