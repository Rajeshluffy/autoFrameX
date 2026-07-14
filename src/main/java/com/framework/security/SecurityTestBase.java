package com.framework.security;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.FakerDataFactory;

/**
 * Abstract base class for security-focused test classes.
 *
 * <h3>What this provides</h3>
 * <ul>
 *   <li>Skips WebDriver acquisition — security tests call APIs and ZAP directly.</li>
 *   <li>{@code @BeforeSuite} — verifies ZAP is reachable before any test runs.</li>
 *   <li>{@code @AfterSuite} — generates the ZAP HTML report into {@code reports/zap/}.</li>
 *   <li>Helper methods for passive scan, active scan, alert assertions, and masking.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * {@literal @}TestMetadata(name = "XSS Scan", category = "security")
 * public class TC001_XssScan extends SecurityTestBase {
 *
 *     {@literal @}Test(groups = "security")
 *     public void scanLoginPage() {
 *         startPassiveScan(zapBaseUrl(), "https://myapp.com/login");
 *         assertNoCriticalAlerts();
 *     }
 * }
 * </pre>
 *
 * <h3>ZAP setup</h3>
 * Start ZAP before running the suite:
 * <pre>
 *   zap.sh -daemon -port 8080 -config api.disablekey=true
 * </pre>
 * Override the default URL via system property: {@code -Dzap.base.url=http://localhost:8090}
 */
public abstract class SecurityTestBase extends ProjectSpecificMethods {

    private static final Logger logger = LoggerFactory.getLogger(SecurityTestBase.class);

    private static final String ZAP_URL_PROPERTY  = "zap.base.url";
    private static final String DEFAULT_ZAP_URL   = "http://localhost:8080";
    private static final String ZAP_REPORT_PATH   = "reports/zap/zap-security-report.html";

    // =========================================================================
    // SUITE LIFECYCLE
    // =========================================================================

    /**
     * Verifies ZAP is reachable before any security test runs.
     * Logs a warning (does not fail) if ZAP is not running — individual tests
     * will fail naturally when they try to call the ZAP API.
     */
    @BeforeSuite(alwaysRun = true, dependsOnMethods = "startReport")
    public void startZap() {
        logger.info("=== SecurityTestBase: Verifying ZAP at {} ===", zapBaseUrl());
        try {
            io.restassured.RestAssured.given()
                .baseUri(zapBaseUrl())
                .get("/JSON/core/view/version/")
                .then().statusCode(200);
            logger.info("✓ ZAP is running at {}", zapBaseUrl());
        } catch (Exception e) {
            logger.warn("ZAP not reachable at {} — security tests will fail: {}",
                    zapBaseUrl(), e.getMessage());
        }
    }

    /**
     * Generates the ZAP HTML report after all security tests complete.
     * Report is written to {@code reports/zap/zap-security-report.html}.
     */
    @AfterSuite(alwaysRun = true, dependsOnMethods = "endReport")
    public void generateZapReport() {
        logger.info("=== SecurityTestBase: Generating ZAP report ===");
        try {
            java.io.File dir = new java.io.File("reports/zap");
            if (!dir.exists()) dir.mkdirs();
            ZapSecurityUtils.generateHtmlReport(zapBaseUrl(), ZAP_REPORT_PATH);
            logger.info("✓ ZAP report saved to: {}", ZAP_REPORT_PATH);
        } catch (Exception e) {
            logger.warn("ZAP report generation failed: {}", e.getMessage());
        }
    }

    // =========================================================================
    // METHOD LIFECYCLE — skip WebDriver
    // =========================================================================

    @Override
    @BeforeMethod(alwaysRun = true)
    public void preCondition(Method method, ITestContext context, Object[] parameters) {
        logger.info("SecurityTestBase: skipping driver setup for {}", method.getName());
    }

    @Override
    @AfterMethod(alwaysRun = true)
    public void postCondition(Method method, ITestResult result) {
        FakerDataFactory.cleanup();
        tearDownTest(method, result);
    }

    // =========================================================================
    // SCAN HELPERS
    // =========================================================================

    /**
     * Starts a ZAP passive scan (spider + passive scanners) against {@code targetUrl}.
     */
    protected void startPassiveScan(String targetUrl) {
        ZapSecurityUtils.startPassiveScan(zapBaseUrl(), targetUrl);
    }

    /**
     * Starts a ZAP active scan against {@code targetUrl} and waits up to
     * {@code timeoutSeconds} for it to complete.
     *
     * @return the scan ID
     */
    protected String runActiveScan(String targetUrl, int timeoutSeconds) {
        String scanId = ZapSecurityUtils.startActiveScan(zapBaseUrl(), targetUrl);
        ZapSecurityUtils.waitForActiveScanComplete(zapBaseUrl(), scanId, timeoutSeconds);
        return scanId;
    }

    /**
     * Fails the test if ZAP found any High-risk alerts.
     * Logs each alert name at ERROR level before failing.
     */
    protected void assertNoCriticalAlerts() {
        ZapSecurityUtils.assertNoCriticalAlerts(zapBaseUrl());
    }

    /**
     * Returns all ZAP alerts grouped by risk level.
     * Keys: {@code High}, {@code Medium}, {@code Low}, {@code Informational}.
     */
    protected Map<String, List<String>> getAlertsByRisk() {
        return ZapSecurityUtils.getAlertsByRisk(zapBaseUrl());
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    /**
     * Returns the ZAP base URL, resolved from the {@code zap.base.url} system
     * property with a fallback to {@code http://localhost:8080}.
     */
    protected String zapBaseUrl() {
        String url = System.getProperty(ZAP_URL_PROPERTY, DEFAULT_ZAP_URL);
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
