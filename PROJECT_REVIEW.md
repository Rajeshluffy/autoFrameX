# autoFrameX — Project Review & High-Priority Remediation

Reviewed: 2026-10-13 · Branch: `main` · Scope: architecture, build, CI/CD (GitHub Actions, Jenkins, Docker, K8s), and ~15 core classes read in depth.

**Not covered:** internals of `WebDriverPoolFactory`, `Reporter`, `VideoRecorder`, the Cucumber glue, the performance module, and the JMeter plans.

## 1. Build status

`mvn clean install` (offline) → **BUILD SUCCESS**, all 9 reactor entries.

| Module | Tests | Failures |
|---|---|---|
| `autoframex-core` | 47 (35 existing + 12 new) | 0 |
| `autoframex-selenium` | 86 | 0 |
| `autoframex-cucumber` | 2 | 0 |
| `autoframex-security` | 2 | 0 |
| `autoframex-api`, `-database`, `-performance`, `-testkit` | none reported | n/a |

Caveat: JaCoCo was skipped for modules with no execution data, so the 20% coverage floor was only checked for `core` and `selenium`. A green build says little about the untested modules.

## 2. What's good

- The 8-module split has no dependency cycles, and an ArchUnit rule guards it.
- Config priority chain (param → env → system property → default) is a single implementation (`ConfigResolver`).
- Driver handling is context-aware and thread-safe; failed drivers are poisoned, never re-pooled.
- Regression tests for the pool, retry engine, and registry are well targeted.

## 3. High-priority findings and status

| # | Finding | Status |
|---|---|---|
| 1 | Credential files baked into the Docker image (`.dockerignore` bare names only match the context root) | **Fixed** — needs a runtime-injection decision (see §5) |
| 2 | Jenkins pipeline can go green when a suite never ran | **Fixed in the Jenkinsfile, untested** (Jenkins is down) |
| 3 | `EncryptionUtils` returned `null` on a missing key, contradicting its Javadoc | **Fixed + tested** |
| 4 | Log masking missed JSON and could corrupt log lines | **Fixed + tested** |
| 5 | Local `main` diverged from `origin/main` (11 ahead, 12 behind) | **Open** — command blocked by the tool, run it yourself (§5) |

### 3.1 Docker secrets (item 1)

- Jenkins injects `alfaDOCKConfig.properties` / `leafTapConfig.properties` under `autoframex-core/src/main/resources/`, and the Dockerfile copies that whole tree into the image.
- Docker matches a bare `.dockerignore` pattern against the context root only, so the old rules never excluded those files.
- `.dockerignore` now uses `**/alfaDOCKConfig.properties` and `**/leafTapConfig.properties`.
- The ineffective `Config.properties` rule was removed (it matched nothing and carried a misleading comment).
- The Jenkinsfile's "Build Docker Image" comment, which claimed the image picks up the injected files, was corrected.

### 3.2 Jenkinsfile (item 2)

- **Fast failure:** `kubectl wait --for=condition=complete … || true` sat out its full 10-minute timeout when a Job failed. It is replaced by a poll loop (120 × 5 s) that exits on Complete or Failed and prints the last 60 log lines on failure.
- **Empty-result guard:** a suite that produces no surefire XML now fails its stage instead of leaving the quality gate nothing to count.
- **Opt-in suites:** `RUN_ALFADOCK_SUITE` and `RUN_GPN_SUITE` (default `false`). Their suite files (`alfaDOCKtestng.xml`, `gpn.xml`) are not in this repo, so those Jobs always failed.
- **Parameter validation:** new *Validate Parameters* stage restricts `MODULE` (`autoframex-<name>`), `SUITE_FILE` (plain `*.xml` name) and `THREAD_COUNT` (number) before they reach shell scripts, closing a shell-injection route for anyone who can start a build.
- **Sonar stage:** its `when` tested `env.SONAR_TOKEN`, which only exists inside `withCredentials`, so the stage never ran. It now probes for the `sonar-token` and `sonar-host-url` credentials. The token is passed through the shell environment instead of Groovy string interpolation.
- The three K8s suite scripts were generated from one template so they cannot drift apart.
- Verification so far: brace/parenthesis balance only. No Groovy or Jenkins was available.

### 3.3 `EncryptionUtils` (item 3)

`deriveKey` was called inside `catch (Exception)`, so the "no key configured" `IllegalStateException` became a logged error and a `null` return. `encrypt` and `decrypt` now read the passphrase before the `try`, so a missing key throws. A corrupt value or a wrong key still returns `null`.

