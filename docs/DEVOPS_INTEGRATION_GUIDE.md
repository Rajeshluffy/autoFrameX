# DevOps Integration Guide

This guide explains how downstream projects that use autoFrameX as a Maven dependency can reuse and extend the DevOps artifacts shipped with the framework.

---

## What autoFrameX ships

| Artifact | Location | Purpose |
|---|---|---|
| `Jenkinsfile` | project root | Parameterized Jenkins pipeline template |
| `testng-ci.xml` | project root | Browser-free CI suite (no Chrome required) |
| `.github/workflows/ci.yml` | `.github/workflows/` | GitHub Actions: push/PR smoke check |
| `.github/workflows/regression.yml` | `.github/workflows/` | GitHub Actions: manual full regression |
| `Dockerfile` | project root | Chrome + Maven base image |
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
| `SUITE_FILE` | testng.xml | TestNG suite to run |
| `THREAD_COUNT` | 1 | Surefire parallel thread count |

All parameters are overridable at build time: `Build with Parameters` in the Jenkins UI, or via `curl` for API-triggered builds.

---

## GitHub Actions

### Use ci.yml as a PR gate

Copy `.github/workflows/ci.yml` into your project. It runs `testng-ci.xml` (browser-free) on every push and PR. To point it at your own browser-free suite:

```yaml
- name: Run framework unit tests
  run: |
    mvn test \
      -Dtestng.suite.file=myproject-ci.xml \   # your CI suite
      -Denv=qa \
      -Dheadless=true
```

### Use regression.yml for manual full runs

Copy `.github/workflows/regression.yml`. It is `workflow_dispatch` only — it never auto-triggers. Trigger it from the GitHub Actions UI or via the API:

```bash
gh workflow run regression.yml \
  -f browser=chrome \
  -f environment=qa \
  -f headless=true \
  -f suite_file=myproject-testng.xml
```

### Add your project-specific config injection

If your project needs secrets injected before tests run, add a step before the `mvn test` step:

```yaml
- name: Inject project config
  env:
    MY_CONFIG: ${{ secrets.MY_PROJECT_CONFIG }}
  run: |
    mkdir -p src/main/resources
    echo "$MY_CONFIG" > src/main/resources/myProjectConfig.properties
```

---

## Docker

### Run autoFrameX tests locally in a container

```bash
# Build the image
docker build -t autoframex .

# Run with default settings (testng.xml, chrome, headless, qa)
docker run --rm \
  -v $(pwd)/reports:/app/reports \
  -v $(pwd)/logs:/app/logs \
  autoframex

# Override suite and environment
docker run --rm \
  -e SUITE_FILE=myproject-testng.xml \
  -e ENVIRONMENT=staging \
  -v $(pwd)/reports:/app/reports \
  autoframex
```

### Extend the autoFrameX image for your project

Create a `Dockerfile` in your project that builds FROM the autoFrameX image:

```dockerfile
FROM autoframex:latest

# Add your project-specific config files
COPY src/main/resources/myProjectConfig.properties \
     /app/src/main/resources/myProjectConfig.properties

# Override the default suite
ENV SUITE_FILE=myproject-testng.xml
ENV ENVIRONMENT=qa
```

This inherits Chrome, Maven, all framework dependencies, and the compiled framework classes. Your project only adds its own source and config on top.

### Use docker-compose for local execution

Copy `docker-compose.yml` and run:

```bash
# Run with defaults
docker-compose up

# Override via environment variables
SUITE_FILE=myproject-testng.xml ENVIRONMENT=staging docker-compose up

# Or create a .env file
echo "SUITE_FILE=myproject-testng.xml" > .env
echo "ENVIRONMENT=staging" >> .env
docker-compose up
```

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
