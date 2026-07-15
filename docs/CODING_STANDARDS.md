# autoFrameX Coding Standards

Produced by the enterprise architecture review. Companion to
[TECHNICAL_DEBT_REGISTER.md](TECHNICAL_DEBT_REGISTER.md) — these rules exist to prevent
the debt register's fixed items from creeping back in.

## Exceptions

- Every framework-thrown exception should extend a common base rather than being an
  ad-hoc nested `RuntimeException` subclass scattered across unrelated utility classes
  (today: `RetryUtils.RetryExhaustedException`, `WebDriverPoolFactory.DriverAcquisitionException`,
  etc., with no shared root — a new `com.framework.exception.FrameworkException` base is
  recommended but not yet introduced).
- Log-and-continue only at true system boundaries (I/O, observability publishing);
  everything else should rethrow rather than swallow.
- Never throw a bare `RuntimeException` — always a named, typed subtype so callers can
  distinguish failure modes in a catch block.

## Utility classes

- State a Thread-Safety/Lifecycle line in every class-level Javadoc — `WaitUtils` and
  `VideoRecorder` already do this well; classes silently carrying ThreadLocal or cache
  state (`FakerDataFactory`, `DataLibrary`) should say so as clearly as classes that are
  genuinely stateless (`ValidationUtils`, `EncryptionUtils`).
- Stateless helpers stay `final` with a private constructor.
- Anything ThreadLocal-backed documents its required cleanup call.

## Configuration

- One `ConfigResolver` chain (`com.framework.config.data.ConfigResolver`), reused
  everywhere — never re-implement the 4-tier priority logic (TestNG/Cucumber param →
  env var → system property → default). This was duplicated with subtly different
  behavior in two places before being unified in the 2026-07-15 pass; don't reintroduce
  a second implementation.
- No hardcoded relative paths — resolve through config (e.g. `DataLibrary`'s
  `./data/...` path is CWD-dependent and should eventually route through
  `ConfigManager` instead).
- Every new config value documents its default and override precedence.

## Multi-context awareness

- `ConfigManager`/`DriverPoolManager` are context-keyed, not JVM singletons. Any new
  lifecycle entry point (a new TestNG listener, a new Cucumber hook, a new runner) that
  touches either of them **must** call `ConfigManager.resolveContextId(params)` +
  `bindContext(...)` on the thread it runs on before calling `getInstance()` —
  `fetchData()`'s `@DataProvider` was the one entry point missed in the original fix,
  since TestNG evaluates it on a thread that runs before `@BeforeMethod`. Check every
  new entry point against this rule, not just the obvious `@BeforeMethod`/`@Before` ones.

## Package boundaries

- Infra packages (`design.patterns.*`) should never import application-level config
  types (`com.framework.config.data.*`). `DriverPoolManager` is the one legitimate
  bridge — it's supposed to read `ConfigManager` and thread the resulting values
  through as plain data (`PoolConfig` fields) to the rest of `design.patterns.*`. As of
  2026-07-15 that's the *only* remaining legitimate import: `PoolConfig`'s own
  `ConfigManager` coupling was removed earlier, and `RemoteGridBrowser`'s grid-hub-URL
  coupling was fixed the same way `BrowserFactory`'s timeout lookups were — threaded
  through `PoolConfig.getGridHubUrl()` instead of read directly. Don't add a new
  `ConfigManager`/`ProjectConfig` import anywhere in `design.patterns.*` outside
  `DriverPoolManager` without going through this same pattern.
- No package may import back up a chain that would create a cycle — verify with
  `grep -rn "import <other-package>" src/main/java/<this-package>/` before adding a
  cross-package import in a shared/base package like `com.framework.utils`.
- Every package should eventually get a `package-info.java` stating its stability
  contract; none exist yet.

## Formatting & tooling

- `.editorconfig` is checked in (added 2026-07-15), standardizing 4-space indentation
  for new code — mixed tabs/spaces still exist across the codebase from before it
  existed, and nothing mass-reformatted them. Reformat a file to match only as part of
  otherwise touching it, not as a standalone unrelated diff.
- Checkstyle + PMD + SpotBugs now run on every `mvn verify` (report-only —
  `failOnViolation=false` in `pom.xml`, since the codebase was never checked before
  these were added).

  **Actual violation counts measured 2026-07-15** (via `mvn checkstyle:checkstyle
  pmd:pmd com.github.spotbugs:spotbugs-maven-plugin:spotbugs`, then counting each
  tool's XML report):
  - **Checkstyle: 11,039** (all "warning" severity, across 79 files) — using Google's
    ruleset (`google_checks.xml`), which is strict on things like Javadoc completeness
    and line length. This number is too large to triage file-by-file; before ever
    flipping `failOnViolation=true`, first switch to a more lenient ruleset (Sun's
    default, or a trimmed custom one) and re-measure — 11K is very likely mostly
    stylistic noise, not real defects.
  - **PMD: 37** (30 priority-3, 7 priority-4, across 10 files) — genuinely triageable.
    `Browser.java` alone has 14.
  - **SpotBugs: 34** (28 medium, 6 high priority) — by category: 16 `BAD_PRACTICE`,
    7 `MALICIOUS_CODE` (mostly `EI_EXPOSE_REP`-style internal-representation exposure),
    5 `PERFORMANCE`, 3 `I18N`, 1 `CORRECTNESS`, 2 `STYLE`. Also triageable — start with
    the 6 high-priority findings.

  Don't silently flip any of the three to enforcing without first triaging its
  backlog — do fix violations in files you're already touching in the meantime.
- `jacoco:check` enforces a 20% line-coverage floor at the `verify` phase — deliberately
  set below the ~34% actually measured via the parallel smoke suite, so it only catches
  a genuine regression. Ratchet it up as real coverage grows; never lower it to "make CI
  pass" without explaining why in the commit message.

## Dead code

- Commented-out code is deleted, not archived in place — git history is the archive.
  This was a recurring pattern before the 2026-07-15 cleanup (180 lines in
  `Reporter.java`, two entire dead test files) — don't reintroduce it.
- Any class with zero live callers is removed in the same PR that orphans it.

## Browser extension

- **Custom or additional browsers**: use `BrowserRegistry.register(id, config -> new
  YourBrowser(...))` — an open, `String`-keyed registry (2026-07-15). Register as many
  browsers as you need, each under its own id; no enum to fork. Register ids in
  uppercase (matching the framework's existing `"CHROME"`/`"GRID_CHROME"` convention)
  so they're selectable via the standard `browser=YOUR_ID` TestNG parameter/env
  var/system property/config value — `DriverPoolManager` uppercase-normalizes before
  checking the registry.
- A `BrowserProvider` receives the current `PoolConfig` at driver-creation time (not
  baked in at registration) — read whatever you need from it (e.g. a hub URL) fresh on
  every call, the same way the built-in `GRID_*` providers do, instead of reaching into
  `ConfigManager` from your provider (see the Package boundaries rule above).
- `BrowserType` (the enum) is kept only as a small, stable, backward-compatible surface
  for `BrowserFactory.createDriver(BrowserType, ...)`/`WebDriverFactoryInterface` — the
  pool itself no longer uses it as a key. Don't add new constants to it; register a new
  id with `BrowserRegistry` instead.
