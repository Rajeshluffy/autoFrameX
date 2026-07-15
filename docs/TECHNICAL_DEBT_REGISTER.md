# autoFrameX Technical Debt Register

Produced by the enterprise architecture review (2026-07-14) and updated after the
remediation pass (2026-07-15). See [ARCHITECTURE.md](ARCHITECTURE.md) for diagrams and
[ARCHITECTURE_REVIEW_ROADMAP.md](ARCHITECTURE_REVIEW_ROADMAP.md) for what's next.

**All 20 items now fixed or closed** (compiled + run against real suites, not just
unit-tested in isolation) across the 2026-07-15 passes. 1 closed as not applicable
(TD-19 — repo owner confirmed this is not going open-source). The register's entire
backlog is now resolved: ~~TD-07~~ (done) → ~~`BrowserType` registry rework~~ (done —
covered both the `RemoteGridBrowser` coupling and multi-custom-browser support) →
~~TD-20~~ (done — all 3 stages: prerequisite decoupling, reactor/module migration,
CI/Docker/Jenkins/docs updates).

**TD-20 Stage 3 (2026-07-15):** every `.github/workflows/*.yml` file, the
`Dockerfile`/`docker-compose.yml`, and the `Jenkinsfile` were updated for the
8-module reactor — each `mvn` invocation now installs the full reactor first
(`-DskipTests -Djacoco.skip=true`, so `-pl`-scoped downstream builds can resolve
upstream modules from the local repo) then targets the module that owns the
suite it runs via `-pl` (`autoframex-selenium` for `testng-ci.xml`,
`autoframex-testkit` for the aggregate `testng.xml`, or an operator-chosen
module via a new `module` workflow-dispatch input on `regression.yml`/the
Jenkinsfile). Artifact-upload paths were globbed (`**/target/surefire-reports/`
etc.) since results now land under each module's own `target/`, not the repo
root. `sonar.yml`/the Jenkinsfile's SonarQube stage still run at the reactor
root (no `-pl`) so Sonar analyzes every module's source, with a globbed
`xmlReportPaths` picking up whichever module(s) actually produced coverage.
`README.md`, `CONTRIBUTING.md` (including its already-stale
`BrowserRegistry.registerCustom`/`BrowserType.CUSTOM` reference from before the
`BrowserType` rework), `docs/DEVOPS_INTEGRATION_GUIDE.md`,
`docs/QUICK_START_PATTERNS.md`, and
`docs/CUCUMBER_DEPENDENCY_INJECTION_PATTERN.md` were all updated for the new
module layout and the `classpath:features` fix from Stage 2. This closes the
entire 20-item register.

**TD-20 Stage 2 (2026-07-15):** the repo is now a real 8-module Maven reactor —
`autoframex-core`, `-selenium`, `-api`, `-database` (new, missing from the original
sketch), `-cucumber`, `-performance`, `-security`, `-testkit` (new — hosts `testng.xml`,
the one suite spanning more than one module's classpath). `com.api.design` was
originally slated for `core` but turned out to directly import
`io.restassured.specification.RequestSpecification` — caught only when `core` first
failed to compile; moved to `autoframex-api` instead (a third-party-import gap the
framework-only import-graph research from planning didn't catch, same category as the
`com.framework.utils` Selenium-dependent classes found earlier). Suite XMLs that only
touch one module's classes moved with that module (`testng-ci.xml`,
`testng-ci-retry.xml`, `testng-parallel-smoke.xml`, `testng-data-provider.xml`,
`testng-targeted.xml` → `autoframex-selenium`); `testng.xml` (spans
selenium+cucumber+performance+security) moved to the new `autoframex-testkit`
aggregator, which needs `test-jar` dependencies on `core`/`selenium`/`cucumber` to see
their test classes by name (`ExtentBugTest`, `RetryTest`, `CucumberRunner`) — a real,
somewhat-advanced Maven mechanic this split required. `CucumberRunner`'s
`@CucumberOptions(features = "src/test/resources/features")` was a filesystem-relative
path that broke when invoked from a different module's working directory than the one
that compiled it; fixed to `"classpath:features"` (more portable regardless). All 5
real suites re-verified scoped to their new module homes, behaving identically to
pre-split. Running `testng.xml` end-to-end for the first time all session (via the new
testkit module) surfaced a genuine, previously-undiscovered Cucumber driver-pool-leak
bug — unrelated to the module split itself, flagged as a separate follow-up rather than
fixed here.

**Closed, not applicable:** TD-19 (OSS-readiness: LICENSE, reverse-DNS groupId, purge
client names from core Javadoc) — the repo owner confirmed (2026-07-15) this framework
is not being open-sourced, so none of that work is relevant. Revisit if that changes.

## Fixed

