# API module (`autoframex-api`)

REST API testing on top of REST Assured. A project depends on `autoframex-api` and gets everything below; it needs
no Selenium classes.

## A test in five lines

```java
RequestSpecBuilder base = ApiConfig.load().requestSpecBuilder();        // where the API is, how to sign in
RestAssuredBase client = new RestAssuredBase();

ResponseAPI created = client.send(base, ApiRequest.post("incident").contentType("application/json").body(payload));
ApiAssertions.that(created).created().contentType("application/json").matchesSchema("schemas/incident.json");

String sysId = created.path("result.sys_id", String.class);
ApiCleanup.register("delete incident " + sysId, () -> client.send(base, ApiRequest.delete("incident/{id}").pathParam("id", sysId)));
```

## Running it under TestNG

Extend `com.api.testng.ApiTestBase` (it needs only `autoframex-core` and `autoframex-api`). You get the framework's
report (a node per class and method, every call's timing as a step, `@TestMetadata` for the names) and your
`ApiCleanup` undos run after every test, pass or fail, newest first. A failing undo is a warning in the report and
never fails the hook (a failing `@AfterMethod` would make TestNG skip the tests after it).

```java
@TestMetadata(name = "Incident API", authors = "qa", category = "api")
public class IncidentApiTest extends ApiTestBase { ... }
```

## The pieces

| Class | What it is for |
|---|---|
| `ApiConfig` | The API under test, read from the framework's configuration chain (below). |
| `RequestSpecBuilder` | Service-wide settings: base URI and path, authentication, timeouts. Build once, share. |
| `ApiRequest` | One call, as an immutable value: verb, path with `{placeholders}`, path/query parameters, headers, cookies, body. Nothing one call sets can leak into the next. |
| `RestAssuredBase.send(base, request[, RetryPolicy])` | Sends it. A 401 on an OAuth call gets a new token and one retry. Throws `ApiCallException` when there is no response at all. |
| `ResponseAPI` | The answer: status, headers (case-insensitive), `path(...)`, `pathList(...)`, `as(TypeRef)`, `getResponseTimeMs()`, `reproCommand()`. |
| `ApiAssertions` | `status`, `created()`, `contentType`, `header`, `respondedWithin`, `jsonPath`, `matchesSchema`, ... A failure shows the request, status, time, `X-Request-Id`, a masked body preview and a `curl` command to repeat the call. |
| `RetryPolicy` | Repeats **GET, HEAD, OPTIONS only**, on 429/502/503/504 or no response; exponential wait capped at 10 s; honors `Retry-After`. Off by default. A POST is never repeated by it. |
| `ApiAwait` | Polls until the API reaches a state (`ApiAwait.until(...)`), instead of asserting on the first answer or sleeping. |
| `ApiCleanup` / `UniqueData` | Register an undo when a test creates something; run them all in an `@After` hook, newest first, even after a failure. `UniqueData.unique("incident")` gives names that never collide across parallel tests or reruns. |
| `ApiTimeouts` | Connect (10 s) and read (30 s) timeouts on every call. |
| `TokenProvider` | One OAuth token per client and user, shared by the whole run and renewed shortly before it expires. |

## Configuration

Resolved in the framework's usual order: **TestNG parameter, then environment variable, then `-D` system property.**

| Setting (parameter / `-D`) | Environment variable | Meaning |
|---|---|---|
| `apiBaseUri` | `API_BASE_URI` | required, e.g. `https://host` (a trailing `/` is fine) |
| `apiBasePath` | `API_BASE_PATH` | prefix of every call |
| `apiOAuthBasePath` | `API_OAUTH_BASE_PATH` | token endpoint |
| `apiGrantType` | `API_GRANT_TYPE` | default `client_credentials` |
| `apiClientId` / `apiClientSecret` | `API_CLIENT_ID` / `API_CLIENT_SECRET` | setting a client id turns OAuth on |
| `apiUsername` / `apiPassword` | `API_USERNAME` / `API_PASSWORD` | OAuth `password` grant, or Basic auth (sent on the first request) |
| `apiScope` | `API_SCOPE` | optional OAuth scope |
| `apiBearerToken` | `API_BEARER_TOKEN` | a ready-made token |
| `apiConnectTimeoutMs`, `apiReadTimeoutMs` | `API_CONNECT_TIMEOUT_MS`, `API_READ_TIMEOUT_MS` | timeouts |
| `apiRetryMax`, `apiRetryBaseMs` | `API_RETRY_MAX`, `API_RETRY_BASE_MS` | retry count (0 = off) and first wait |

One sign-in scheme is used: bearer token, else OAuth, else Basic, else none. A half-filled scheme fails at set-up
with the names of the missing settings. **Put secrets in environment variables, not `-D`:** system properties show
in the process list and in the surefire XML. `ApiConfig.toString()` never prints a secret.

## Running in Jenkins without Docker or Kubernetes

`Jenkinsfile.api` runs one module's suite with Maven and publishes the JUnit results. Create a Pipeline job with
*Script Path* `Jenkinsfile.api`.

* `MODULE` and `SUITE_FILE` choose what runs (default: the `autoframex-api` unit suite, which needs no network).
* `API_BASE_URI`, `API_BASE_PATH`, ... are ordinary build parameters; Jenkins turns them into the environment
  variables `ApiConfig` reads.
* Secrets come from the Jenkins credential store. Put the id of a *Username with password* credential in
  `OAUTH_CLIENT_CREDENTIALS_ID` (becomes `API_CLIENT_ID` / `API_CLIENT_SECRET`) or `API_USER_CREDENTIALS_ID`
  (`API_USERNAME` / `API_PASSWORD`), or a *Secret text* id in `BEARER_TOKEN_CREDENTIALS_ID` (`API_BEARER_TOKEN`).
  They are bound for the test step only and masked in the log. A named credential that does not exist fails the
  build at once.
* `junit` publishes `**/target/surefire-reports/TEST-*.xml`, so Jenkins shows one row per test method.

For the browser suites, and for running tests inside the Kubernetes cluster, keep using the main `Jenkinsfile`.
