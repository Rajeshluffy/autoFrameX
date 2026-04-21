package com.framework.utils;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;

import com.framework.testng.api.base.TestMetadata;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.MediaEntityModelProvider;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.ChartLocation;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.framework.config.data.ConfigManager;

import design.patterns.object.pool.DriverPoolManager;

/**
 * Abstract base class for test reporting and framework initialization.
 *
 * KEY FIXES:
 * 1. @BeforeSuite does NOT accept ITestContext - removed parameter injection.
 * 2. @BeforeTest is used as a bridge to extract suite/test parameters from
 * ITestContext (which IS supported there) and pass them to ConfigManager
 * and DriverPoolManager before @BeforeClass runs.
 * 3. Null guards added on 'extent' to prevent NPE if @BeforeSuite fails.
 *
 * Correct TestNG Execution Order:
 * 
 * @BeforeSuite -> initSuite() (no params - sets up report folder only)
 * @BeforeTest -> initFromContext() (ITestContext OK here - inits pool/config)
 * @BeforeClass -> startTestCase() (creates extent test node)
 * @BeforeMethod -> setNode() (creates method node)
 * @Test -> test runs
 * @AfterMethod -> tearDownTest()
 * @AfterSuite -> endReport()
 */
public abstract class Reporter {

    private static final Logger logger = Logger.getLogger(Reporter.class.getName());

    // ExtentReports instances
    private static volatile ExtentReports extent;
    private static final ThreadLocal<ExtentTest> parentTest = new ThreadLocal<>();
    private static final ThreadLocal<ExtentTest> test = new ThreadLocal<>();
    private static final ThreadLocal<String> testName = new ThreadLocal<>();

    // Report configuration
    private static final String FILE_NAME = "result.html";
    private static final String DATE_PATTERN = "dd-MMM-yyyy HH-mm-ss";
    public static volatile String folderName = "";

    // Test metadata (set by child class in @BeforeClass)
    public String testcaseName;
    public String testDescription;
    public String authors;
    public String category;
    public String excelFileName;

    // Driver manager reference
    protected DriverPoolManager driverManager;

    // =========================================================================
    // STEP 1 — @BeforeSuite: NO parameters allowed here by TestNG
    // Only set up the report folder and ExtentReports object.
    // =========================================================================