| # | Item | Category | Severity | Fix |
|---|---|---|---|---|
| TD-01 | Hardcoded fallback AES encryption key committed to source | Security | Critical | `EncryptionUtils.resolveKey()` now throws `IllegalStateException` instead of silently falling back. Confirmed unused elsewhere in the codebase at the time, so zero blast radius. |
| TD-02 | `continue-on-error: true` on every GitHub Actions test/security/performance step | CI/CD | Critical | Split the intentionally-always-failing `RetryTest` into its own `testng-ci-retry.xml`; removed `continue-on-error` everywhere except that one step. |
| TD-03 | JVM-singleton `ConfigManager`/`DriverPoolManager` blocked multi-app concurrency | Scalability | Critical | Converted both to a `contextId`-keyed registry, bound per-thread. `getInstance()` kept its exact signature — zero changes to ~33 existing call sites. Verified with a real two-`<test>` parallel suite (distinct identity hashes, independent pool stats). |
| TD-04 | Parallel execution unvalidated by CI — sample suites were empty templates | Parallel Execution | Critical | Added `ParallelExecutionSmokeTest` (8 methods, `parallel="methods" thread-count="5"`) and `testng-parallel-smoke.xml`, wired into `ci.yml`. Verified real driver reuse under contention (`Reused=true`), pool topped out at exactly `max=5`. |
| TD-05 | Java version mismatch — pom targeted 16, every runtime environment used 17 | Maven | Critical | `pom.xml` `java.version`/`sonar.java.source` bumped to 17. |
| TD-06 | Circular package dependency (`utils → testng.api.base → selenium.api.base → utils`) | Architecture | High | Moved `TestMetadata` and `AccountData` out of `testng.api.base` into `utils` (alongside their primary consumers `Reporter` and `DataLibrary`). Verified `utils` no longer imports anything from `testng.api.base`. |
| TD-08 | Robot/clipboard-based file upload — not thread-safe, not Grid-compatible | Selenium Design | High | `fileUpload()`/`fileUploadWithJs()` now try `WebElement.sendKeys(path)` first (fast, no OS dialog, Grid-safe); the Robot/clipboard path is now `synchronized` as a fallback for non-native upload widgets. Verified against a real `<input type="file">` page. |
| TD-09 | Unmasked credential logging — REST Assured DEBUG bypassed secret masking | Logging | High | Added `MaskingMessageConverter` (console) and `MaskingLogstashEncoder` (JSON file) so `EncryptionUtils.maskSensitiveValues()` applies automatically to every log line. `io.restassured` dropped from DEBUG to INFO. Verified both appenders mask `password=`/`Bearer` patterns. |
| TD-10 | Unused/misleading `jave-all-deps` dependency; ffmpeg missing from Docker; stale README | Dependency Mgmt | High | Removed the unused JAVE2 dependency (confirmed zero references; `VideoRecorder` actually shells out to system `ffmpeg`). Fixed README's stale "Monte Screen Recorder"/"Excel+YAML" claims. |
| TD-11 | Zero dependency vulnerability (SCA) scanning | Security | High | Added `org.owasp:dependency-check-maven`, **not** bound to a lifecycle phase (would make every `mvn verify` slow/flaky downloading the NVD feed) — instead a scheduled weekly + manual `dependency-check.yml` workflow, matching the existing `security.yml` pattern. |
| TD-12 | Closed `BrowserType` enum; inheritance-locked base classes | Plugin Architecture | High | **Fully fixed 2026-07-15.** Replaced the enum's role as the pool's Map/Set key with an open, `String`-keyed `BrowserRegistry` (`register(id, BrowserProvider)`/`resolve(id, config)`) — supports any number of independently-pooled custom browsers, not just one. `BrowserType`'s 6 pre-existing constants (`CHROME`/`FIREFOX`/`EDGE`/`GRID_CHROME`/`GRID_FIREFOX`/`GRID_EDGE`) and `BrowserFactory`/`WebDriverFactoryInterface`'s `BrowserType`-parameterized methods kept working unchanged (one-line bridge into the new `String`-keyed path); the old single-slot `CUSTOM`/`registerCustom` was removed (uncommitted/unreleased, safe to replace). Verified via all 5 real suites plus a new standalone test registering two independent custom browsers and confirming the pool tracks them separately. See below for the `RemoteGridBrowser` half of this fix. |
| TD-13 | No Checkstyle/PMD/SpotBugs; no `jacoco:check` coverage gate | Code Quality | High | Added all three static-analysis tools (report-only — `failOnViolation=false`, since the codebase was never checked before) plus a real `jacoco:check` at 20% line coverage (measured ~34% via the parallel smoke suite, set with real margin). Wired into `ci.yml`. |
| TD-14 | Dead code: 180 commented lines in `Reporter.java`; two fully-commented test classes | Maintainability | Medium | Deleted the commented legacy block and `SimpleTest.java`/`WebDriverPoolTest.java` (referenced a pre-refactor pool API that no longer exists). |
| TD-15 | Duplicated 4-tier config-resolution logic in two places | Design Patterns | Medium | Extracted `ConfigResolver`; `ProjectDirector` and `DriverPoolManager` both delegate to it now — and the shared version fixed a subtle bug (a malformed higher-priority tier no longer shadows a valid lower-priority one). |
| TD-16 | Onboarding suite XMLs shipped with empty, non-runnable `<classes>` blocks | Developer Experience | Medium | Generated a real `data/accounts.xlsx` fixture and wrote `DataProviderExampleTest`/`TargetedExampleTest`, wired into both XMLs. **This surfaced a real regression** from the TD-03 fix: `fetchData()`'s `@DataProvider` runs on a thread never bound to its config context, silently falling back to the wrong execution mode — fixed by re-binding context inside `fetchData()` itself. |
| TD-17 | Shared Chrome download directory collides under real parallel execution | Parallel Execution | Medium | Made the download directory per-thread (`downloads/<threadId>/`). Also fixed a real cross-platform bug found while here: a hardcoded `\\` path separator that silently broke on the Linux CI runners. |
| TD-18 | `RestAssuredBase` mutable field with no documented thread-safety contract | API Design | Medium | Deleted the field — it was write-only, never read anywhere. Every method is now purely stateless. |
| TD-07 | God classes: `SeleniumBase` (1,465 lines), `Reporter` (759 lines) | SOLID | High | **Fixed 2026-07-15.** Extracted `SeleniumBase`'s ~85 methods into 11 focused, single-responsibility action classes under the new `com.framework.selenium.api.actions` package (`ClickActions`, `TypeActions`, `WaitActions`, `AlertActions`, `ScreenshotActions`, `WindowFrameActions`, `NavigationActions`, `JsActions`, `FileUploadActions`, `LocatorActions`, `ElementInspectionActions`) — an "extract class + delegate" refactor, not an inheritance change. `SeleniumBase` keeps every existing public method signature (still `extends Reporter implements Browser, Element`) but each method body is now a one-line delegation, so `BasePage`/`ProjectSpecificMethods`/`CucumberProjectBase`/any external `extends SeleniumBase` consumer needed **zero changes**. Also extracted `ExtentReportManager` (folder/file/`ExtentReports`-instance management) out of `Reporter`, formalizing a pattern (`CucumberRunner`/`ScenarioHooks` already called `Reporter.initReportInfrastructure`/`Reporter.folderName` compositionally, with no inheritance relationship) that was already proven to work; `Reporter`'s TestNG lifecycle hooks (`@BeforeSuite`/`@BeforeTest`/`@BeforeClass`/`@BeforeMethod`/`@AfterMethod`/`@AfterSuite`) stay on `Reporter` itself since TestNG requires them on the actual extended class or a registered listener (proven precedent: `TestAnnotationTransformer`/`RetryEngine` via `META-INF/services/org.testng.ITestNGListener`). Verified via `mvn compile`, all 5 real suites (`testng-ci.xml`, `testng-ci-retry.xml`, `testng-parallel-smoke.xml`, `testng-data-provider.xml`, `testng-targeted.xml`), and a new standalone test (`StandaloneActionComposabilityTest`) proving `ClickActions` can be composed directly with a bare driver supplier + minimal `Reporter`, with no `SeleniumBase`/TestNG lifecycle involved at all. |
| TD-20 | No multi-module split — every team pulls all ~24 dependencies transitively | Scalability | Medium | **Fully fixed 2026-07-15**, in 3 verified stages (comparable in scope/risk to the TD-03 singleton fix, so treated as its own dedicated plan rather than folded into the other 17 changes). **Stage 1:** fixed the 2 circular-dependency blockers that would have made `core` depend on `selenium` (`Reporter`'s pool bind/init/shutdown calls moved down into `SeleniumBase`'s own `@BeforeTest`/`@AfterSuite` hooks; `FailureCategorizer` decoupled from `ElementNotFoundException` via a new `com.framework.exception.Categorized` marker interface, and from Selenium's own exception types via class-name matching instead of `instanceof`) — both guarded by a new ArchUnit rule (`coreMustNotDependOnSeleniumOrPool`). Also ran `mvn dependency:tree -Dverbose` against all 5 dependencies with zero direct code usage: `gson`/`snakeyaml`/`protobuf-java` turned out to be genuine version-override pins (confirmed by breaking the build when one was removed and checking the real transitive conflict each resolves) and `jspecify` matches what `selenium-api` already needs transitively at the same version — all 4 kept, now documented inline; only `jsoup` had zero transitive reference anywhere and was removed. Corrected this doc's and `ARCHITECTURE.md`'s previous claim that performance/security modules exist to isolate JMeter/ZAP-client dependencies — neither exists in the pom. **Stage 2:** the repo became a real 8-module Maven reactor — `autoframex-core`, `-selenium`, `-api`, `-database` (new, missing from the original sketch), `-cucumber`, `-performance`, `-security`, `-testkit` (new — hosts `testng.xml`, the one suite spanning more than one module's classpath); see the Stage 2 narrative above for the `com.api.design`/`CucumberRunner` fixes discovered mid-migration. All 5 real suites re-verified scoped to their new module homes. **Stage 3:** every `.github/workflows/*.yml`, the `Dockerfile`/`docker-compose.yml`, and the `Jenkinsfile` updated with the install-then-`-pl`-scoped-test pattern and globbed artifact paths; `README.md`/`CONTRIBUTING.md`/`docs/DEVOPS_INTEGRATION_GUIDE.md`/`docs/QUICK_START_PATTERNS.md`/`docs/CUCUMBER_DEPENDENCY_INJECTION_PATTERN.md` updated for the new module layout (see the Stage 3 narrative above for full detail). **Flagged separately, not fixed as part of TD-20** (unrelated pre-existing issues found along the way): a genuine Cucumber driver-pool-leak bug surfaced running `testng.xml` end-to-end for the first time; `performance.yml`/`security.yml`'s `-Dgroups=performance\|security` vs `testng-ci.xml` package-mismatch; `WireMockManager` dead code; 2 stale onboarding fixture files. |

