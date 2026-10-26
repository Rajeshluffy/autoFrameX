package com.framework.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.framework.config.data.ConfigResolver;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Calls the OWASP ZAP REST API to run passive and active security scans.
 *
 * ZAP must be running externally before any method is called. Start ZAP with an API key and give the same
 * key to the tests ({@code -Dzap.api.key=...} or the {@code ZAP_API_KEY} environment variable):
 * {@code zap.sh -daemon -port 8080 -config api.key=<key>}
 * ({@code -config api.disablekey=true} also works, but lets anyone who can reach the port control ZAP).
 *
 * All HTTP calls use RestAssured directly (no RequestSpecification wrapper)
 * so the RestAssuredListener does not pollute test logs with ZAP API traffic.
 *
 * <h3>A security check must never pass because nothing was checked</h3>
 * Every call here is strict. A non-200 reply, a ZAP error body
 * ({@code {"code":..., "message":...}}), an unexpected response shape, or a scan
 * that does not finish in time throws; none of them is read as "no alerts". In
 * particular {@link #assertNoCriticalAlerts} waits for ZAP's passive-scan queue to
 * drain and refuses to pass when ZAP has recorded no traffic at all, because an
 * empty alert list is only good news if the scan actually saw the application.
 *
 * <h3>Passive scan workflow</h3>
 * <pre>
 *   ZapSecurityUtils.runPassiveScan(zap, "https://app/login", 300); // crawl, then wait for analysis
 *   ZapSecurityUtils.assertNoCriticalAlerts(zap, "https://app/login");
 * </pre>
 * {@link #startPassiveScan} only <em>starts</em> the crawl and returns at once;
 * calling {@code assertNoCriticalAlerts} straight after it used to read the alert
 * list before the crawl had produced anything, and passed.
 */
public final class ZapSecurityUtils {

    private static final Logger logger = LoggerFactory.getLogger(ZapSecurityUtils.class);

    /** Active scans take minutes; poll gently. */
    private static final int SCAN_POLL_INTERVAL_MS = 5_000;
    /** Spider status and passive-scan queue length are cheap calls. */
    private static final int FAST_POLL_INTERVAL_MS = 1_000;
    /** How long assertNoCriticalAlerts waits for ZAP's passive-scan queue to drain. */
    public static final int DEFAULT_PASSIVE_QUEUE_TIMEOUT_SECONDS = 120;

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** Report generation on a big session can be slow; ordinary calls answer in milliseconds. */
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int BODY_PREVIEW_CHARS = 200;
    private static final String API_KEY_PROPERTY = "zap.api.key";
    private static final String API_KEY_ENV = "ZAP_API_KEY";
    private static final String API_KEY_HEADER = "X-ZAP-API-Key";

    private ZapSecurityUtils() {}

    // =========================================================================
    // PASSIVE SCAN
    // =========================================================================

    /**
     * Enables all passive scanners and starts a spider crawl of {@code targetUrl}.
     * Returns as soon as ZAP accepts the crawl; it does <b>not</b> wait for it, nor for
     * ZAP to analyse what the crawl fetched. Use {@link #runPassiveScan} to wait, or
     * call {@link #waitForSpiderComplete} and {@link #waitForPassiveScanComplete}
     * yourself. {@link #assertNoCriticalAlerts} also waits for the analysis queue.
     *
     * @throws IllegalStateException if ZAP rejects the request or returns no scan id
     */
    public static void startPassiveScan(String zapBaseUrl, String targetUrl) {
        String spiderId = startSpider(zapBaseUrl, targetUrl);
        logger.info("ZAP passive scan started for {} - spider scan id: {}", targetUrl, spiderId);
    }

    /**
     * Crawls {@code targetUrl}, waits for the crawl to finish, then waits for ZAP to
     * finish analysing every response it fetched. When this returns, the alert list
     * is complete for that crawl.
     *
     * @param timeoutSeconds budget for the crawl and the analysis together
     * @throws IllegalStateException if ZAP errors, or the work does not finish in time
     */
    public static void runPassiveScan(String zapBaseUrl, String targetUrl, int timeoutSeconds) {
        long started = System.nanoTime();
        String spiderId = startSpider(zapBaseUrl, targetUrl);
        logger.info("ZAP passive scan started for {} - spider scan id: {}", targetUrl, spiderId);

        waitForSpiderComplete(zapBaseUrl, spiderId, timeoutSeconds);

        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
        waitForPassiveScanComplete(zapBaseUrl, (int) Math.max(1, timeoutSeconds - elapsedSeconds));
    }

    /**
     * Polls the spider until it reports 100% or {@code timeoutSeconds} elapses.
     *
     * @throws IllegalStateException on a ZAP error or on timeout
     */
    public static void waitForSpiderComplete(String zapBaseUrl, String scanId, int timeoutSeconds) {
        waitForSpiderComplete(zapBaseUrl, scanId, timeoutSeconds, FAST_POLL_INTERVAL_MS);
    }

    static void waitForSpiderComplete(String zapBaseUrl, String scanId, int timeoutSeconds, long pollMillis) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        String status;
        while (true) {
            status = zapGet(zapBaseUrl, "/JSON/spider/view/status/", "scanId", scanId).getString("status");
            logger.debug("ZAP spider {} progress: {}%", scanId, status);
            if ("100".equals(status)) {
                logger.info("ZAP spider {} complete", scanId);
                return;
            }
            if (System.nanoTime() - deadline >= 0) {
                throw new IllegalStateException("ZAP spider " + scanId + " did not complete within "
                        + timeoutSeconds + " seconds (last progress: " + status + "%)");
            }
            sleep(pollMillis);
        }
    }

    /**
     * Waits until ZAP's passive scanners have analysed every message queued so far.
     * "Empty" must hold on two consecutive polls, because a crawl can enqueue more
     * work a moment after the queue first reads zero.
     *
     * @throws IllegalStateException on a ZAP error or if the queue does not drain in time
     */
    public static void waitForPassiveScanComplete(String zapBaseUrl, int timeoutSeconds) {
        waitForPassiveScanComplete(zapBaseUrl, timeoutSeconds, FAST_POLL_INTERVAL_MS);
    }

    static void waitForPassiveScanComplete(String zapBaseUrl, int timeoutSeconds, long pollMillis) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        int emptyInARow = 0;
        String remaining;
        while (true) {
            remaining = zapGet(zapBaseUrl, "/JSON/pscan/view/recordsToScan/").getString("recordsToScan");
            if ("0".equals(remaining)) {
                if (++emptyInARow >= 2) {
                    logger.info("ZAP passive-scan queue is empty");
                    return;
                }
            } else {
                emptyInARow = 0;
            }
            if (System.nanoTime() - deadline >= 0) {
                throw new IllegalStateException("ZAP passive scan did not finish within " + timeoutSeconds
                        + " seconds (" + remaining + " message(s) still queued)");
            }
            sleep(pollMillis);
        }
    }

    // =========================================================================
    // ACTIVE SCAN
    // =========================================================================

    /**
     * Starts an active scan against {@code targetUrl} and returns the scan ID.
     * Pass the scan ID to {@link #waitForActiveScanComplete} before reading results.
     *
     * <p>Refused when the environment is production, unless {@code zap.allow.production.scan=true}
     * (env {@code ZAP_ALLOW_PRODUCTION_SCAN}): an active scan sends attack traffic at the target.
     *
     * @throws IllegalStateException if the environment is production and the scan was not explicitly allowed, or if
     *                               ZAP rejects the request or returns no scan id
     */
    public static String startActiveScan(String zapBaseUrl, String targetUrl) {
        requireActiveScanAllowed(targetUrl);
        String scanId = zapGet(zapBaseUrl, "/JSON/ascan/action/scan/",
                "url", targetUrl, "recurse", "true").getString("scan");
        if (scanId == null || scanId.isBlank()) {
            throw new IllegalStateException("ZAP did not return a scan id for the active scan of " + targetUrl);
        }
        logger.info("ZAP active scan started for {} - scan id: {}", targetUrl, scanId);
        return scanId;
    }

    /**
     * Polls the active scan status every 5 seconds until it reaches 100% or
     * {@code timeoutSeconds} elapses.
     *
     * @throws IllegalStateException on a ZAP error, or if the scan does not complete within the timeout
     */
    public static void waitForActiveScanComplete(
            String zapBaseUrl, String scanId, int timeoutSeconds) {

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (true) {
            String status = zapGet(zapBaseUrl, "/JSON/ascan/view/status/", "scanId", scanId).getString("status");
            logger.debug("ZAP active scan {} progress: {}%", scanId, status);
            if ("100".equals(status)) {
                logger.info("ZAP active scan {} complete", scanId);
                return;
            }
            if (System.nanoTime() - deadline >= 0) {
                throw new IllegalStateException(
                    "ZAP active scan " + scanId + " did not complete within " + timeoutSeconds + " seconds");
            }
            sleep(SCAN_POLL_INTERVAL_MS);
        }
    }

    // =========================================================================
    // REPORTING
    // =========================================================================

    /**
     * Downloads the ZAP HTML report and writes it to {@code outputPath}, creating
     * missing folders.
     *
     * @throws IllegalStateException if ZAP does not answer 200 or sends an empty report
     * @throws UncheckedIOException  if the file cannot be written
     */
    public static void generateHtmlReport(String zapBaseUrl, String outputPath) {
        Response response = zap(zapBaseUrl).get("/OTHER/core/other/htmlreport/");
        if (response.statusCode() != 200) {
            throw new IllegalStateException("ZAP HTML report request returned HTTP " + response.statusCode()
                    + ": " + preview(response.asString()));
        }
        byte[] reportBytes = response.asByteArray();
        if (reportBytes.length == 0) {
            throw new IllegalStateException("ZAP returned an empty HTML report");
        }

        try {
            Path target = Paths.get(outputPath);
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, reportBytes);
            logger.info("ZAP HTML report saved to: {}", target.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write ZAP report to " + outputPath, e);
        }
    }

    // =========================================================================
    // ALERT ANALYSIS
    // =========================================================================

    /**
     * Returns all ZAP alerts grouped by risk level. Keys: {@code High}, {@code Medium},
     * {@code Low}, {@code Informational} (only the levels that have alerts). Each value
     * is the distinct alert names at that level.
     *
     * @throws IllegalStateException if ZAP errors or does not return an alert list
     */
    public static Map<String, List<String>> getAlertsByRisk(String zapBaseUrl) {
        return getAlertsByRisk(zapBaseUrl, null);
    }

    /**
     * Same as {@link #getAlertsByRisk(String)}, limited to alerts raised for URLs under
     * {@code targetUrl} ({@code null} means everything ZAP knows about).
     */
    public static Map<String, List<String>> getAlertsByRisk(String zapBaseUrl, String targetUrl) {
        JsonPath json = zapGet(zapBaseUrl, "/JSON/alert/view/alerts/", "baseurl", targetUrl);
        List<Map<String, Object>> alerts = json.getList("alerts");
        if (alerts == null) {
            // "no alerts" is {"alerts":[]}; a missing list means this is not the reply we asked for.
            throw new IllegalStateException("ZAP alert reply has no 'alerts' list - refusing to read it as 'no alerts'");
        }

        Map<String, Set<String>> byRisk = new LinkedHashMap<>();
        for (Map<String, Object> alert : alerts) {
            String risk = riskOf(alert);
            Object name = alert.get("alert") != null ? alert.get("alert") : alert.get("name");
            byRisk.computeIfAbsent(risk, r -> new LinkedHashSet<>()).add(String.valueOf(name));
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String risk : List.of("High", "Medium", "Low", "Informational")) {
            if (byRisk.containsKey(risk)) {
                result.put(risk, new ArrayList<>(byRisk.remove(risk)));
            }
        }
        byRisk.forEach((risk, names) -> result.put(risk, new ArrayList<>(names)));
        return result;
    }

    /**
     * Fails the test if any High-risk alerts are present, using everything ZAP knows about.
     * See {@link #assertNoCriticalAlerts(String, String)} for what it checks first.
     */
    public static void assertNoCriticalAlerts(String zapBaseUrl) {
        assertNoCriticalAlerts(zapBaseUrl, null);
    }

    /**
     * Fails the test if any High-risk alerts are present for {@code targetUrl}
     * ({@code null} means everything). Before it trusts an empty result it:
     * <ol>
     *   <li>waits for ZAP's passive-scan queue to drain, so alerts still being worked
     *       out are not missed;</li>
     *   <li>fails if ZAP has recorded no traffic for the target, because then nothing
     *       was scanned and "no alerts" means nothing.</li>
     * </ol>
     *
     * @throws AssertionError        on High-risk alerts, or when no traffic was recorded
     * @throws IllegalStateException if ZAP errors or its queue does not drain in time
     */
    public static void assertNoCriticalAlerts(String zapBaseUrl, String targetUrl) {
        assertNoCriticalAlerts(zapBaseUrl, targetUrl, DEFAULT_PASSIVE_QUEUE_TIMEOUT_SECONDS, FAST_POLL_INTERVAL_MS);
    }

    static void assertNoCriticalAlerts(String zapBaseUrl, String targetUrl, int queueTimeoutSeconds, long pollMillis) {
        waitForPassiveScanComplete(zapBaseUrl, queueTimeoutSeconds, pollMillis);

        String recorded = zapGet(zapBaseUrl, "/JSON/core/view/numberOfMessages/", "baseurl", targetUrl)
                .getString("numberOfMessages");
        if (parseCount(recorded) <= 0) {
            Assert.fail("ZAP has recorded no traffic" + (targetUrl == null ? "" : " for " + targetUrl)
                    + ", so nothing was scanned and 'no alerts' would mean nothing."
                    + " Run the scan (or proxy the browser through ZAP) before asserting.");
        }

        Map<String, List<String>> alerts = getAlertsByRisk(zapBaseUrl, targetUrl);
        List<String> highRisk = alerts.getOrDefault("High", List.of());

        if (!highRisk.isEmpty()) {
            highRisk.forEach(name -> logger.error("ZAP High-risk alert: {}", name));
            Assert.fail("ZAP found " + highRisk.size() + " High-risk security alert(s): " + highRisk);
        }
        logger.info("ZAP security check passed - no High-risk alerts in {} recorded message(s) (other alerts: {})",
                recorded.trim(), summarise(alerts));
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * An active scan is an attack on {@code targetUrl}: SQL injection and XSS probes, fuzzing, forced browsing. A
     * pipeline whose environment variable was left on production would run it against production, so it needs a
     * deliberate, named switch. The passive scan only observes traffic and is not restricted.
     */
    static void requireActiveScanAllowed(String targetUrl) {
        String environment = ConfigResolver.resolveEnvironment(runtimeEnvironmentParameters());
        if (!ConfigResolver.isProduction(environment)) {
            return;
        }
        if (ConfigResolver.resolveBoolean("zap.allow.production.scan", "ZAP_ALLOW_PRODUCTION_SCAN", null, false)) {
            logger.warn("Active scan of {} in the {} environment, allowed by zap.allow.production.scan", targetUrl, environment);
            return;
        }
        throw new IllegalStateException("Refusing to run a ZAP active scan against " + targetUrl + ": the environment is '"
                + environment + "' (production). An active scan sends attack traffic at the target. Run it against dev or "
                + "qa, or, if production really is meant, set zap.allow.production.scan=true (or ZAP_ALLOW_PRODUCTION_SCAN=true).");
    }

    /** The environment parameters of the context bound to this thread, if any. */
    private static Map<String, String> runtimeEnvironmentParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        try {
            for (String key : new String[] { "environment", "env" }) {
                String value = com.framework.config.data.ConfigManager.getInstance().getRuntimeOverride(key);
                if (value != null) {
                    params.put(key, value);
                }
            }
        } catch (RuntimeException e) {
            logger.debug("No suite parameters available for the environment: {}", e.toString());
        }
        return params;
    }

    private static String startSpider(String zapBaseUrl, String targetUrl) {
        zapGet(zapBaseUrl, "/JSON/pscan/action/enableAllScanners/");
        String spiderId = zapGet(zapBaseUrl, "/JSON/spider/action/scan/", "url", targetUrl).getString("scan");
        if (spiderId == null || spiderId.isBlank()) {
            throw new IllegalStateException("ZAP did not return a spider scan id for " + targetUrl);
        }
        return spiderId;
    }

    private static RequestSpecification zap(String zapBaseUrl) {
        RequestSpecification request = RestAssured.given()
                .config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", CONNECT_TIMEOUT_MS)
                        .setParam("http.socket.timeout", READ_TIMEOUT_MS)))
                .baseUri(zapBaseUrl);
        String apiKey = apiKey();
        if (apiKey != null) {
            request = request.header(API_KEY_HEADER, apiKey);   // a header, so it never lands in a URL or a log
        }
        return request;
    }

    /**
     * The ZAP API key: the {@code zap.api.key} system property, else the {@code ZAP_API_KEY} environment variable,
     * else none. ZAP started with {@code -config api.disablekey=true} (the setup this module used to document)
     * lets anyone who can reach its port run scans and shut it down; a ZAP with a key rejects every call that
     * does not carry it, which these utilities could not send before.
     */
    static String apiKey() {
        String key = System.getProperty(API_KEY_PROPERTY);
        if (key == null || key.isBlank()) {
            key = System.getenv(API_KEY_ENV);
        }
        return key == null || key.isBlank() ? null : key.trim();
    }

    /**
     * Asks ZAP for its version, with the same timeouts, API key and error handling as every other call.
     *
     * @return the version ZAP reports
     * @throws IllegalStateException if ZAP is unreachable, answers with an error, or does not report a version
     */
    public static String verifyReachable(String zapBaseUrl) {
        String version;
        try {
            version = zapGet(zapBaseUrl, "/JSON/core/view/version/").getString("version");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {   // includes the checked ConnectException REST Assured throws without declaring it
            throw new IllegalStateException("ZAP is not reachable at " + zapBaseUrl + ": " + e.getMessage(), e);
        }
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("ZAP at " + zapBaseUrl + " did not report a version");
        }
        return version;
    }

    /**
     * One GET against the ZAP JSON API. Anything other than a 200 with a normal JSON body
     * throws, so callers can never mistake an error reply for an empty result.
     *
     * @param queryParams alternating names and values; a {@code null} value is skipped
     */
    static JsonPath zapGet(String zapBaseUrl, String path, String... queryParams) {
        RequestSpecification request = zap(zapBaseUrl);
        for (int i = 0; i + 1 < queryParams.length; i += 2) {
            if (queryParams[i + 1] != null) {
                request = request.queryParam(queryParams[i], queryParams[i + 1]);
            }
        }
        Response response = request.get(path);
        String body = response.asString();
        if (response.statusCode() != 200) {
            throw new IllegalStateException("ZAP " + path + " returned HTTP " + response.statusCode()
                    + ": " + preview(body));
        }
        JsonPath json;
        boolean errorBody;
        try {
            json = new JsonPath(body);
            // JsonPath parses lazily, so this first read is what rejects a body that is not JSON.
            errorBody = json.get("code") != null && json.get("message") != null;
        } catch (RuntimeException e) {
            throw new IllegalStateException("ZAP " + path + " did not return JSON: " + preview(body), e);
        }
        if (errorBody) {
            throw new IllegalStateException("ZAP " + path + " reported an error: " + preview(body));
        }
        return json;
    }

    /** ZAP names the level in {@code risk}; fall back to the numeric {@code riskcode} (0-3) if it is absent. */
    private static String riskOf(Map<String, Object> alert) {
        Object risk = alert.get("risk");
        if (risk != null && !String.valueOf(risk).isBlank()) {
            String text = String.valueOf(risk).trim();
            return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase();
        }
        switch (String.valueOf(alert.get("riskcode"))) {
            case "3": return "High";
            case "2": return "Medium";
            case "1": return "Low";
            default:  return "Informational";
        }
    }

    private static int parseCount(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("ZAP returned a non-numeric message count: " + preview(value));
        }
    }

    private static String summarise(Map<String, List<String>> alerts) {
        StringBuilder sb = new StringBuilder();
        alerts.forEach((risk, names) -> sb.append(sb.length() == 0 ? "" : ", ").append(risk).append('=').append(names.size()));
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static String preview(String body) {
        if (body == null) {
            return "<empty>";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= BODY_PREVIEW_CHARS ? flat : flat.substring(0, BODY_PREVIEW_CHARS) + "...";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // Do not swallow it and keep polling: that would spin until the deadline.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for ZAP", e);
        }
    }
}