    /**
     * Initializes the report infrastructure ONLY.
     *
     * CRITICAL: TestNG does NOT support ITestContext injection in @BeforeSuite.
     * Attempting to inject it causes:
     * "Native Injection is NOT supported for @BeforeSuite annotated startReport"
     *
     * Pool and config initialization is deferred to @BeforeTest where
     * ITestContext IS supported.
     */
    @BeforeSuite(alwaysRun = true)
    public synchronized void startReport() {
        logger.info("========================================");
        logger.info("=== @BeforeSuite: Initializing Report ===");
        logger.info("========================================");

        setupReporting();

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

        // Extract parameters from TestNG XML
        ConcurrentMap<String, String> suiteParams = extractSuiteParameters(context);
        logger.info("Suite parameters extracted: " + suiteParams);

        // Initialize configuration (safe to call multiple times - idempotent)
        ConfigManager.getInstance().initializeConfig(suiteParams);
        logger.info("✓ Configuration initialized: "
                + ConfigManager.getInstance().getConfigSummary());

        // Initialize WebDriver pool (safe to call multiple times - idempotent)
        DriverPoolManager.getInstance().initializePool(suiteParams);
        logger.info("✓ WebDriver Pool initialized");

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
        if (extent == null) {
            logger.severe("ExtentReports not initialized - @BeforeSuite may have failed.");
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
            logger.warning("testcaseName not set, defaulting to: " + testcaseName);
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
        if (extent == null) {
            logger.severe("ExtentReports not initialized - skipping node creation.");
            return;
        }

        String methodName = method.getName();
        logger.info("Creating test method node: " + methodName);

        ExtentTest parent = parentTest.get();

        // Fallback: if parent node missing, create a default one
        if (parent == null) {
            logger.warning("Parent node not found for " + methodName + ", creating default.");
            synchronized (extent) {
                String name = (testcaseName != null) ? testcaseName
                        : this.getClass().getSimpleName();
                parent = extent.createTest(name);
                parentTest.set(parent);
            }
        }

        ExtentTest child = parent.createNode(methodName);
        test.set(child);

        logger.fine("✓ Method node created: " + methodName);
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
            logger.warning("No test node - cannot report: " + desc);
            return;
        }

        MediaEntityModelProvider img = null;

        if (bSnap && !status.equalsIgnoreCase("INFO")
                && !status.equalsIgnoreCase("SKIPPED")) {
            long snapNumber = takeSnap();
            try {
                // Determine absolute/relative path correctly
                String imagePathStr = "./" + folderName + "/images/" + snapNumber + ".jpg";
                File imageFile = new File(imagePathStr);

                if (imageFile.exists() && imageFile.length() > 0) {
                    // For ExtentReports, use the relative path it expects for HTML
                    // The HTML file and images folder are stored in the same 'folderName' directory.
                    String reportPath = "./images/" + snapNumber + ".jpg";
                    img = MediaEntityBuilder.createScreenCaptureFromPath(reportPath).build();
                } else {
                    logger.warning("Screenshot not found at " + imageFile.getAbsolutePath() + " - skipping attachment to avoid NPE.");
                    img = null;
                }
            } catch (Exception e) {
                logger.warning("Screenshot entity creation failed: " + e.getMessage());
                img = null; // Ensure img remains null so ExtentReports doesn't crash internally
            }
        }

        synchronized (currentTest) {
            switch (status.toUpperCase()) {
                case "PASS":
                    currentTest.pass(desc, img);
                    break;
                case "FAIL":
                    currentTest.fail(desc, img);
                    throw new RuntimeException("Test failed: " + desc);
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
        test.remove();
        logger.fine("Method completed: " + method.getName());
    }

    /**
     * Flushes the report and shuts down the driver pool.
     * Runs once after all tests complete.
     */
    @AfterSuite(alwaysRun = true)
    public synchronized void endReport() {
        logger.info("=== @AfterSuite: Finalizing ===");

        if (extent != null) {
            synchronized (extent) {
                extent.flush();
            }
            logger.info("✓ Report flushed: " + folderName + "/" + FILE_NAME);
        }

        DriverPoolManager dm = getDriverManager();
        if (dm != null) {
            logger.info("Pool stats:\n" + dm.getPoolStatistics());
            dm.shutdownPool();
            logger.info("✓ Pool shutdown complete");
        }

        cleanupThreadLocals();
        logger.info("=== @AfterSuite Complete ===");
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Sets up the ExtentReports HTML reporter.
     * Creates report and images directories.
     */
    private synchronized void setupReporting() {
        if (extent != null) {
            logger.fine("ExtentReports already initialized, skipping.");
            return;
        }

        String date = new SimpleDateFormat(DATE_PATTERN).format(new Date());
        folderName = "reports/" + date;

        // Create report folder
        File folder = new File("./" + folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // Create images folder
        File images = new File("./" + folderName + "/images");
        if (!images.exists()) {
            images.mkdirs();
        }

        // Configure HTML reporter
        ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter("./" + folderName + "/" + FILE_NAME);
        htmlReporter.config().setTestViewChartLocation(ChartLocation.BOTTOM);
        htmlReporter.config().setChartVisibilityOnOpen(false);
        htmlReporter.config().setTheme(Theme.STANDARD);
        htmlReporter.config().setDocumentTitle("Automation Test Report");
        htmlReporter.config().setEncoding("utf-8");
        htmlReporter.config().setReportName("Automation Test Results");
        htmlReporter.setAppendExisting(false);

        extent = new ExtentReports();
        extent.attachReporter(htmlReporter);

        extent.setSystemInfo("Java Version", System.getProperty("java.version"));
        extent.setSystemInfo("OS", System.getProperty("os.name"));
        extent.setSystemInfo("User", System.getProperty("user.name"));

        logger.info("ExtentReports initialized at: " + folderName);
    }

    /**
     * Extracts all parameters from TestNG suite/test context.
     * Safe to call from @BeforeTest where ITestContext is supported.
     *
     * @param context TestNG context
     * @return thread-safe map of parameters
     */
    private ConcurrentMap<String, String> extractSuiteParameters(ITestContext context) {
        ConcurrentMap<String, String> params = new ConcurrentHashMap<>();

        if (context == null) {
            logger.warning("ITestContext is null, using defaults.");
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

    /** Gets or lazily creates the DriverPoolManager instance. */
    protected DriverPoolManager getDriverManager() {
        if (driverManager == null) {
            driverManager = DriverPoolManager.getInstance();
        }
        return driverManager;
    }

    public String getTestName() {
        return testName.get();
    }

    public Status getTestStatus() {
        ExtentTest parent = parentTest.get();
        return parent != null ? parent.getModel().getStatus() : Status.SKIP;
    }

    public String getReportFolder() {
        return folderName;
    }

    public boolean isReportingInitialized() {
        return extent != null;
    }

    protected void cleanupThreadLocals() {
        parentTest.remove();
        test.remove();
        testName.remove();
    }
}

//
// import java.io.File;
// import java.io.IOException;
// import java.text.SimpleDateFormat;
// import java.util.Date;
// import java.util.logging.Logger;
//
// import org.testng.ITestContext;
// import org.testng.annotations.AfterSuite;
// import org.testng.annotations.BeforeClass;
// import org.testng.annotations.BeforeMethod;
// import org.testng.annotations.BeforeSuite;
//
// import com.aventstack.extentreports.ExtentReports;
// import com.aventstack.extentreports.ExtentTest;
// import com.aventstack.extentreports.MediaEntityBuilder;
// import com.aventstack.extentreports.MediaEntityModelProvider;
// import com.aventstack.extentreports.Status;
// import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
// import com.aventstack.extentreports.reporter.configuration.ChartLocation;
// import com.aventstack.extentreports.reporter.configuration.Theme;
//
// import design.patterns.factory.browser.BrowserFactory;
// import design.patterns.factory.browser.WebDriverFactoryInterface;
// import design.patterns.object.pool.DriverPoolManager;
// import design.patterns.object.pool.WebDriverPoolFactory;
//
// public abstract class Reporter extends DriverPoolManager {
//
//
//
// /**
// * Initializes and returns a new WebDriverPoolFactory instance using this
// factory.
// *
// * @return a WebDriverPoolFactory instance
// */
// public WebDriverPoolFactory createWebDriverPool() {
//
// return null;
//
// }
//
//
//
// private static final Logger logger =
// Logger.getLogger(DriverPoolManager.class.getName());
// private static ExtentReports extent;
// private static final ThreadLocal<ExtentTest> parentTest = new
// ThreadLocal<>();
// private static final ThreadLocal<ExtentTest> test = new ThreadLocal<>();
// private static final ThreadLocal<String> testName = new ThreadLocal<>();
//
// private static final String fileName = "result.html";
// private static final String pattern = "dd-MMM-yyyy HH-mm-ss";
//
// public String testcaseName, testDescription, authors, category,
// excelFileName;
// public static String folderName = "";
//
// protected static WebDriverPoolFactory createWebDriverPool = null;
//
// @BeforeClass(alwaysRun = true)
// public static void initializePoolFactory(ITestContext context) {
// initializePoolFactory(context);
// }
//
// @BeforeSuite(alwaysRun = true)
// public synchronized void startReport() {
// String date = new SimpleDateFormat(pattern).format(new Date());
// folderName = "reports/" + date;
//
// File folder = new File("./" + folderName);
// if (!folder.exists()) {
// folder.mkdirs();
// }
//
// ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter("./" + folderName +
// "/" + fileName);
// htmlReporter.config().setTestViewChartLocation(ChartLocation.BOTTOM);
// htmlReporter.config().setChartVisibilityOnOpen(false);
// htmlReporter.config().setTheme(Theme.STANDARD);
// htmlReporter.config().setDocumentTitle("Automation Report");
// htmlReporter.config().setEncoding("utf-8");
// htmlReporter.config().setReportName("Automation Results");
// htmlReporter.setAppendExisting(false);
//
// extent = new ExtentReports();
// extent.attachReporter(htmlReporter);
// }
//
// @BeforeClass(alwaysRun = true)
// public void startTestCase() {
// ExtentTest parent = extent.createTest(testcaseName,
// testDescription).assignAuthor(authors)
// .assignCategory(category);
// parentTest.set(parent);
// testName.set(testcaseName);
// }
//
// @BeforeMethod(alwaysRun = true)
// public void setNode() {
// ExtentTest child = parentTest.get().createNode(getTestName());
// test.set(child);
// }
//
// public abstract long takeSnap();
//
// public void reportStep(String desc, String status, boolean bSnap) {
// synchronized (test) {
// MediaEntityModelProvider img = null;
// if (bSnap && !(status.equalsIgnoreCase("INFO") ||
// status.equalsIgnoreCase("SKIPPED"))) {
// long snapNumber = takeSnap();
// try {
// String path = "./../../" + folderName + "/images/" + snapNumber + ".jpg";
// img = MediaEntityBuilder.createScreenCaptureFromPath(path).build();
// } catch (IOException e) {
// e.printStackTrace();
// }
// }
//
// switch (status.toUpperCase()) {
// case "PASS":
// test.get().pass(desc, img);
// break;
// case "FAIL":
// test.get().fail(desc, img);
// throw new RuntimeException("Test failed: See report for details.");
// case "WARNING":
// test.get().warning(desc, img);
// break;
// case "SKIPPED":
// test.get().skip("Skipped: " + desc);
// break;
// case "INFO":
// test.get().info(desc);
// break;
// default:
// test.get().info(desc);
// break;
// }
// }
// }
//
// public void reportStep(String desc, String status) {
// reportStep(desc, status, true);
// }
//
// @AfterSuite(alwaysRun = true)
// public synchronized void endReport() {
// if (extent != null) {
// extent.flush();
// }
//
//// closeBrowserPool(context);
// }
//
// /**
// * Final cleanup - close all drivers
// */
// // @AfterSuite(alwaysRun = true,dependsOnMethods = "endReport")
// public static void closeBrowserPool(ITestContext context) {
// if (poolFactory != null) {
// logger.info("Shutting down WebDriver Pool for test suite: " +
// context.getSuite().getName()); logger.info("Final Pool Statistics:\n" +
// poolFactory.getPoolStatistics());
//
// poolFactory.close(); poolFactory = null;
//
// logger.info("WebDriver Pool shutdown complete"); } }
//
//
// public String getTestName() {
// return testName.get();
// }
//
// public Status getTestStatus() {
// return parentTest.get().getModel().getStatus();
// }
//
// }