Also related: the bidirectional `design.patterns.* ↔ com.framework.config.data` coupling
(`PoolConfig`, `DriverPoolManager`, `BrowserFactory`, `RemoteGridBrowser`, `DBManager` all
imported `ConfigManager`/`ProjectConfig` directly) — see
[ARCHITECTURE.md](ARCHITECTURE.md#high-level-architecture).

**Partially fixed (2026-07-15):** `PoolConfig.Builder`'s no-arg constructor pulled 5
defaults from `ConfigManager`, but its only caller (`DriverPoolManager.loadConfiguration()`)
always overrode every one of them explicitly — the coupling was dead. Replaced with
plain constants (mirroring `frameworkConfig.properties`' own defaults). Verified via the
parallel smoke suite: identical pool config (`max=5, min=1, ...`) before and after.

**Fixed (2026-07-15):** `BrowserFactory`'s 3 timeout lookups (`pageLoadTimeout()`,
`scriptTimeout()`, `implicitWait()`) were genuinely used, not dead — but
`createDriver(BrowserType, PoolConfig)` already received a `PoolConfig` parameter that
was silently ignored. Added `pageLoadTimeoutSeconds`/`scriptTimeoutSeconds`/
`implicitWaitSeconds` to `PoolConfig`, populated by `DriverPoolManager.loadConfiguration()`
(which already depends on `ConfigManager` — that dependency isn't part of this fix), and
wired `BrowserFactory.configureTimeouts()` to read from the passed-in `PoolConfig`
instead of calling `ConfigManager` itself. Zero interface signature changes needed.
Verified via the parallel smoke suite: all 8 methods pass, real page loads succeed
(proving timeouts weren't silently zeroed).

**Fixed (2026-07-15):** `RemoteGridBrowser`'s grid-hub-URL lookup
(`ConfigManager.getInstance().getConfig().getGridHubUrl()`, called from inside
`launchBrowser(Capabilities)`) was the same shape of problem as the timeout coupling
above — no live bug (TD-03's context registry already resolves it correctly even
across concurrent multi-app runs), but a layering violation: `design.patterns.factory.browser`
reaching into `com.framework.config.data` directly. Fixed via the same `BrowserRegistry`
rework: `RemoteGridBrowser` gained a second constructor (`RemoteGridBrowser(String
browserName, String gridHubUrl)`) that takes the URL as data instead of reading
`ConfigManager`; `BrowserRegistry`'s `GRID_*` providers construct it this way, reading
the URL from `PoolConfig.getGridHubUrl()` (newly added, populated by
`DriverPoolManager.loadConfiguration()` — which already legitimately depends on
`ConfigManager`) fresh on every driver-creation call. The original 1-arg constructor
(and its `ConfigManager` read) is kept, used only by `BrowserType`'s own `GRID_*`
constant bindings for backward compatibility with a caller holding a raw `BrowserType`
and calling `.launchBrowser()` directly — every real pool-driven code path in this
repo now uses the 2-arg constructor and never touches `ConfigManager` from this
package. Verified structurally (constructing `RemoteGridBrowser` with a fake hub URL
and confirming the connection attempt targets that exact URL) since no real Selenium
Grid hub is available in this environment.
