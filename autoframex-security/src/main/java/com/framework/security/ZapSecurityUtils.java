package com.framework.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * Calls the OWASP ZAP REST API to run passive and active security scans.
 *
 * ZAP must be running externally before any method is called. Start ZAP with:
 * {@code zap.sh -daemon -port 8080 -config api.disablekey=true}
 *
 * All HTTP calls use RestAssured directly (no RequestSpecification wrapper)
 * so the RestAssuredListener does not pollute test logs with ZAP API traffic.
 */
public final class ZapSecurityUtils {

    private static final Logger logger = LoggerFactory.getLogger(ZapSecurityUtils.class);
    private static final int SCAN_POLL_INTERVAL_MS = 5_000;

    private ZapSecurityUtils() {}

    // =========================================================================
    // PASSIVE SCAN
    // =========================================================================

    /**
     * Enables all passive scanners and starts a spider crawl of {@code targetUrl}.
     * Passive scanning runs automatically as ZAP observes traffic — no explicit
     * wait is needed before calling {@link #getAlertsByRisk}.
     */
    public static void startPassiveScan(String zapBaseUrl, String targetUrl) {
        RestAssured.given()
            .baseUri(zapBaseUrl)
            .get("/JSON/pscan/action/enableAllScanners/")
            .then().statusCode(200);

        Response spiderResponse = RestAssured.given()
            .baseUri(zapBaseUrl)
            .queryParam("url", targetUrl)
            .get("/JSON/spider/action/scan/")
            .then().extract().response();

        logger.info("ZAP passive scan started for {} — spider scan id: {}",
            targetUrl, spiderResponse.jsonPath().getString("scan"));
    }

    // =========================================================================
    // ACTIVE SCAN
    // =========================================================================

    /**
     * Starts an active scan against {@code targetUrl} and returns the scan ID.
     * Pass the scan ID to {@link #waitForActiveScanComplete} before reading results.
     */
    public static String startActiveScan(String zapBaseUrl, String targetUrl) {
        Response response = RestAssured.given()
            .baseUri(zapBaseUrl)
            .queryParam("url", targetUrl)
            .queryParam("recurse", "true")
            .get("/JSON/ascan/action/scan/")
            .then().extract().response();

        String scanId = response.jsonPath().getString("scan");
        logger.info("ZAP active scan started for {} — scan id: {}", targetUrl, scanId);
        return scanId;
    }

    /**
     * Polls the active scan status every 5 seconds until it reaches 100% or
     * {@code timeoutSeconds} elapses.
     *
     * @throws IllegalStateException if the scan does not complete within the timeout
     */
    public static void waitForActiveScanComplete(
            String zapBaseUrl, String scanId, int timeoutSeconds) {

        long deadline = System.currentTimeMillis() + (long) timeoutSeconds * 1_000;
        while (System.currentTimeMillis() < deadline) {
            String status = RestAssured.given()
                .baseUri(zapBaseUrl)
                .queryParam("scanId", scanId)
                .get("/JSON/ascan/view/status/")
                .then().extract().response()
                .jsonPath().getString("status");

            logger.debug("ZAP active scan {} progress: {}%", scanId, status);
            if ("100".equals(status)) {
                logger.info("ZAP active scan {} complete", scanId);
                return;
            }
            sleep(SCAN_POLL_INTERVAL_MS);
        }
        throw new IllegalStateException(
            "ZAP active scan " + scanId + " did not complete within " + timeoutSeconds + " seconds");
    }

    // =========================================================================
    // REPORTING
    // =========================================================================

    /**
     * Downloads the ZAP HTML report and writes it to {@code outputPath}.
     */
    public static void generateHtmlReport(String zapBaseUrl, String outputPath) {
        byte[] reportBytes = RestAssured.given()
            .baseUri(zapBaseUrl)
            .get("/OTHER/core/other/htmlreport/")
            .then().extract().asByteArray();

        try {
            Files.write(Paths.get(outputPath), reportBytes);
            logger.info("ZAP HTML report saved to: {}", Paths.get(outputPath).toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write ZAP report to {}: {}", outputPath, e.getMessage());
        }
    }

    // =========================================================================
    // ALERT ANALYSIS
    // =========================================================================

    /**
     * Returns all ZAP alerts grouped by risk level.
     * Keys: {@code High}, {@code Medium}, {@code Low}, {@code Informational}.
     * Each value is a list of alert names.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> getAlertsByRisk(String zapBaseUrl) {
        Response response = RestAssured.given()
            .baseUri(zapBaseUrl)
            .get("/JSON/alert/view/alertsByRisk/")
            .then().extract().response();

        Map<String, List<String>> result = new HashMap<>();
        List<Map<String, Object>> riskGroups =
            response.jsonPath().getList("alertsByRisk");

        if (riskGroups != null) {
            for (Map<String, Object> group : riskGroups) {
                String risk = (String) group.get("risk");
                List<Map<String, Object>> alerts = (List<Map<String, Object>>) group.get("alerts");
                List<String> names = new ArrayList<>();
                if (alerts != null) {
                    alerts.forEach(a -> names.add((String) a.get("alert")));
                }
                result.put(risk, names);
            }
        }
        return result;
    }

    /**
     * Fails the test if any High-risk alerts are present.
     * Logs each alert name at ERROR level before failing.
     */
    public static void assertNoCriticalAlerts(String zapBaseUrl) {
        Map<String, List<String>> alerts = getAlertsByRisk(zapBaseUrl);
        List<String> highRisk = alerts.getOrDefault("High", List.of());

        if (!highRisk.isEmpty()) {
            highRisk.forEach(name -> logger.error("ZAP High-risk alert: {}", name));
            Assert.fail("ZAP found " + highRisk.size() + " High-risk security alert(s): " + highRisk);
        }
        logger.info("ZAP security check passed — no High-risk alerts found");
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
