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
 * <h3>What counts as "set"</h3>
 * A tier that holds a blank value, or an unresolved placeholder such as {@code ${browser}} (what a suite file
 * passes through when no {@code -Dbrowser} was given, and what an empty Jenkins parameter becomes as an
 * environment variable), is <em>not set</em>: the next tier decides. This is the same for strings, booleans and
 * numbers. A boolean used to be the exception: an empty {@code HEADLESS=} counted as {@code false} and overrode
 * every lower tier.
 *
 * <p>The numeric and boolean resolvers fall through to the next tier if a given
 * tier's value fails to parse, rather than immediately giving up and returning
 * the default, with a warning that names the bad value. A boolean must be exactly {@code true} or
 * {@code false} (any case): {@code Boolean.parseBoolean} turned every typo ({@code ture}, {@code yes}, {@code 1})
 * into {@code false}, silently, and let it override a perfectly valid lower-priority value.
 */
public final class ConfigResolver {

    private static final Logger logger = LoggerFactory.getLogger(ConfigResolver.class);

    private ConfigResolver() {}

    /** Whether a tier's value is a real value: not null, not blank, and not an unresolved {@code ${...}}. */
    static boolean isSet(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;
        return !(trimmed.startsWith("${") && trimmed.endsWith("}"));
    }

    /**
     * Resolves a string value through the priority chain.
     *
     * @param paramKey     TestNG/Cucumber parameter name (also used as the
     *                     system property name)
     * @param envKey       environment variable name
     * @param params       parameter map; may be {@code null}
     * @param defaultValue returned if no tier has a set value
     */
    public static String resolveString(String paramKey, String envKey,
            Map<String, String> params, String defaultValue) {
        if (params != null) {
            String v = params.get(paramKey);
            if (isSet(v)) return v.trim();
        }
        String envValue = System.getenv(envKey);
        if (isSet(envValue)) return envValue.trim();
        String sysValue = System.getProperty(paramKey);
        if (isSet(sysValue)) return sysValue.trim();
        return defaultValue;
    }

    /**
     * Resolves a boolean value through the priority chain. A tier's value must be {@code true} or
     * {@code false} (any case, surrounding whitespace ignored); anything else is logged and the next tier decides.
     */
    public static boolean resolveBoolean(String paramKey, String envKey,
            Map<String, String> params, boolean defaultValue) {
        if (params != null) {
            Boolean parsed = parseBoolean(params.get(paramKey), "parameter '" + paramKey + "'");
            if (parsed != null) return parsed;
        }
        Boolean fromEnv = parseBoolean(System.getenv(envKey), "env var '" + envKey + "'");
        if (fromEnv != null) return fromEnv;
        Boolean fromProperty = parseBoolean(System.getProperty(paramKey), "system property '" + paramKey + "'");
        if (fromProperty != null) return fromProperty;
        return defaultValue;
    }

    /** {@code true}/{@code false}, or {@code null} when the tier is unset or not a boolean (the latter is logged). */
    private static Boolean parseBoolean(String value, String source) {
        if (!isSet(value)) return null;
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (trimmed.equalsIgnoreCase("false")) return Boolean.FALSE;
        logger.warn("Invalid boolean for {}: '{}' (expected true or false) — checking next source", source, trimmed);
        return null;
    }

    /**
     * Resolves an int value through the priority chain. A tier whose value
     * fails to parse is logged and skipped in favor of the next tier, rather
     * than immediately returning {@code defaultValue}.
     */
    public static int resolveInt(String paramKey, String envKey,
            Map<String, String> params, int defaultValue) {
        if (params != null) {
            String v = params.get(paramKey);
            if (isSet(v)) {
                try {
                    return Integer.parseInt(v.trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid int for parameter '{}': {} — checking next source", paramKey, v);
                }
            }
        }
        String envValue = System.getenv(envKey);
        if (isSet(envValue)) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid int for env var '{}': {} — checking next source", envKey, envValue);
            }
        }
        String sysValue = System.getProperty(paramKey);
        if (isSet(sysValue)) {
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
