# autoFrameX — Review Status

First reviewed 2026-10-13, re-reviewed since, and brought up to date with the hardening work recorded in
[CHANGELOG.md](CHANGELOG.md). This file says what is fixed, how it was checked, and what is still open. It
replaces the first review's findings list, which had gone stale (most of it was fixed).

## 1. Build status

`mvn -B clean install` on the full reactor: **BUILD SUCCESS**, every module's own suite and every gate.

| Module | Tests | Line coverage | Floor |
|---|---|---|---|
| `autoframex-core` | 121 | 70% | 67% |
| `autoframex-selenium` | 243 | 43% | 40% |
| `autoframex-api` | 118 | 91% | 88% |
| `autoframex-database` | 10 | 47% | 43% |
| `autoframex-cucumber` | 5 | 30% | 27% |
| `autoframex-performance` | 14 | 75% | 70% |
| `autoframex-security` | 23 | 64% | 60% |
| `autoframex-testkit` | 2 | n/a | n/a |

Total 536 tests, 0 failures, 0 skipped. CI (`ci.yml`) runs the same build for every module.

What the numbers do **not** say: no test starts a real browser. Selenium behaviour is tested against
session-less fake drivers (a `RemoteWebDriver` subclass or a `java.lang.reflect.Proxy`), which proves the
framework's own logic (what it waits for, what it throws, what it cleans up) but not how Chrome, Firefox or a
Grid node responds. See §4.

## 2. What is good

- The 8-module split has no dependency cycles, and an ArchUnit rule guards it.
- One config resolver (`ConfigResolver`): TestNG parameter, then environment variable, then `-D`, then default;
  blank and unfilled `${...}` values count as unset; booleans are strict.
- Driver handling is context-aware and thread-safe; failed drivers are poisoned, never re-pooled; a browser is
  quit if its setup fails; the reuse cap and a second browser type work.
- The quality gates fail the build (coverage floors, PMD 1-2, SpotBugs High), the Maven and JDK versions are
  enforced, and every plugin version is pinned.
- Credentials are masked in logs, exceptions and the event stream; gitleaks scans every push.

## 3. Fixed since the first review

| Area | What was wrong | Fix and check |
|---|---|---|
| Checks that passed without checking | An SLA breach only logged; an error response met any SLA; missing data files, unknown environments and empty URLs were tolerated; `getBackgroundColor` read `color`; `verifyPartialAttribute` returned nothing; a Cucumber scenario ran on a blank page when the URL failed | Each now fails loudly; each has a test that fails without the fix (`PerformanceAssertionsAreHonestTest`, `ConfigResolverTest`, `ElementInspectionHonestyTest`, `CucumberProjectBaseTest`, ...) |
| Selenium waits | Window switching failed before the popup opened and left the browser on the wrong window; a fallback locator waited behind the primary's full implicit wait; implicit and explicit waits stacked | `WindowSwitchingTest`, `LocatorActionsTwoLocatorTest`, `ImplicitWaitTest` |
| Pool and drivers | `maxReuseCount` never took effect; a second browser type was not served; headless was ignored unless `-Dheadless`; a temp screenshot leaked per video frame; a browser leaked when timeouts failed; hung health checks left non-daemon threads | `PoolReuseCapTest`, `PoolSecondBrowserTest`, `BrowserHeadlessConfigTest`, `ScreenshotTempFileTest`, `DriverCreationCleanupTest`, `PoolHealthCheckThreadsTest` |
| Grid | File upload sent the local path as text; a refused upload drove a `Robot` on the test runner | `LocalFileDetector` on Grid sessions; the fallback fails on a remote session (`FileUploadRemoteSessionTest`, `DriverCreationCleanupTest`) |
| Video | A frame taken while an alert was open dismissed it | Frames are skipped while an alert is open (`VideoRecorderAlertTest`, red without the guard) |
| Secrets | Quadratic masking regex (20,000 characters: 16 s); credentials in JMeter results; credential defaults; `SecretDetector` missed `dbPassword`-style keys | Linear regexes; results untracked; blank defaults; gitleaks; detector checks keys by name |
| CI | Two workflows selected a group no test declared and went green; only some modules were tested; gates were reports | `ci.yml` builds everything; gates fail the build |
| Dependencies | Known-vulnerable versions; broken SnakeYAML override | Upgraded; Dependabot added |
| Earlier medium findings | `ZapSecurityUtils` false green, double auth scheme in `RequestSpecBuilder`, shared `DBManager` connection, `closeAfterEach` no-op, unconditional `--no-sandbox` | Fixed 2026-10-15 (see git history) |
| `main` diverged from `origin/main` | 11 ahead, 12 behind with identical trees | Resolved: `main` is now only ahead of `origin/main` (9 local commits not yet pushed) |

## 4. Still open

### Needs your action

- **Rotate the credentials that are in git history** (README, *Secrets*): the AlfaDOCK, Leaftaps and Leaftaps
  database credentials in `alfaDOCKConfig.properties` / `leafTapConfig.properties`, and the ERP key that the
  JMeter results recorded. Rotation is what makes them safe; rewriting history only stops them being
  downloadable and needs a force push of every branch.
- **Push the local commits** when you have looked at them. Nothing has been pushed from this review's work.
- **Decide runtime config for AlfaDOCK / Leaftaps**: with the credential files out of the Docker image, those
  suites need them injected at run time (for example a Kubernetes Secret mounted into the Job's pod). Not built.

### Not verified

- No real browser or Selenium Grid was started by these tests: Chrome's response to the headless switch, the
  `LocalFileDetector` upload against a real node, and the alert guard against a real `confirm()` are
  reasoned from the W3C behaviour and the fake-driver tests, not observed. A smoke run on a real browser and a
  Grid (the `testng-parallel-smoke.xml` suite) is the check to run before relying on them.
- The `secret-scan.yml` (gitleaks) workflow and `Jenkinsfile.api` with credentials have not run on a real
  runner. ZAP replies in the security tests are modelled on ZAP's documented API, not compared with a live ZAP.

### Known gaps

- Layering: `design.patterns.*` (the pool and browser factories) still imports `com.framework.config.data`
  directly, against its own package's stated intent. `ArchitectureRulesTest` guards the rest.
- Coverage is thinnest where it matters for browsers: `autoframex-selenium` 43%, `autoframex-cucumber` 30%.
  Raise a module's floor in its pom when its coverage rises.
- Checkstyle is report-only (its Google config does not match this code's style). PMD priority 3 to 5 do not
  fail the build; raise `pmd.gate.priority` to 3 once they are cleared (`-Dpmd.gate.priority=3` shows the list).
- `performance/` is large (a committed JMeter HTML report inflated the git pack); ignore
  `performance/**/results/` if it grows again.
- `ProjectSpecificMethods.faker()` returns `null` (dead API).
- `ci.yml` runs on every push to any branch and again on pull requests. `sonar.yml` fails on fork pull
  requests, which do not receive secrets.
- The `docs/` folder is deliberately git-ignored (it holds an ERP API spec and a generator script); the
  contributor guide that lived there is now the root `CONTRIBUTING.md`. The architecture and coding-standards
  documents that code comments used to point at do not exist; those comments now point at the README and
  `CONTRIBUTING.md`.
