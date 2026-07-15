# DevOps Integration Guide

This guide explains how downstream projects that use autoFrameX as a Maven dependency can reuse and extend the DevOps artifacts shipped with the framework.

---

## What autoFrameX ships

autoFrameX is an 8-module Maven reactor (TD-20). Every `mvn test`/`mvn
verify` invocation below targets one module with `-pl <module>` — Surefire
resolves suite XML paths relative to that module's own basedir, not the
reactor root, so the module and suite file must always be specified together.

| Artifact | Location | Purpose |
|---|---|---|
| `Jenkinsfile` | project root | Parameterized Jenkins pipeline template |
| `testng-ci.xml` | `autoframex-selenium/` | Browser-free CI suite (no Chrome required) |
| `testng.xml` | `autoframex-testkit/` | Aggregate suite spanning every module |
| `.github/workflows/ci.yml` | `.github/workflows/` | GitHub Actions: push/PR smoke check (`autoframex-selenium`) |
| `.github/workflows/regression.yml` | `.github/workflows/` | GitHub Actions: manual full regression (module chosen via `module` input) |
| `.github/workflows/dependency-check.yml` | `.github/workflows/` | GitHub Actions: weekly/manual OWASP SCA scan (reactor root, every module) |
| `Dockerfile` | project root | Chrome + Maven base image, builds every module |
| `docker-compose.yml` | project root | Local container execution with volume mounts |

---

## Jenkins

### Copy and customize the Jenkinsfile

Copy `Jenkinsfile` from autoFrameX into your project root and adjust:

```groovy
// 1. Point to your suite files
string(name: 'SUITE_FILE', defaultValue: 'myproject-testng.xml', ...)

// 2. Add your credential IDs
withCredentials([
    file(credentialsId: 'myproject-config', variable: 'MY_CONFIG')
]) { ... }

// 3. Lower the quality gate threshold for real test suites
if (failureRate > 10) {   // 10% instead of the framework's 50%
    error("Quality gate failed")
}

// 4. Enable Slack/email notifications (stubs are already in the file)
// Uncomment the slackSend / mail blocks in post { success } and post { failure }
```

### Parameters available out of the box

