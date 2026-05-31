package com.framework.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

/**
 * Scans strings and configuration maps for hardcoded secrets.
 * All patterns are compiled once at class load for efficiency.
 *
 * The scan methods never log the suspicious value itself — only the key name —
 * to prevent secrets from appearing in test output or CI logs.
 */
public final class SecretDetector {

    private static final Logger logger = LoggerFactory.getLogger(SecretDetector.class);

    // key=value or key: value where key suggests a credential
    private static final Pattern CREDENTIAL_KV_PATTERN = Pattern.compile(
        "(?i)(password|passwd|pwd|secret|token|api[_-]?key|apikey|auth[_-]?token|" +
        "access[_-]?key|private[_-]?key|client[_-]?secret)\\s*[=:]\\s*(?!\\s*$)\\S{6,}");

    // AWS access key ID format
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");

    // PEM private key header
    private static final Pattern PEM_PATTERN = Pattern.compile(
        "-----BEGIN\\s+(RSA\\s+|EC\\s+|OPENSSH\\s+)?PRIVATE KEY-----");

    // JDBC / connection string with embedded user:pass@host
    private static final Pattern CONNECTION_STRING_PATTERN = Pattern.compile(
        "(?i)(jdbc:|mongodb(\\+srv)?://|redis://|amqp://|postgresql://)[^:]+:[^@]{4,}@");

    // Long base64 string that could be an encoded credential (40+ chars)
    private static final Pattern BASE64_CREDENTIAL_PATTERN = Pattern.compile(
        "(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{40,}={0,2}(?![A-Za-z0-9+/])");

    private static final List<Pattern> ALL_PATTERNS = List.of(
        CREDENTIAL_KV_PATTERN,
        AWS_KEY_PATTERN,
        PEM_PATTERN,
        CONNECTION_STRING_PATTERN,
        BASE64_CREDENTIAL_PATTERN
    );

    private SecretDetector() {}

    /**
     * Returns {@code true} if {@code value} matches any known secret pattern.
     * Null or blank input always returns {@code false}.
     */
    public static boolean scan(String value) {
        if (value == null || value.isBlank()) return false;
        return ALL_PATTERNS.stream().anyMatch(p -> p.matcher(value).find());
    }

    /**
     * Scans all values in {@code configMap} and returns the list of keys
     * whose values triggered a pattern match.
     */
    public static List<String> scan(Map<String, String> configMap) {
        List<String> suspicious = new ArrayList<>();
        if (configMap == null) return suspicious;
        configMap.forEach((key, value) -> {
            if (scan(value)) suspicious.add(key);
        });
        return suspicious;
    }

    /**
     * Asserts that no values in {@code configMap} contain hardcoded secrets.
     * Logs each suspicious key at WARN level (never the value) and fails the
     * test if any are found.
     *
     * @param configMap the configuration to scan
     * @param context   human-readable label for the log message (e.g. file name)
     */
    public static void assertNoSecrets(Map<String, String> configMap, String context) {
        List<String> suspicious = scan(configMap);
        if (!suspicious.isEmpty()) {
            suspicious.forEach(key ->
                logger.warn("Potential hardcoded secret detected in [{}] at key: {}", context, key));
            Assert.fail("Hardcoded secrets detected in [" + context + "] at keys: " + suspicious);
        }
        logger.info("Secret scan passed for [{}] — no hardcoded credentials detected", context);
    }
}
