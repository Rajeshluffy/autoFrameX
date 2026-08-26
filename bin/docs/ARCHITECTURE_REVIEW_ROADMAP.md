# autoFrameX Modernization & Refactoring Roadmap

Produced by the enterprise architecture review (2026-07-14), updated after the
2026-07-15 remediation pass. See [TECHNICAL_DEBT_REGISTER.md](TECHNICAL_DEBT_REGISTER.md)
for the full item-by-item status and [ARCHITECTURE.md](ARCHITECTURE.md) for diagrams.

This is scoped differently from [FRAMEWORK_IMPROVEMENT_ROADMAP.md](FRAMEWORK_IMPROVEMENT_ROADMAP.md)
(that one tracks pattern-documentation work; this one tracks the architecture review's
findings specifically) — check both, they don't overlap.

## Phase 1 — Stop the bleeding ✅ Done (2026-07-15)

Make the pipeline tell the truth.

- [x] Remove the hardcoded encryption key fallback; fail fast instead (TD-01)
- [x] Remove `continue-on-error: true` from every GitHub workflow (TD-02)
- [x] Fix the Java 16/17 mismatch across pom, Sonar, and CI (TD-05)
- [x] Wire automatic secret masking into Logback (TD-09)
- [x] Delete confirmed dead code: `Reporter.java`'s 180 commented lines, both dead test classes (TD-14)

## Phase 2 — Structural repair ✅ Done

Break the cycle, split the god classes.

- [x] Remove the `Reporter → TestMetadata` back-reference; break the package cycle (TD-06)
- [x] Extract a shared `ConfigResolver`, eliminating the duplicated priority-chain logic (TD-15)
- [x] Replace Robot/clipboard file upload with native `sendKeys`, isolate the fallback (TD-08)
- [x] Add Checkstyle + PMD + SpotBugs + `jacoco:check` to the build (TD-13)
- [x] Unify the scattered exception classes under `com.framework.exception.FrameworkException`
- [x] Add `.editorconfig` to stop tabs/spaces drift going forward
- [x] Decompose `SeleniumBase` into 11 composable action classes under
      `com.framework.selenium.api.actions`, extract `ExtentReportManager` out of
      `Reporter` (TD-07) — "extract class + delegate," not an inheritance change, so
      `ProjectSpecificMethods`/`CucumberProjectBase`/`BasePage` needed zero changes.
      See `TECHNICAL_DEBT_REGISTER.md`'s TD-07 entry for the full design and verification.

## Phase 3 — Scale-out ✅ Done

Make "20 teams, multiple apps" actually true.

- [x] Make `ConfigManager`/`DriverPoolManager` instance-scoped instead of JVM-singleton (TD-03)
- [x] Add a real parallel-execution CI smoke suite with actual test classes (TD-04)
- [x] Fix the shared Chrome download directory (TD-17)
- [x] Convert `BrowserType`'s pool-key role to an open `String`-keyed `BrowserRegistry`,
      supporting any number of independently-pooled custom browsers (TD-12, fully done
      2026-07-15 — see `TECHNICAL_DEBT_REGISTER.md`)
- [x] Fix `PoolConfig.Builder`'s dead `ConfigManager` coupling (its 5 defaults were
      always overridden by its only caller — real coupling removed with zero behavior
      change, verified)
- [x] Fix `BrowserFactory`'s 3 timeout lookups — threaded through `PoolConfig` (which
      it already received but ignored), zero interface changes, verified
- [x] Fix `RemoteGridBrowser`'s grid-hub-URL lookup (2026-07-15) — added a second
      constructor taking the URL as data (from `PoolConfig.getGridHubUrl()`) instead of
      reading `ConfigManager` directly; the pool always uses this path now, decoupling
      `design.patterns.factory.browser` from `com.framework.config.data` for this class
- [x] Fix `DataLibrary`'s hardcoded `./data/` relative path — now resolves via
      `ConfigResolver` (system property / env var / default), CWD-independent
