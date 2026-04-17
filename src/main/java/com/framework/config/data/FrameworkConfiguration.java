package com.framework.config.data;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;

/**
 * Owner-backed interface for framework-level configuration.
 *
 * <p><b>Scope:</b> This interface covers settings that are the SAME across every
 * project — browser type, wait times, retry limits, and the driver pool.
 * It deliberately contains NO project-specific URL or credential keys;
 * those belong in each project's own {@link ProjectAppConfiguration} implementation.
 *
 * <p><b>Source:</b> All keys are read from {@code frameworkConfig.properties},
 * which is shared by every project. {@code @DefaultValue} annotations make the
 * interface self-contained even when the file is absent (e.g. unit tests).
 *
 * <p><b>Design pattern — Bridge (Structural):</b>
 * {@code FrameworkConfiguration} is the stable abstraction layer. Project
 * configurations extend it, varying the "implementation" (which property file
 * to load) without changing this contract.
 */
@LoadPolicy(LoadType.MERGE)
@Config.Sources({
    "classpath:frameworkConfig.properties"
})
public interface FrameworkConfiguration extends Config {

    // ------------------------------------------------------------------
    // Browser
    // ------------------------------------------------------------------

    /** Browser to launch. Matches a {@code BrowserType} enum name (case-insensitive). */
    @Key("autoFrameX.browser.name")
    @DefaultValue("chrome")
    String browserType();

    /** {@code true} launches the browser without a visible UI (CI-friendly). */
    @Key("autoFrameX.browser.isheadless")
    @DefaultValue("false")
    boolean headless();

    // ------------------------------------------------------------------
    // Wait Times
    // ------------------------------------------------------------------

    /** Global implicit wait applied at the WebDriver level (seconds). */
    @Key("autoFrameX.implicit.wait.time")
    @DefaultValue("10")
    short implicit();

    /** Default explicit / fluent wait timeout (seconds). */
    @Key("autoFrameX.explicit.wait.time")
    @DefaultValue("20")
    short explicit();

    /** Short wait used for alert and disappearance checks (seconds). */
    @Key("autoFrameX.short.wait.time")
    @DefaultValue("5")
    short shortWait();

    /** Fluent-wait polling interval (milliseconds). */
    @Key("autoFrameX.polling.interval.ms")
    @DefaultValue("500")
    int pollingIntervalMs();

    /** Fallback {@code WebDriverWait} timeout in the pool and test base (seconds). */
    @Key("autoFrameX.default.wait.timeout")
    @DefaultValue("15")
    int defaultWaitTimeout();

    /** Page-load timeout applied to every new WebDriver instance (seconds). */
    @Key("autoFrameX.page.load.timeout")
    @DefaultValue("30")
    int pageLoadTimeout();

    /** JavaScript execution timeout (seconds). */
    @Key("autoFrameX.script.load.timeout")
    @DefaultValue("30")
    int scriptTimeout();

    // ------------------------------------------------------------------
    // Retry
    // ------------------------------------------------------------------

    /** Maximum TestNG-level retries per failing test (used by {@code RetryEngine}). */
    @Key("autoFrameX.test.retry.max.limit")
    @DefaultValue("4")
    short retry();

    /** Maximum element-level interaction retries inside {@code SeleniumBase}. */
    @Key("autoFrameX.element.max.retry.attempts")
    @DefaultValue("3")
    int elementMaxRetryAttempts();

    // ------------------------------------------------------------------
    // WebDriver Object Pool
    // ------------------------------------------------------------------

    /** Maximum number of WebDriver instances kept alive in the pool. */
    @Key("autoFrameX.pool.max.size")
    @DefaultValue("5")
    int poolMaxSize();

    /** Minutes an idle driver stays in the pool before being evicted. */
    @Key("autoFrameX.pool.max.idle.minutes")
    @DefaultValue("10")
    int poolMaxIdleMinutes();

    /**
     * Minimum drivers pre-warmed per browser type at pool startup.
     * Eliminates cold-start latency for the first test in each suite.
     */
    @Key("autoFrameX.pool.min.size")
    @DefaultValue("2")
    int poolMinSize();

    /**
     * Maximum seconds a borrow attempt blocks waiting for an available driver
     * before throwing {@code DriverAcquisitionException}.
     * Prevents test threads from hanging indefinitely when the pool is exhausted.
     */
    @Key("autoFrameX.pool.borrow.timeout.seconds")
    @DefaultValue("30")
    int poolBorrowTimeoutSeconds();

    /**
     * Maximum number of test uses per driver before it is automatically retired.
     * Chrome and Firefox accumulate memory and file-descriptor leaks per page load;
     * enforcing this cap keeps browser health stable across large suites.
     */
    @Key("autoFrameX.pool.max.reuse.count")
    @DefaultValue("75")
    int poolMaxReuseCount();

    /**
     * When {@code true} (default) the WebDriver is fully quit after every
     * {@code @Test} method — pass or fail — guaranteeing a clean browser for the
     * next test.  Set to {@code false} to return healthy drivers to the pool for
     * reuse and improve execution speed in large suites.
     */
    @Key("autoFrameX.pool.close.after.each")
    @DefaultValue("true")
    boolean closeAfterEach();
}
