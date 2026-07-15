package com.framework.utils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.model.Media;
import com.framework.config.data.ConfigManager;

import com.framework.observability.CorrelationContext;
import com.framework.observability.FailureCategorizer;
import com.framework.observability.FlakyTestTracker;
import com.framework.observability.ResourceUsageAccumulator;
import com.framework.observability.TestEvent;
import com.framework.observability.TestEventCollector;

/**
 * Abstract base class for test reporting and framework initialization.
 *
 * KEY FIXES:
 * 1. @BeforeSuite does NOT accept ITestContext - removed parameter injection.
 * 2. @BeforeTest is used as a bridge to extract suite/test parameters from
 * ITestContext (which IS supported there) and pass them to ConfigManager
 * before @BeforeClass runs.
 * 3. Null guards added on 'extent' to prevent NPE if @BeforeSuite fails.
 *
 * <p>Deliberately has no compile-time dependency on the WebDriver pool
 * (design.patterns.object.pool) — this class lives in the framework's core
 * module and must stay usable by non-UI (API-only) consumers (TD-20's
 * multi-module split). {@code SeleniumBase} (which extends this class, in the
 * selenium module) has its own @BeforeTest/@AfterSuite hooks for pool
 * bind/init/shutdown — see that class.
 *
 * Correct TestNG Execution Order:
 *
 * @BeforeSuite -> initSuite() (no params - sets up report folder only)
 * @BeforeTest -> initFromContext() (ITestContext OK here - inits config)
 * @BeforeClass -> startTestCase() (creates extent test node)
 * @BeforeMethod -> setNode() (creates method node)
 * @Test -> test runs
 * @AfterMethod -> tearDownTest()
 * @AfterSuite -> endReport()
 */
public abstract class Reporter {

    private static final Logger logger = LoggerFactory.getLogger(Reporter.class);

    // ExtentTest node tracking (per-thread) — the ExtentReports instance itself
    // and the report folder/file naming live in ExtentReportManager (TD-07).
    private static final ThreadLocal<ExtentTest> parentTest = new ThreadLocal<>();
    private static final ThreadLocal<ExtentTest> test = new ThreadLocal<>();
    private static final ThreadLocal<String> testName = new ThreadLocal<>();

    // Test metadata (set by child class in @BeforeClass)
    public String testcaseName;
    public String testDescription;
    public String authors;
    public String category;
    public String excelFileName;

    // Observability — per-test start time (ThreadLocal, matches pattern of other TLs)
    private static final ThreadLocal<Long> testStartTimeMs = new ThreadLocal<>();

    // =========================================================================
    // STEP 1 — @BeforeSuite: NO parameters allowed here by TestNG
    // Only set up the report folder and ExtentReports object.
    // =========================================================================

    /**
     * Initializes logging/event infrastructure ONLY.
     *
     * CRITICAL: TestNG does NOT support ITestContext injection in @BeforeSuite.
     * Attempting to inject it causes:
     * "Native Injection is NOT supported for @BeforeSuite annotated startReport"
     *
     * The suite name is required to name the report file, so the report
     * folder/file itself is created in @BeforeTest (see {@link #initFromContext}),
     * where ITestContext IS supported.
     */
    @BeforeSuite(alwaysRun = true)
    public synchronized void startReport() {
        // Route any remaining java.util.logging calls (Selenium internals, etc.) through Logback
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        TestEventCollector.getInstance().init("logs");

        logger.info("========================================");
        logger.info("=== @BeforeSuite: Initializing Report ===");
        logger.info("========================================");
        logger.info("=== @BeforeSuite Complete ===");
    }

    // =========================================================================
    // STEP 2 — @BeforeTest: ITestContext IS supported here.
    // Initialize config and driver pool with suite parameters.
    // =========================================================================