| Parameter | Default | Description |
|---|---|---|
| `BROWSER` | chrome | Browser for UI tests |
| `ENVIRONMENT` | qa | Target environment |
| `HEADLESS` | true | Headless mode |
| `SUITE_FILE` | testng.xml | TestNG suite to run (relative to `MODULE`'s directory) |
| `MODULE` | autoframex-testkit | Reactor module that owns `SUITE_FILE` (TD-20) |
| `THREAD_COUNT` | 1 | Surefire parallel thread count |

All parameters are overridable at build time: `Build with Parameters` in the Jenkins UI, or via `curl` for API-triggered builds.

---

## GitHub Actions

### Use ci.yml as a PR gate

Copy `.github/workflows/ci.yml` into your project. It first runs `mvn clean
install -DskipTests -Djacoco.skip=true` (required so `-pl`-scoped steps below
can resolve upstream reactor modules), then runs `testng-ci.xml`
(browser-free) from `autoframex-selenium` on every push and PR. To point it
at your own browser-free suite, add `-pl` for whichever module you placed it in:

```yaml
- name: Run framework unit tests
  run: |
    mvn test \
      -pl your-module \
      -Dtestng.suite.file=myproject-ci.xml \   # your CI suite
      -Denv=qa \
      -Dheadless=true
```

### Use regression.yml for manual full runs

Copy `.github/workflows/regression.yml`. It is `workflow_dispatch` only — it never auto-triggers. It takes a `module` input alongside `suite_file` (TD-20) so it knows which reactor module's basedir to resolve the suite file against. Trigger it from the GitHub Actions UI or via the API:

```bash
gh workflow run regression.yml \
  -f browser=chrome \
  -f environment=qa \
  -f headless=true \
  -f module=your-module \
  -f suite_file=myproject-testng.xml
```

### Add your project-specific config injection

If your project needs secrets injected before tests run, add a step before the `mvn test` step. Point it at whichever module reads the config (typically `autoframex-core`, since `ConfigManager`/`ProjectDirector` live there):

```yaml
- name: Inject project config
  env:
    MY_CONFIG: ${{ secrets.MY_PROJECT_CONFIG }}
  run: |
    mkdir -p autoframex-core/src/main/resources
    echo "$MY_CONFIG" > autoframex-core/src/main/resources/myProjectConfig.properties
```

---

## Docker

### Run autoFrameX tests locally in a container

The image builds and installs every reactor module (TD-20); `MODULE` selects
which one the container's `mvn test` targets, and `SUITE_FILE` must be a
suite that module actually owns.

```bash
# Build the image
docker build -t autoframex .

# Run with default settings (autoframex-testkit / testng.xml, chrome, headless, qa)
docker run --rm \
  -v $(pwd)/autoframex-testkit/reports:/app/autoframex-testkit/reports \
  -v $(pwd)/autoframex-testkit/logs:/app/autoframex-testkit/logs \
  autoframex

# Override module, suite, and environment
docker run --rm \
  -e MODULE=your-module \
  -e SUITE_FILE=myproject-testng.xml \
  -e ENVIRONMENT=staging \
  -v $(pwd)/your-module/reports:/app/your-module/reports \
  autoframex
```

### Extend the autoFrameX image for your project

Create a `Dockerfile` in your project that builds FROM the autoFrameX image:

```dockerfile
FROM autoframex:latest

# Add your project-specific config files (autoframex-core owns ConfigManager/ProjectDirector)
COPY src/main/resources/myProjectConfig.properties \
     /app/autoframex-core/src/main/resources/myProjectConfig.properties

# Override the default module/suite
ENV MODULE=your-module
ENV SUITE_FILE=myproject-testng.xml
ENV ENVIRONMENT=qa
```

This inherits Chrome, Maven, all framework dependencies, and the compiled framework classes for every module. Your project only adds its own source and config on top.

### Use docker-compose for local execution

Copy `docker-compose.yml` and run:

```bash
# Run with defaults
docker-compose up

# Override via environment variables
MODULE=your-module SUITE_FILE=myproject-testng.xml ENVIRONMENT=staging docker-compose up

# Or create a .env file
echo "MODULE=your-module" > .env
echo "SUITE_FILE=myproject-testng.xml" >> .env
echo "ENVIRONMENT=staging" >> .env
docker-compose up
```

Note: `docker-compose.yml`'s volume mounts are hardcoded to the `MODULE`
default (`autoframex-testkit`) — if you override `MODULE`, update the
`volumes:` paths in your copy to match (`./your-module/reports:/app/your-module/reports`, etc.).

Test artifacts (HTML reports, NDJSON observability events, Surefire XML) are written to the mounted host directories after the container exits.

### Enable Selenium Grid

Uncomment the `selenium-hub` and `selenium-chrome` services in `docker-compose.yml`, then add to the `autoframex-tests` environment:

```yaml
GRID_ENABLED: "true"
GRID_HUB_URL: "http://selenium-hub:4444/wd/hub"
```

---

## Observability artifacts

Every test run produces `logs/test-events.json` — one JSON line per test containing:
- Correlation IDs: `traceId`, `buildId`, `executionId`, `sessionId`
- Test outcome: `status`, `failureCategory`, `durationMs`, `retryCount`
- Flakiness: `flakyScore` (0.0–1.0 sliding window)

This file is archived by both the Jenkinsfile and the GitHub Actions workflows. Feed it to Filebeat → Elasticsearch → Kibana for centralized test observability across all projects.

---

## Quality gate thresholds

The Jenkinsfile ships with a 50% failure threshold to accommodate autoFrameX's own `RetryTest` (which intentionally always fails — it tests the retry engine). For real project suites, lower this:

```groovy
// In your project's Jenkinsfile copy:
if (failureRate > 10) {   // fail build if >10% of tests fail
    error("Quality gate failed")
}
```
