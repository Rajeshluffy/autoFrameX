package com.framework.config.data;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single implementation of the framework's standard configuration priority
 * chain: TestNG/Cucumber parameter → CI/CD environment variable → JVM system
 * property → caller-supplied default.
 *
 * <p>Both {@link ProjectDirector} (project-level config) and
 * {@code design.patterns.object.pool.DriverPoolManager} (pool sizing) used to
 * implement this same four-tier chain independently — a new override source
 * added to one would silently not apply to the other. They now both delegate
 * here.
 *
 * <p>{@code paramKey} doubles as both the TestNG/Cucumber parameter name and
 * the {@code -D} system property name. Every real call site in this codebase
 * already passed the same string for both, so this isn't a behavior change,
 * just dropping a parameter that was always redundant.
 *
 * <p>Unlike a naive "resolve the string once, then parse it" approach, the
 * numeric/boolean resolvers here fall through to the next tier if a given
 * tier's value fails to parse, rather than immediately giving up and
 * returning the default — a malformed higher-priority override shouldn't
 * shadow a perfectly valid lower-priority one.
 */
public final class ConfigResolver {

    private static final Logger logger = LoggerFactory.getLogger(ConfigResolver.class);

    private ConfigResolver() {}

    /**
     * Resolves a string value through the priority chain.
     *
     * @param paramKey     TestNG/Cucumber parameter name (also used as the
     *                     system property name)
     * @param envKey       environment variable name
     * @param params       parameter map; may be {@code null}
     * @param defaultValue returned if no tier has a non-blank value
     */
    public static String resolveString(String paramKey, String envKey,
            Map<String, String> params, String defaultValue) {
        if (params != null) {
            String v = params.get(paramKey);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) return envValue.trim();
        String sysValue = System.getProperty(paramKey);
        if (sysValue != null && !sysValue.trim().isEmpty()) return sysValue.trim();
        return defaultValue;
    }

    /** Resolves a boolean value through the priority chain. */
    public static boolean resolveBoolean(String paramKey, String envKey,
            Map<String, String> params, boolean defaultValue) {
        if (params != null && params.containsKey(paramKey)) {
            return Boolean.parseBoolean(params.get(paramKey));
        }
        String envValue = System.getenv(envKey);
        if (envValue != null) return Boolean.parseBoolean(envValue);
        String sysValue = System.getProperty(paramKey);
        if (sysValue != null) return Boolean.parseBoolean(sysValue);
        return defaultValue;
    }

    /**
     * Resolves an int value through the priority chain. A tier whose value
     * fails to parse is logged and skipped in favor of the next tier, rather
     * than immediately returning {@code defaultValue}.
     */
    public static int resolveInt(String paramKey, String envKey,
            Map<String, String> params, int defaultValue) {
        if (params != null && params.containsKey(paramKey)) {
            String v = params.get(paramKey);
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid int for parameter '{}': {} — checking next source", paramKey, v);
            }
        }
        String envValue = System.getenv(envKey);
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid int for env var '{}': {} — checking next source", envKey, envValue);
            }
        }
        String sysValue = System.getProperty(paramKey);
        if (sysValue != null) {
            try {
                return Integer.parseInt(sysValue.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid int for system property '{}': {} — using default {}", paramKey, sysValue, defaultValue);
            }
        }
        return defaultValue;
    }

    /** Resolves a short value through the priority chain (see {@link #resolveInt}). */
    public static short resolveShort(String paramKey, String envKey,
            Map<String, String> params, short defaultValue) {
        int resolved = resolveInt(paramKey, envKey, params, defaultValue);
        if (resolved < Short.MIN_VALUE || resolved > Short.MAX_VALUE) {
            logger.warn("Value {} for '{}' is out of short range — using default {}", resolved, paramKey, defaultValue);
            return defaultValue;
        }
        return (short) resolved;
    }
}