    /**
     * Initializes configuration and WebDriver pool using TestNG parameters.
     *
     * ITestContext is fully supported in @BeforeTest and gives access to:
     * - Suite-level parameters from testng.xml
     * - Test-level parameters from testng.xml
     *
     * This runs AFTER @BeforeSuite and BEFORE @BeforeClass, making it the
     * correct place to set up the pool that @BeforeClass depends on.
     *
     * @param context TestNG test context (auto-injected - supported here)
     */
    @BeforeTest(alwaysRun = true)
    public synchronized void initFromContext(ITestContext context) {
        logger.info("=== @BeforeTest: Initializing Pool & Config ===");

        String suiteName = (context != null && context.getSuite() != null)
                ? context.getSuite().getName() : null;
        ExtentReportManager.initReportInfrastructure(suiteName);

        // Extract parameters from TestNG XML
        ConcurrentMap<String, String> suiteParams = extractSuiteParameters(context);
        logger.info("Suite parameters extracted: " + suiteParams);

        // Bind this (suite-initiating) thread to its config context before
        // touching the singleton-turned-registry. @BeforeMethod re-binds again
        // per test method, since parallel="methods"/"classes" run methods on
        // different worker threads than the one that ran this @BeforeTest.
        //
        // NOTE: the WebDriver pool (design.patterns.object.pool.DriverPoolManager)
        // is deliberately NOT touched here — Reporter lives in the framework's
        // core module and must not have a compile-time dependency on the selenium
        // module (TD-20's multi-module split). Pool bind/init happens in
        // SeleniumBase's own @BeforeTest hook instead (selenium module), which
        // runs after this one since SeleniumBase extends Reporter.
        String contextId = ConfigManager.resolveContextId(suiteParams);
        ConfigManager.bindContext(contextId);

        // Initialize configuration (safe to call multiple times - idempotent)
        ConfigManager.getInstance().initializeConfig(suiteParams);
        logger.info("✓ Configuration initialized: "
                + ConfigManager.getInstance().getConfigSummary());

        logger.info("=== @BeforeTest Complete ===");
    }

    // =========================================================================
    // STEP 3 — @BeforeClass: extent is guaranteed non-null here.
    // Create the test case node for reporting.
    // =========================================================================

    /**
     * Creates a test case node in ExtentReports.
     * Runs once per test class, AFTER @BeforeTest.
     *
     * Null guard on 'extent' prevents NPE if report setup failed.
     */
    @BeforeClass(alwaysRun = true)
    public void startTestCase() {
        // Guard: if extent failed to initialize, log and skip
        ExtentReports extent = ExtentReportManager.getExtent();
        if (extent == null) {
            logger.error("ExtentReports not initialized - @BeforeSuite may have failed.");
            return;
        }

        // @TestMetadata annotation takes precedence over legacy setValues() fields
        TestMetadata meta = this.getClass().getAnnotation(TestMetadata.class);
        if (meta != null) {
            testcaseName   = meta.name();
            testDescription = meta.description();
            authors        = meta.authors();
            category       = meta.category();
            if (!meta.excelFile().isEmpty()) {
                excelFileName = meta.excelFile();
            }
            logger.info("TestMetadata annotation found on " + this.getClass().getSimpleName());
        }

        // Final fallback: if neither annotation nor setValues() provided a name
        if (testcaseName == null || testcaseName.isEmpty()) {
            testcaseName = this.getClass().getSimpleName();
            logger.warn("testcaseName not set, defaulting to: " + testcaseName);
        }

        logger.info("Creating test case node: " + testcaseName);

        synchronized (extent) {
            ExtentTest parent = extent
                    .createTest(testcaseName, testDescription)
                    .assignAuthor(authors)
                    .assignCategory(category);

            parentTest.set(parent);
            testName.set(testcaseName);
        }

        logger.info("✓ Test case node created: " + testcaseName);
    }

    // =========================================================================
    // STEP 4 — @BeforeMethod: ITestContext IS supported here too.
    // Create the test method node for reporting.
    // =========================================================================

    /**
     * Creates a test method node in ExtentReports.
     * Runs once per test method.
     *
     * @param method  test method being executed (auto-injected)
     * @param context TestNG context (auto-injected - supported in @BeforeMethod)
     */
    @BeforeMethod(alwaysRun = true)
    public void setNode(Method method, ITestContext context) {
        // Guard: if extent failed to initialize, skip node creation
        ExtentReports extent = ExtentReportManager.getExtent();
        if (extent == null) {
            logger.error("ExtentReports not initialized - skipping node creation.");
            return;
        }

        String methodName = method.getName();
        logger.info("Creating test method node: " + methodName);

        ExtentTest parent = parentTest.get();

        // Fallback: if parent node missing, create a default one
        if (parent == null) {
            logger.warn("Parent node not found for " + methodName + ", creating default.");
            synchronized (extent) {
                String name = (testcaseName != null) ? testcaseName
                        : this.getClass().getSimpleName();
                parent = extent.createTest(name);
                parentTest.set(parent);
            }
        }

        ExtentTest child = parent.createNode(methodName);
        test.set(child);

        CorrelationContext.init();
        testStartTimeMs.set(System.currentTimeMillis());

        logger.debug("✓ Method node created: " + methodName);
    }