### 3.4 Log masking (item 4)

- The old pattern `key\s*[=:]\s*\S+` missed `"password":"abc"`.
- Against a compact JSON log line its greedy `\S+` swallowed every field after the match, corrupting the record the file encoder writes.
- The new patterns cover plain `key=value`, quoted JSON values (including spaces), JSON escaped inside a JSON log line, and `Authorization: Bearer|Basic …` headers. Matching stops at the value's end.
- Behaviour change: the original delimiter is now preserved (`password: abc` → `password: ***`; it used to become `password=***`).

## 4. Files changed (uncommitted)

| File | Change |
|---|---|
| `autoframex-core/src/main/java/com/framework/utils/EncryptionUtils.java` | key handling + masking patterns |
| `autoframex-core/src/test/java/com/framework/utils/EncryptionUtilsTest.java` | new, 12 tests |
| `autoframex-core/testng.xml` | registers the new test class |
| `.dockerignore` | `**/` patterns for the two credential files |
| `Jenkinsfile` | items in §3.2 |
| `PROJECT_REVIEW.md` | this document |

## 5. Needs your action

**Item 5 — diverged `main`.** Verified: local and `origin/main` have the identical tree hash, and the 11 local commits have the same patch IDs as the 11 remote ones. The only extra remote commit is empty. This realigns the branch and keeps all uncommitted edits (old tip: `4aaf496`):

```bash
git reset --mixed origin/main
```

**Decision — runtime config for AlfaDOCK / Leaftaps.** With the credential files out of the image, suites that need them must receive them at runtime, typically a Kubernetes Secret mounted into the Job's pod. Not built yet: it can't be tested while Jenkins is down, and those suites are now off by default.

**When Jenkins is back:** run one pipeline with default parameters first. The new job-wait loop is the part most worth watching.

## 6. Remaining findings (not yet addressed)

### Medium

- **FIXED 2026-10-15: `ZapSecurityUtils` can give a false green.** `startPassiveScan` doesn't wait for the spider or the passive-scan queue, so `assertNoCriticalAlerts` may run before any alerts exist. HTTP statuses aren't checked, and a failed report write is swallowed.
- **FIXED 2026-10-15: `RequestSpecBuilder` sends two auth schemes.** `setOAuth` also stores the username and password, so `build()` applies Basic auth and then the OAuth token. `RequestAuthentication` doesn't check the token response status and caches for a fixed hour instead of honouring `expires_in`.
- **FIXED 2026-10-15: `DBManager` isn't safe to share.** One instance per config class name (later configs ignored), one shared connection across threads, and `executeQuery` swallows errors and returns `null`.
- **FIXED 2026-10-15: `closeAfterEach` does nothing in teardown.** Both branches call `release(driver, false)`, and the comments describe different behaviour (`DriverPoolManager.java:284`).
- **FIXED 2026-10-15: Unconditional browser flags.** `--no-sandbox`, `acceptInsecureCerts` and disabled Safe Browsing downloads are always on, including for `prod` runs. Gate them on CI/container mode.
- **Docs are not versioned.** `/docs/` is in `.gitignore`. The pom and code comments reference `docs/TECHNICAL_DEBT_REGISTER.md` and `docs/CODING_STANDARDS.md`, which don't exist.
- **Thin test coverage (improved 2026-10-15: `autoframex-api`, `-database` and `-security` now have unit suites).** `autoframex-database` and `autoframex-security` have no tests; `autoframex-api` has one WireMock helper. About 107 `@Test` methods in total. The coverage floor is 20% and non-fatal, and CI only measures the selenium module.

### Low

- `performance/` is 42 MB, mostly a committed JMeter HTML report; the git pack is 17.7 MB because of it. Ignore `performance/**/results/`.
- `ProjectSpecificMethods.faker()` returns `null` (dead API).
- FIXED 2026-10-15 for API groups (they now default to no retry): every `@Test` gets 4 retries by default, including non-idempotent API calls. A failure on the same line twice stops retrying, so deterministic failures get only one retry.
- Logback 1.4.7, TestNG 7.7.0 and Surefire 3.0.0-M7 are old; the weekly dependency-check should flag them.
- `ci.yml` runs on every push to any branch and again on PRs. `sonar.yml` will fail on fork PRs, which don't receive secrets.
- SpotBugs findings print at `[ERROR]` level while the build still passes. The `Random` finding in `EncryptionUtils` is a false positive (it is `SecureRandom`).
