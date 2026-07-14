package com.framework.config.data;

import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Director in the Builder pattern — constructs a fully resolved {@link ProjectConfig}
 * from a single {@link ProjectAppConfiguration} instance.
 *
 * <h3>How it works</h3>
 * <p>Because every concrete config (e.g. {@code LeafTapConfiguration},
 * {@code AlfaDockConfiguration}) now extends {@link ProjectAppConfiguration}, which
 * in turn extends {@link FrameworkConfiguration}, one Owner proxy loaded with
 * {@code @LoadPolicy(LoadType.MERGE)} across <em>both</em> property files covers the
 * complete configuration surface:
 * <pre>
 *   frameworkConfig.properties  ──► browser, wait, retry, pool
 *   &lt;project&gt;Config.properties  ──► URL, credentials, DB
 *                 ↕ merged by Owner into one proxy
 *          ProjectAppConfiguration cfg   (single object)
 * </pre>
 * {@code ProjectDirector} no longer needs two separate config objects.
 *
 * <h3>Configuration priority (highest → lowest)</h3>
 * <ol>
 *   <li>TestNG Suite/Test parameters ({@code <parameter>} in XML)</li>
 *   <li>CI/CD environment variables ({@code BROWSER}, {@code BASE_URL}, etc.)</li>
 *   <li>JVM system properties ({@code -Dbrowser=chrome})</li>
 *   <li>Project properties file ({@code alfaDOCKConfig.properties}, etc.)</li>
 *   <li>Framework properties file ({@code frameworkConfig.properties})</li>
 *   <li>Owner {@code @DefaultValue} annotations</li>
 * </ol>
 *
 * <h3>Multi-project support</h3>
 * <p>Declare the project's config class in the TestNG XML:
 * <pre>
 * &lt;parameter name="configClass"
 *            value="com.alfaDOCK.config.data.AlfaDockConfiguration"/&gt;
 * &lt;parameter name="environment" value="dev"/&gt;
 * </pre>
 *
 * @author Framework Team
 * @version 5.0 — unified single-config architecture
 */
public class ProjectDirector {

    private static final Logger logger = LoggerFactory.getLogger(ProjectDirector.class);

    /**
     * Fallback when no {@code configClass} parameter is present in the TestNG XML.
     *
     * <p>Points to {@link AutoFrameXConfiguration} — the framework's own built-in
     * implementation that loads sensible defaults from {@code frameworkConfig.properties}.
     * Projects that run real applications should override this via their TestNG XML:
     * <pre>
     * &lt;parameter name="configClass" value="com.myapp.config.MyAppConfig"/&gt;
     * </pre>
     */
    private static final String DEFAULT_CONFIG_CLASS =
            "com.framework.config.data.AutoFrameXConfiguration";

    // =========================================================================
    // Public API
    // =========================================================================

    /** Constructs {@link ProjectConfig} without TestNG parameters. */
    public static ProjectConfig construct() {
        return construct(null);
    }