    // =========================================================================
    // REPORTING
    // =========================================================================

    /**
     * Reports a test step with status and optional screenshot.
     *
     * @param desc   step description
     * @param status PASS | FAIL | WARNING | SKIPPED | INFO
     * @param bSnap  whether to capture a screenshot
     */
    public void reportStep(String desc, String status, boolean bSnap) {
        ExtentTest currentTest = test.get();

        if (currentTest == null) {
            logger.warn("No test node - cannot report: " + desc);
            return;
        }

        // ExtentReports 5.x: MediaEntityBuilder.createScreenCaptureFromPath().build()
        // returns Media directly (not a provider wrapper). The v3 getMedia()==null bug
        // no longer exists; null-safety is sufficient.
        Media img = null;

        if (bSnap && !status.equalsIgnoreCase("INFO")
                && !status.equalsIgnoreCase("SKIPPED")) {
            long snapNumber = takeSnap();
            try {
                String imagePathStr = "./" + ExtentReportManager.folderName + "/images/" + snapNumber + ".jpg";
                File imageFile = new File(imagePathStr);

                if (imageFile.exists() && imageFile.length() > 0) {
                    // Relative path: HTML report and images/ folder share the same parent.
                    String reportPath = "./images/" + snapNumber + ".jpg";
                    img = MediaEntityBuilder.createScreenCaptureFromPath(reportPath).build();
                } else {
                    logger.warn("Screenshot not found at " + imageFile.getAbsolutePath()
                            + " — screenshot attachment skipped.");
                }
            } catch (Exception e) {
                logger.warn("Screenshot entity creation failed: " + e.getMessage());
            }
        }

        synchronized (currentTest) {
            switch (status.toUpperCase()) {
                case "PASS":
                    currentTest.pass(desc, img);
                    break;
                case "FAIL":
                    currentTest.fail(desc, img);
                    throw new com.framework.exception.TestStepFailedException("Test failed: " + desc);
                case "WARNING":
                    currentTest.warning(desc, img);
                    break;
                case "SKIPPED":
                    currentTest.skip("Skipped: " + desc);
                    break;
                case "INFO":
                default:
                    currentTest.info(desc);
                    break;
            }
        }
    }

    /** Reports a step with screenshot enabled by default. */
    public void reportStep(String desc, String status) {
        reportStep(desc, status, true);
    }

    // =========================================================================
    // TEARDOWN
    // =========================================================================

    /**
     * Cleans up the thread-local test node after each method.
     *
     * @param method test method that completed
     * @param result test result
     */
    @AfterMethod(alwaysRun = true)
    public void tearDownTest(Method method, ITestResult result) {
        // --- Observability: build and publish test event ---
        try {
            long startMs = testStartTimeMs.get() != null ? testStartTimeMs.get() : System.currentTimeMillis();
            long endMs   = System.currentTimeMillis();
            String testName = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
            boolean passed  = result.isSuccess();
            int     status  = result.getStatus();

            FlakyTestTracker.getInstance().record(testName, passed);

            CorrelationContext.Context ctx = CorrelationContext.get();
            TestEvent event = TestEvent.builder()
                .testId(testName)
                .suite(result.getTestContext().getSuite().getName())
                .feature(testcaseName != null ? testcaseName : "")
                .severity("")
                .owner(authors != null ? authors : "")
                .environment(ctx.getEnvironmentId())
                .browser(ConfigManager.getInstance().getConfig().getBrowserName())
                .startTimeMs(startMs)
                .endTimeMs(endMs)
                .durationMs(endMs - startMs)
                .status(passed ? "PASS" : status == ITestResult.SKIP ? "SKIP" : "FAIL")
                .failureCategory(FailureCategorizer.categorize(result.getThrowable()))
                .failureMessage(result.getThrowable() != null ? result.getThrowable().getMessage() : null)
                .retryCount(result.getMethod().getCurrentInvocationCount() - 1)
                .flakyScore(FlakyTestTracker.getInstance().getFlakyScore(testName))
                .traceId(ctx.getTraceId())
                .buildId(ctx.getBuildId())
                .gitCommit(ctx.getCommitId())
                .containerId(ctx.getContainerId())
                .executionId(ctx.getExecutionId())
                .sessionId(ctx.getSessionId())
                .threadId(ctx.getThreadId())
                .resourceUsage(ResourceUsageAccumulator.drain())
                .build();

            TestEventCollector.getInstance().publish(event);
        } catch (Exception e) {
            logger.warn("Failed to publish test event for {}: {}", method.getName(), e.getMessage());
        } finally {
            CorrelationContext.clear();
            testStartTimeMs.remove();
        }

        test.remove();
        logger.debug("Method completed: " + method.getName());
    }

