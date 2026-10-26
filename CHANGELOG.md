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

### API module

- Shared token cache, `ApiRequest`, typed responses, `ApiAssertions`, safe-call retries, cURL repro, cleanup
  registry, unique data, `ApiAwait`, `ApiConfig`, and an API-only Jenkins pipeline (`Jenkinsfile.api`). See
  `autoframex-api/README.md`.

### Documentation

- `CONTRIBUTING.md` moved to the repository root (it lived in the git-ignored `docs/` folder) and no longer
  links to documents that do not exist. New `CHANGELOG.md` and `RELEASING.md`. README brought up to date.