- [x] Add an ArchUnit test (`ArchitectureRulesTest`, wired into `testng-ci.xml`) that
      fails the build if the `utils → testng.api.base` cycle ever comes back, plus a
      guard against mutable static state in page objects
- [x] Split `autoFrameX` into an 8-module Maven reactor so a team depending on it only
      pulls the concerns it needs (TD-20, fully done 2026-07-15 in 3 stages — see
      `TECHNICAL_DEBT_REGISTER.md`)

All 20 items on the register are now fixed or closed — see "Why TD-07 needed its own
pass (and why TD-20 needed one too)" below.

## Phase 4 — Polish ✅ Done

Docs truth, onboarding, and the OSS-readiness decision.

- [x] **Closed, not applicable (2026-07-15):** TD-19 (LICENSE, reverse-DNS groupId,
      purge client-specific references from core Javadoc) — repo owner confirmed this
      framework is not going open-source.
- [x] Correct the README's video/YAML/Java-version claims to match reality (TD-10)
- [x] Populate one working test class in each onboarding suite XML; real `data/accounts.xlsx`
      fixture (TD-16) — this also surfaced and fixed a real regression in `fetchData()`'s
      context binding, caught only because the onboarding suites were finally exercised
      end-to-end instead of left as templates.
- [x] Add SCA dependency scanning; remove the unused JAVE2 dependency (TD-10, TD-11)
- [x] Write `CONTRIBUTING.md` covering "add your project" in under 10 steps
- [x] Add `package-info.java` to `com.framework.utils`, `com.framework.observability`,
      `com.framework.selenium.exception`, `com.framework.config.data`,
      `com.framework.exception`, `design.patterns.object.pool`,
      `design.patterns.factory.browser`, stating each package's stability contract

---

## Why TD-07 and the BrowserType rework needed their own pass (and why TD-20 needed one too)

All three looked structurally similar to TD-03 (the JVM-singleton fix) in that they
touch a lot of surface area and carry real risk of subtly breaking existing consumers
— but TD-03 was tractable in one pass because the fix kept every external method
signature identical (`getInstance()` unchanged everywhere).

**TD-07** turned out to have that same property once actually designed: research
showed real consumers are extremely narrow (only 3 test classes extend
`ProjectSpecificMethods`, zero page objects extend `BasePage`, zero step-def classes
extend `CucumberProjectBase`), and the fix — "extract class + delegate" — kept every
existing public method signature on `SeleniumBase`/`Reporter` identical, the same way
TD-03 kept `getInstance()` identical.

**The `BrowserType` registry rework** (TD-12's remaining half + `RemoteGridBrowser`'s
coupling) had the same shape: `WebDriverFactoryInterface`/`BrowserFactory`'s public
`BrowserType`-parameterized methods and `BrowserType`'s 6 pre-existing constants were
kept working via a one-line bridge into the new `String`-keyed `BrowserRegistry` — only
the genuinely-internal pool plumbing (`WebDriverPoolFactory`, `PoolConfig`) and the
uncommitted, unreleased single-custom-slot mechanism changed shape.

That property — a design that keeps every pre-existing public signature stable — is
what made both of these safe to execute in one dedicated pass instead of remaining
deferred; see each item's `TECHNICAL_DEBT_REGISTER.md` entry for the design.

**TD-20** didn't have that property — it changed the *build artifact* teams depend on.
Splitting `autoFrameX` into 8 modules meant every consuming project's pom needs to
change which artifact(s) it depends on. That's a breaking change to the packaging
contract, not an internal refactor hidden behind a stable API, so it got its own
dedicated plan-mode pass with an explicit target design signed off before touching
code, executed as 3 independently-verified stages rather than one sweep — see
`TECHNICAL_DEBT_REGISTER.md`'s TD-20 entry for the final design and what actually
changed at each stage.