    /**
     * Constructs {@link ProjectConfig} with TestNG parameters taking highest priority.
     *
     * @param testngParams parameters from the TestNG XML (may be null)
     * @return fully resolved, immutable {@link ProjectConfig}
     */
    public static ProjectConfig construct(ConcurrentMap<String, String> testngParams) {
        logger.info("Constructing ProjectConfig...");

        // Single unified config — covers framework + project settings via Owner MERGE
        ProjectAppConfiguration cfg = loadProjectConfig(testngParams);

        ProjectConfigBuilder builder = new ProjectConfigBuilder();

        // ── Browser ──────────────────────────────────────────────────────────
        builder.setBrowserName(
                resolve("browser",           testngParams, "BROWSER",          "browser",          cfg.browserType()));
        builder.setRemote(
                resolveBoolean("headless",   testngParams, "HEADLESS",         "headless",         cfg.headless()));

        // ── Application URL ───────────────────────────────────────────────────
        String appUrl = resolveUrl(testngParams, cfg);
        builder.setAppUrl(appUrl);
        builder.setDevUrl(cfg.devUrl());
        builder.setQaUrl(cfg.qaUrl());
        logger.info("Resolved URL → " + appUrl);

        // ── Timeouts ─────────────────────────────────────────────────────────
        builder.setImplicit(
                resolveShort("implicitWait",        testngParams, "IMPLICIT_WAIT",    "implicitWait",        cfg.implicit()));
        builder.setExplicit(
                resolveShort("explicitWait",        testngParams, "EXPLICIT_WAIT",    "explicitWait",        cfg.explicit()));
        builder.setShortWait(
                resolveShort("shortWait",           testngParams, "SHORT_WAIT",       "shortWait",           cfg.shortWait()));
        builder.setPollingIntervalMs(
                resolveInt("pollingIntervalMs",     testngParams, "POLLING_INTERVAL", "pollingIntervalMs",   cfg.pollingIntervalMs()));
        builder.setDefaultWaitTimeout(
                resolveInt("defaultWaitTimeout",    testngParams, "DEFAULT_TIMEOUT",  "defaultWaitTimeout",  cfg.defaultWaitTimeout()));
        builder.setPageLoadTimeout(
                resolveInt("pageLoadTimeout",       testngParams, "PAGE_LOAD_TIMEOUT","pageLoadTimeout",     cfg.pageLoadTimeout()));
        builder.setScriptTimeout(
                resolveInt("scriptTimeout",         testngParams, "SCRIPT_TIMEOUT",   "scriptTimeout",       cfg.scriptTimeout()));

        // ── Retry ─────────────────────────────────────────────────────────────
        builder.setRetryLimit(
                resolveShort("retryLimit",          testngParams, "RETRY_LIMIT",      "retryLimit",          cfg.retry()));
        builder.setElementMaxRetryAttempts(
                resolveInt("elementMaxRetryAttempts",testngParams,"ELEMENT_MAX_RETRY","elementMaxRetryAttempts",cfg.elementMaxRetryAttempts()));

        // ── Pool ──────────────────────────────────────────────────────────────
        builder.setPoolMaxSize(
                resolveInt("poolMaxSize",             testngParams, "MAX_POOL_SIZE",    "poolMaxSize",             cfg.poolMaxSize()));
        builder.setPoolMaxIdleMinutes(
                resolveInt("poolMaxIdleMinutes",      testngParams, "MAX_IDLE_MINUTES", "poolMaxIdleMinutes",      cfg.poolMaxIdleMinutes()));
        builder.setPoolMinSize(
                resolveInt("poolMinSize",             testngParams, "MIN_POOL_SIZE",    "poolMinSize",             cfg.poolMinSize()));
        builder.setPoolBorrowTimeoutSeconds(
                resolveInt("borrowTimeoutSeconds",    testngParams, "BORROW_TIMEOUT",   "borrowTimeoutSeconds",    cfg.poolBorrowTimeoutSeconds()));
        builder.setPoolMaxReuseCount(
                resolveInt("maxReuseCount",           testngParams, "MAX_REUSE_COUNT",  "maxReuseCount",           cfg.poolMaxReuseCount()));
        builder.setCloseAfterEach(
                resolveBoolean("closeAfterEach",      testngParams, "CLOSE_AFTER_EACH", "closeAfterEach",          cfg.closeAfterEach()));

        // ── Selenium Grid ─────────────────────────────────────────────────────
        builder.setGridEnabled(
                resolveBoolean("gridEnabled",  testngParams, "GRID_ENABLED",  "gridEnabled",  cfg.gridEnabled()));
        builder.setGridHubUrl(
                resolve("gridHubUrl",          testngParams, "GRID_HUB_URL",  "gridHubUrl",   cfg.gridHubUrl()));

        // ── Execution Mode ────────────────────────────────────────────────────
        builder.setExecutionMode(ExecutionMode.from(
                resolve("execution.mode", testngParams, "EXECUTION_MODE", "execution.mode", "targeted")));
        builder.setExcelDataFile(
                resolve("excel.data.file", testngParams, "EXCEL_DATA_FILE", "excel.data.file", ""));

        // ── Credentials ───────────────────────────────────────────────────────
        builder.setCredentials(
                resolve("username",  testngParams, "APP_USERNAME", "username",  cfg.primaryUserName()),
                resolve("password",  testngParams, "APP_PASSWORD", "password",  cfg.primaryPassword()));

        // ── Database ──────────────────────────────────────────────────────────
        builder.setDbConfig(
                resolve("dbUrl",      testngParams, "DB_URL",      "dbUrl",      cfg.dbUrl()),
                resolve("dbUser",     testngParams, "DB_USERNAME", "dbUser",     cfg.dbUserName()),
                resolve("dbPassword", testngParams, "DB_PASSWORD", "dbPassword", cfg.dbPassword()),
                resolve("dbQuery",    testngParams, "DB_QUERY",    "dbQuery",    cfg.dbQuery()));

        ProjectConfig config = builder.build();
        logger.info("ProjectConfig built: " + summarise(config));
        return config;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Dynamically loads the project's {@link ProjectAppConfiguration}.
     *
     * <p>The class name is read from the {@code configClass} TestNG parameter.
     * Falls back to {@link #DEFAULT_CONFIG_CLASS} when absent.
     *
     * <p>Because every project config extends {@code ProjectAppConfiguration}
     * (which already extends {@code FrameworkConfiguration}), and because both
     * property files are listed in the config's {@code @Config.Sources}, Owner's
     * MERGE policy automatically resolves framework keys from
     * {@code frameworkConfig.properties} and project keys from the project file —
     * no manual combination required.
     */
    @SuppressWarnings("unchecked")
    private static ProjectAppConfiguration loadProjectConfig(
            ConcurrentMap<String, String> testngParams) {

        String className = resolve("configClass", testngParams,
                "CONFIG_CLASS", "configClass", DEFAULT_CONFIG_CLASS);

        if (DEFAULT_CONFIG_CLASS.equals(className)) {
            logger.info("No 'configClass' TestNG parameter found — using built-in AutoFrameXConfiguration. "
                    + "For a project-specific config add: "
                    + "<parameter name=\"configClass\" value=\"com.yourapp.config.YourConfig\"/> "
                    + "to your testng.xml.");
        }

        try {
            Class<? extends ProjectAppConfiguration> configClass =
                    Class.forName(className).asSubclass(ProjectAppConfiguration.class);
            logger.info("Loading project config: " + className);
            return ConfigurationManager.getConfiguration(configClass);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "configClass not found on classpath: '" + className + "'."
                    + " Check the value in your TestNG XML.", e);
        } catch (ClassCastException e) {
            throw new RuntimeException(
                    "'" + className + "' does not implement ProjectAppConfiguration.", e);
        }
    }

    /**
     * Resolves the application URL using the {@code environment} parameter.
     *
     * <p>Priority:
     * <ol>
     *   <li>TestNG / env / sys-prop {@code url} — explicit full URL override</li>
     *   <li>TestNG / env / sys-prop {@code baseUrl} — suite-level URL override</li>
     *   <li>{@code environment} parameter → routes to dev / qa / prod URL in the
     *       project's properties file</li>
     * </ol>
     */
    private static String resolveUrl(ConcurrentMap<String, String> testngParams,
            ProjectAppConfiguration cfg) {

        // Explicit URL overrides (highest priority)
        String explicit = resolve("url", testngParams, "BASE_URL", "baseUrl", null);
        if (explicit != null && !explicit.isEmpty()) return explicit;

        String baseUrl = resolve("baseUrl", testngParams, "BASE_URL", "baseUrl", null);
        if (baseUrl != null && !baseUrl.isEmpty()) return baseUrl;

        // Environment-based routing
        String env = resolve("environment", testngParams, "ENVIRONMENT", "env", "dev");

        switch (env.toLowerCase()) {
            case "dev":
            case "development":
                return cfg.devUrl();

            case "qa":
            case "test":
                return cfg.qaUrl();

            case "prod":
            case "production":
                String prodUrl = cfg.prodUrl();
                if (prodUrl == null || prodUrl.isEmpty()) {
                    logger.warn("prodUrl not configured — falling back to qaUrl.");
                    return cfg.qaUrl();
                }
                return prodUrl;

            default:
                logger.warn("Unknown environment '" + env + "' — defaulting to dev.");
                return cfg.devUrl();
        }
    }

    // ── Type-safe resolution helpers ─────────────────────────────────────────

    private static String resolve(String testngKey, ConcurrentMap<String, String> params,
            String envKey, String sysPropKey, String defaultValue) {
        if (params != null) {
            String v = params.get(testngKey);
            if (v != null && !v.trim().isEmpty()) return v;
        }
        String env = System.getenv(envKey);
        if (env != null && !env.trim().isEmpty()) return env;
        String sys = System.getProperty(sysPropKey);
        if (sys != null && !sys.trim().isEmpty()) return sys;
        return defaultValue;
    }

    private static boolean resolveBoolean(String testngKey, ConcurrentMap<String, String> params,
            String envKey, String sysPropKey, boolean defaultValue) {
        return Boolean.parseBoolean(
                resolve(testngKey, params, envKey, sysPropKey, String.valueOf(defaultValue)));
    }

    private static short resolveShort(String testngKey, ConcurrentMap<String, String> params,
            String envKey, String sysPropKey, short defaultValue) {
        String v = resolve(testngKey, params, envKey, sysPropKey, String.valueOf(defaultValue));
        try { return Short.parseShort(v); }
        catch (NumberFormatException e) {
            logger.warn("Invalid short for '" + testngKey + "': " + v + " → using " + defaultValue);
            return defaultValue;
        }
    }

    private static int resolveInt(String testngKey, ConcurrentMap<String, String> params,
            String envKey, String sysPropKey, int defaultValue) {
        String v = resolve(testngKey, params, envKey, sysPropKey, String.valueOf(defaultValue));
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) {
            logger.warn("Invalid int for '" + testngKey + "': " + v + " → using " + defaultValue);
            return defaultValue;
        }
    }

    private static String summarise(ProjectConfig c) {
        return String.format("browser=%s, url=%s, implicit=%ds, pool=%d",
                c.getBrowserName(), c.getAppUrl(), c.getImplicit(), c.getPoolMaxSize());
    }
}
