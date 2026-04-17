package com.framework.config.data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Thread-safe configuration manager implementing Singleton pattern.
 * Manages lifecycle of configuration with lazy initialization support.
 * 
 * <p>Features:
 * <ul>
 *   <li>Thread-safe singleton with double-checked locking</li>
 *   <li>Support for runtime configuration updates</li>
 *   <li>Environment-aware configuration loading</li>
 *   <li>Fallback to defaults when configs unavailable</li>
 * </ul>
 * 
 * @author Framework Team
 * @version 3.0
 */
public class ConfigManager {
    
    private static final Logger logger = Logger.getLogger(ConfigManager.class.getName());
    private static volatile ConfigManager instance;
    
    private volatile ProjectConfig config;
    private volatile boolean initialized = false;
    
    // Runtime overrides storage
    private final ConcurrentMap<String, String> runtimeOverrides = new ConcurrentHashMap<>();
    
    /**
     * Private constructor for singleton pattern.
     */
    private ConfigManager() {}
    
    /**
     * Gets singleton instance using double-checked locking.
     * 
     * @return ConfigManager instance
     */
    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initializes configuration from multiple sources.
     * Safe to call multiple times - will only initialize once.
     */
    public synchronized void initializeConfig() {
        if (initialized) {
            logger.fine("Configuration already initialized, skipping");
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
            logger.fine("Configuration already initialized, applying overrides only");
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
            logger.warning("Configuration not initialized, auto-initializing with defaults");
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
    private void applyOverrides(ConcurrentMap<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        
        runtimeOverrides.putAll(overrides);
        
        // Rebuild config with new overrides
        config = ProjectDirector.construct(runtimeOverrides);
        logger.info("Applied runtime overrides: " + overrides.keySet());
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
        logger.warning("Configuration reset");
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