    /**
     * Flushes the report and shuts down the driver pool.
     * Runs once after all tests complete.
     */
    @AfterSuite(alwaysRun = true)
    public synchronized void endReport() {
        logger.info("=== @AfterSuite: Finalizing ===");

        TestEventCollector.getInstance().flush();
        logger.info("Flaky test summary: {}", FlakyTestTracker.getInstance().getSummary());

        ExtentReports extent = ExtentReportManager.getExtent();
        if (extent != null) {
            synchronized (extent) {
                FlakyTestTracker.getInstance().getSummary().forEach((name, score) ->
                    extent.setSystemInfo("Flaky: " + name,
                            String.format("%.0f%% failure rate", score * 100)));
                extent.flush();
            }
            logger.info("✓ Report flushed: " + ExtentReportManager.getReportFolder() + "/"
                    + ExtentReportManager.getReportFileName());
        }

        FlakyTestTracker.getInstance().reset();

        // WebDriver pool shutdown deliberately NOT done here — see the note in
        // initFromContext(). SeleniumBase's own @AfterSuite hook handles it
        // (selenium module); TestNG runs @AfterSuite in reverse-hierarchy order,
        // so SeleniumBase's runs before this one.

        cleanupThreadLocals();
        logger.info("=== @AfterSuite Complete ===");
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Extracts all parameters from TestNG suite/test context.
     * Safe to call from @BeforeTest or @BeforeMethod, both of which support
     * ITestContext injection — subclasses reuse this to re-resolve the same
     * config/pool context id on worker threads (see {@link ConfigManager#resolveContextId}).
     *
     * @param context TestNG context
     * @return thread-safe map of parameters
     */
    protected ConcurrentMap<String, String> extractSuiteParameters(ITestContext context) {
        ConcurrentMap<String, String> params = new ConcurrentHashMap<>();

        if (context == null) {
            logger.warn("ITestContext is null, using defaults.");
            return params;
        }

        // Suite-level parameters
        if (context.getSuite() != null
                && context.getSuite().getXmlSuite() != null) {
            context.getSuite().getXmlSuite().getAllParameters()
                    .forEach(params::put);
        }

        // Test-level parameters (override suite params)
        if (context.getCurrentXmlTest() != null) {
            context.getCurrentXmlTest().getAllParameters()
                    .forEach(params::put);
        }

        params.put("suiteName", context.getSuite().getName());
        params.put("testName", context.getName());

        return params;
    }

    // =========================================================================
    // ABSTRACT & UTILITY
    // =========================================================================

    /** Takes a screenshot. Implemented by SeleniumBase. */
    public abstract long takeSnap();

    public String getTestName() {
        return testName.get();
    }

    public Status getTestStatus() {
        ExtentTest parent = parentTest.get();
        return parent != null ? parent.getModel().getStatus() : Status.SKIP;
    }

    public String getReportFolder() {
        return ExtentReportManager.getReportFolder();
    }

    /** Suite-name-based HTML report file name (e.g. {@code MySuite.html}), resolved by {@link ExtentReportManager#initReportInfrastructure}. */
    public static String getReportFileName() {
        return ExtentReportManager.getReportFileName();
    }

    public boolean isReportingInitialized() {
        return ExtentReportManager.getExtent() != null;
    }

    protected void cleanupThreadLocals() {
        parentTest.remove();
        test.remove();
        testName.remove();
        testStartTimeMs.remove();
        ResourceUsageAccumulator.clear();
    }
}

