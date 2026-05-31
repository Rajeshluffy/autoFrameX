package com.framework.security;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

/**
 * SQL injection and XSS pattern detection for test assertions.
 * Use these methods to verify that application responses do not reflect
 * unsanitized input back to the client.
 *
 * The assertion methods never log the raw input value to avoid polluting
 * test output with attack strings.
 */
public final class InputSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(InputSanitizer.class);

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|EXEC|EXECUTE|" +
        "UNION|SCRIPT|DECLARE|CAST|CONVERT)\\b" +
        "|'\\s*--" +
        "|;\\s*DROP" +
        "|\\bOR\\b\\s+['\"]?1['\"]?\\s*=\\s*['\"]?1" +
        "|\\bAND\\b\\s+['\"]?1['\"]?\\s*=\\s*['\"]?1" +
        "|\\/\\*.*\\*\\/)",
        Pattern.DOTALL);

    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(<\\s*script[\\s>]" +
        "|javascript\\s*:" +
        "|on(error|load|click|mouseover|focus|blur|change|submit)\\s*=" +
        "|<\\s*(img|iframe|object|embed|svg|math)[\\s>]" +
        "|alert\\s*\\(" +
        "|document\\s*\\.\\s*(cookie|write|location)" +
        "|eval\\s*\\()");

    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[<>\"';&]");

    private InputSanitizer() {}

    /**
     * Returns {@code true} if {@code input} contains SQL injection patterns.
     * Null input returns {@code false}.
     */
    public static boolean containsSqlInjection(String input) {
        if (input == null) return false;
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * Returns {@code true} if {@code input} contains XSS patterns.
     * Null input returns {@code false}.
     */
    public static boolean containsXss(String input) {
        if (input == null) return false;
        return XSS_PATTERN.matcher(input).find();
    }

    /**
     * Asserts that {@code input} contains neither SQL injection nor XSS patterns.
     * Logs {@code fieldName} at WARN level (not the input value) and fails the
     * test if either pattern matches.
     *
     * @param input     the value to check (e.g. a form field or API response value)
     * @param fieldName human-readable label for the log message
     */
    public static void assertSafeInput(String input, String fieldName) {
        boolean hasSql = containsSqlInjection(input);
        boolean hasXss = containsXss(input);

        if (hasSql || hasXss) {
            String detected = hasSql && hasXss ? "SQL injection and XSS"
                : hasSql ? "SQL injection" : "XSS";
            logger.warn("Unsafe input detected in field [{}]: {}", fieldName, detected);
            Assert.fail("Field [" + fieldName + "] contains " + detected + " patterns");
        }
    }

    /**
     * Escapes {@code <}, {@code >}, {@code "}, {@code '}, {@code ;}, and {@code &}
     * with their HTML entity equivalents so user-supplied input can be safely included
     * in log messages.
     *
     * @param input the raw value (may be null)
     * @return sanitized string, or empty string if input is null
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        return DANGEROUS_CHARS.matcher(input)
            .replaceAll(m -> switch (m.group()) {
                case "<"  -> "&lt;";
                case ">"  -> "&gt;";
                case "\"" -> "&quot;";
                case "'"  -> "&#x27;";
                case ";"  -> "&#x3B;";
                case "&"  -> "&amp;";
                default   -> m.group();
            });
    }
}
