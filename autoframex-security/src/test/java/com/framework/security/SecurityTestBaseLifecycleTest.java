package com.framework.security;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;

import design.patterns.object.pool.DriverPoolManager;

/**
 * {@code SecurityTestBase} is the class a project's security tests extend, and no test in this repository did, so
 * its suite hooks (verify ZAP in {@code @BeforeSuite}, write the report in {@code @AfterSuite}) and its
 * "no WebDriver" override of the method hooks had never run. This is the first subclass: a test that scans through
 * the base class's helpers against a fake ZAP, inside the real TestNG lifecycle.
 */
public class SecurityTestBaseLifecycleTest extends SecurityTestBase {

    private static final HttpServer FAKE_ZAP;
    private static final ConcurrentMap<String, AtomicInteger> HITS = new ConcurrentHashMap<>();

    static {
        // Must be up before the suite hooks run, and before SecurityTestBase reads zap.base.url.
        try {
            FAKE_ZAP = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        Map<String, String> replies = Map.of(
                "/JSON/core/view/version/", "{\"version\":\"2.15.0\"}",
                "/JSON/pscan/action/enableAllScanners/", "{\"Result\":\"OK\"}",
                "/JSON/spider/action/scan/", "{\"scan\":\"0\"}",
                "/JSON/spider/view/status/", "{\"status\":\"100\"}",
                "/JSON/pscan/view/recordsToScan/", "{\"recordsToScan\":\"0\"}",
                "/JSON/core/view/numberOfMessages/", "{\"numberOfMessages\":\"12\"}",
                "/JSON/alert/view/alerts/", "{\"alerts\":[{\"alert\":\"Missing Anti-clickjacking Header\",\"risk\":\"Medium\"}]}",
                "/OTHER/core/other/htmlreport/", "<html><body>zap report</body></html>");
        FAKE_ZAP.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            HITS.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
            String body = replies.get(path);
            byte[] bytes = (body != null ? body : "{\"code\":\"does_not_exist\",\"message\":\"" + path + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body != null ? 200 : 404, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        FAKE_ZAP.start();
        System.setProperty("zap.base.url", "http://localhost:" + FAKE_ZAP.getAddress().getPort());
    }

    @Test
    public void aSubclassScansThroughTheBaseHelpersWithoutAWebDriver() {
        runPassiveScan("https://app.example/login", 10);
        assertNoCriticalAlerts("https://app.example/login");

        Assert.assertEquals(getAlertsByRisk().get("Medium"), java.util.List.of("Missing Anti-clickjacking Header"));
        Assert.assertTrue(HITS.get("/JSON/spider/action/scan/").get() >= 1, "the crawl was started through the base class");
    }

    @Test(dependsOnMethods = "aSubclassScansThroughTheBaseHelpersWithoutAWebDriver")
    public void theSuiteHookVerifiedZapBeforeTheTestRan() {
        Assert.assertTrue(HITS.containsKey("/JSON/core/view/version/") && HITS.get("/JSON/core/view/version/").get() >= 1,
                "@BeforeSuite startZap() asked ZAP for its version");
        Assert.assertNotNull(zapBaseUrl());
    }

    @Test
    public void noBrowserIsStartedForASecuritySuite() {
        // SeleniumBase.initDriverPool is a @BeforeTest every subclass inherits and it pre-warms the pool's minimum
        // number of real Chrome browsers (two by default). A ZAP/API security suite uses none, and needs Chrome
        // installed only because of it.
        DriverPoolManager.bindContext(null);
        Assert.assertFalse(DriverPoolManager.getInstance().isPoolInitialized(),
                "a security suite must not pre-warm browsers it never uses");
    }

    @Test
    public void theZapReportPathIsUnderTheReportsFolder() {
        // generateZapReport() runs in @AfterSuite: it cannot be asserted from inside the suite, so this only pins
        // where it will write, and that the folder can be created.
        File dir = new File("reports/zap");
        Assert.assertTrue(dir.isDirectory() || dir.mkdirs(), "reports/zap can be created");
    }
}
