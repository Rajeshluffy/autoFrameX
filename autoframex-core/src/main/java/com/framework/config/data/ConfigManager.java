package com.framework.config.data;

import java.util.TreeSet;
import java.util.Set;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration manager, keyed by execution context so multiple applications
 * (distinct {@code configClass} values) can run concurrently in one JVM.
 *
 * <p>Historically this was a plain JVM-wide singleton — one {@link ProjectConfig}
 * shared by the entire process. That blocked running two applications' suites
 * concurrently in one Surefire run: the second suite's parameters silently merged
 * into the first's. It is now a small registry of instances keyed by a
 * {@code contextId} string (see {@link #bindContext} / {@link #resolveContextId}),
 * bound per-thread via a {@link ThreadLocal}.
 *
 * <p><b>{@link #getInstance()} keeps its original signature and behavior</b> for
 * every existing caller: it always resolves to whichever context is currently
 * bound on the calling thread, defaulting to a single shared context if nothing
 * ever calls {@link #bindContext}. Single-application suites are unaffected.
 *
 * <p><b>Binding contract:</b> {@link #bindContext} must be called on every thread
 * before it touches {@code getInstance()} — once per {@code @BeforeTest} (suite
 * thread) and again per {@code @BeforeMethod}/Cucumber {@code @Before} (worker
 * thread), since TestNG's {@code parallel="methods"}/{@code "classes"} runs test
 * methods on different threads than the one that ran {@code @BeforeTest}. See
 * {@code Reporter.initFromContext}, {@code ProjectSpecificMethods.preCondition},
 * {@code ScenarioHooks.setUp}, and {@code CucumberHooks.setUp}.
 *
 * <p><b>Known limitation:</b> the Owner library's own {@code ConfigCache} (used by
 * {@link ConfigurationManager}) is itself a process-wide cache keyed by
 * {@code Class}. Two contexts sharing the same {@code configClass} but wanting
 * different property overrides (e.g. same app, two environments, run
 * concurrently) still share that cached proxy — this fix isolates config across
 * different {@code configClass} values, not across differing overrides of the
 * same one.
 *
 * @author Framework Team
 * @version 3.1
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    /** Context id used when nothing ever calls {@link #bindContext}. */
    private static final String DEFAULT_CONTEXT = "default";

    private static final ConcurrentMap<String, ConfigManager> REGISTRY = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> CONTEXT_ID = new ThreadLocal<>();

    private volatile ProjectConfig config;
    private volatile boolean initialized = false;

    // Runtime overrides storage
    // Replaced as a whole (never edited in place) when a later <test> brings different settings, so a
    // reader never sees a half-updated map.
    private volatile ConcurrentMap<String, String> runtimeOverrides = new ConcurrentHashMap<>();

    /**
     * Private constructor — instances are only created via the {@link #REGISTRY}.
     */
    private ConfigManager() {}

    /**
     * Binds the calling thread to a configuration context. Must be called before
     * any {@link #getInstance()} use on this thread if per-application isolation
     * is needed; otherwise every thread falls back to {@link #DEFAULT_CONTEXT}.
     *
     * @param contextId identifies which application's config this thread should
     *                  see (typically the resolved {@code configClass}); null
     *                  falls back to the default context
     */
    public static void bindContext(String contextId) {
        CONTEXT_ID.set(contextId != null && !contextId.isBlank() ? contextId : DEFAULT_CONTEXT);
    }

    /**
     * Resolves a context id from a TestNG/Cucumber parameter map, using the same
     * priority order {@link ProjectDirector} uses to resolve {@code configClass}:
     * explicit {@code contextId} param, then {@code configClass} param, then the
     * {@code CONFIG_CLASS} env var, then the {@code configClass} system property,
     * then {@link #DEFAULT_CONTEXT}.
     *
     * @param params suite/test/method parameters; may be {@code null} (e.g. from
     *               Cucumber hooks that have no TestNG XML params available)
     * @return a non-null context id
     */
    public static String resolveContextId(java.util.Map<String, String> params) {
        if (params != null) {
            String explicit = params.get("contextId");
            if (explicit != null && !explicit.isBlank()) return explicit;

            String configClass = params.get("configClass");
            if (configClass != null && !configClass.isBlank()) return configClass;
        }
        String envConfigClass = System.getenv("CONFIG_CLASS");
        if (envConfigClass != null && !envConfigClass.isBlank()) return envConfigClass;

        String sysConfigClass = System.getProperty("configClass");
        if (sysConfigClass != null && !sysConfigClass.isBlank()) return sysConfigClass;

        return DEFAULT_CONTEXT;
    }

    /**
     * Gets the configuration instance bound to the calling thread's context,
     * creating it on first use. Falls back to {@link #DEFAULT_CONTEXT} if the
     * thread never called {@link #bindContext} — identical to the old singleton
     * behavior for any caller that doesn't opt into multi-context isolation.
     *
     * @return ConfigManager instance for the current thread's context
     */
    public static ConfigManager getInstance() {
        String id = CONTEXT_ID.get();
        if (id == null) id = DEFAULT_CONTEXT;
        return REGISTRY.computeIfAbsent(id, k -> new ConfigManager());
    }
    
    /**
     * Initializes configuration from multiple sources.
     * Safe to call multiple times - will only initialize once.
     */
    public synchronized void initializeConfig() {
        if (initialized) {
            logger.debug("Configuration already initialized, skipping");
            return;
        }
        
        logger.info("Initializing configuration...");
        config = ProjectDirector.construct();
        initialized = true;
        logger.info("Configuration initialized successfully");
    }
    
    /**
     * Initializes configuration with TestNG parameters.
     * Allows override of properties from TestNG XML.
     * 
     * @param testngParams parameters from TestNG suite/test
     */
    public synchronized void initializeConfig(ConcurrentMap<String, String> testngParams) {
        if (initialized) {
            logger.debug("Configuration already initialized, applying overrides only");
            applyOverrides(testngParams);
            return;
        }
        
        logger.info("Initializing configuration with TestNG parameters...");
        
        // Store overrides for priority resolution
        if (testngParams != null) {
            runtimeOverrides.putAll(testngParams);
        }
        
        config = ProjectDirector.construct(testngParams);
        initialized = true;
        logger.info("Configuration initialized with TestNG parameters");
    }
    
    /**
     * Gets the current configuration.
     * Auto-initializes with defaults if not yet initialized.
     * 
     * @return ProjectConfig instance
     */
    public ProjectConfig getConfig() {
        if (!initialized) {
            logger.warn("Configuration not initialized, auto-initializing with defaults");
            initializeConfig();
        }
        return config;
    }
    
    /**
     * Updates configuration at runtime (useful for multi-environment tests).
     * 
     * @param newConfig new configuration to apply
     */
    public synchronized void updateConfig(ProjectConfig newConfig) {
        if (newConfig == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        this.config = newConfig;
        logger.info("Configuration updated at runtime");
    }
    
    /**
     * Applies runtime overrides to existing configuration.
     * 
     * @param overrides map of property overrides
     */
    private void applyOverrides(ConcurrentMap<String, String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        // Every <test> brings its full parameter set (suite parameters included), so these are the
        // complete settings for that test. They used to be merged into the previous test's: a key the
        // new test did not set (say environment=qa from the first test) stayed in force, and the
        // shared config was rebuilt for every <test> even when nothing had changed.
        Set<String> changed = changedKeys(runtimeOverrides, incoming);
        if (changed.isEmpty()) {
            logger.debug("Same settings as before - keeping the existing configuration");
            return;
        }

        ConcurrentMap<String, String> next = new ConcurrentHashMap<>(incoming);
        config = ProjectDirector.construct(next);
        runtimeOverrides = next;
        logger.warn("Configuration rebuilt for test '{}'; changed settings: {}. Tests that run in "
                + "parallel in the same context share this one configuration - give each <test> its own "
                + "'contextId' parameter to keep them apart.", incoming.get("testName"), changed);
    }

    /** Parameters TestNG adds to every <test>; they differ per test without being settings. */
    private static final Set<String> BOOKKEEPING_KEYS = Set.of("suiteName", "testName");

    /** The setting names whose values differ between two parameter sets (bookkeeping keys ignored). */
    static Set<String> changedKeys(Map<String, String> current, Map<String, String> incoming) {
        Set<String> all = new TreeSet<>(current.keySet());
        all.addAll(incoming.keySet());
        Set<String> changed = new TreeSet<>();
        for (String key : all) {
            if (!BOOKKEEPING_KEYS.contains(key)
                    && !Objects.equals(current.get(key), incoming.get(key))) {
                changed.add(key);
            }
        }
        return changed;
    }
    
    /**
     * Gets a runtime override value.
     * 
     * @param key property key
     * @return override value or null
     */
    public String getRuntimeOverride(String key) {
        return runtimeOverrides.get(key);
    }
    
    /**
     * Checks if configuration is initialized.
     * 
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Resets configuration (useful for testing).
     * WARNING: Only use in test teardown scenarios.
     */
    public synchronized void reset() {
        config = null;
        initialized = false;
        runtimeOverrides.clear();
        logger.warn("Configuration reset");
    }
    
    /**
     * Gets configuration summary for logging.
     * 
     * @return formatted config summary
     */
    public String getConfigSummary() {
        if (!initialized) {
            return "Configuration not initialized";
        }
        
        return String.format(
            "Config{browser=%s, url=%s, implicit=%ds, explicit=%ds, poolSize=%d}",
            config.getBrowserName(),
            config.getAppUrl(),
            config.getImplicit(),
            config.getExplicit(),
            config.getPoolMaxSize()
        );
    }
}