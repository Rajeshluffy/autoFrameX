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
 * <p>The assertion methods never log the raw input value to avoid polluting
 * test output with attack strings.
 *
 * <h3>What the detection means</h3>
 * <p>It is a pattern check, not a parser, and it is tuned so that both of its answers are worth something:
 * <ul>
 *   <li>Ordinary text is not an attack. The words "select", "update", "create", "union" or "script" on their own
 *       appear on any page, so a keyword is only reported inside the shape of a statement
 *       ({@code SELECT ... FROM}, {@code UNION SELECT}, {@code INSERT INTO}, {@code DROP TABLE}, ...), and an event
 *       handler only when it is a real one ({@code onerror=}, {@code ontoggle=}), not any word starting with "on".</li>
 *   <li>The common payload families are caught: quote-and-comment endings, always-true conditions, stacked
 *       statements, time-delay calls, script and markup injection, event-handler attributes, and
 *       {@code javascript:}, {@code vbscript:} and {@code data:text/html} URLs.</li>
 * </ul>
 * A payload that is encoded (percent, HTML entity, Unicode escapes) is not decoded here: decode the value first if
 * the application might.
 */
public final class InputSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(InputSanitizer.class);

    /** Event-handler attributes that execute script; deliberately a list, so "onion = tasty" is not one. */
    private static final String EVENT_HANDLERS =
        "error|load|unload|beforeunload|click|dblclick|auxclick|contextmenu|mouse\\w+|key\\w+|focus\\w*|blur|"
        + "change|input|invalid|submit|reset|select|toggle|drag\\w*|drop|touch\\w+|pointer\\w+|wheel|scroll|"
        + "resize|copy|cut|paste|pageshow|pagehide|hashchange|message|animation\\w+|transition\\w+|begin|end|"
        + "start|play|pause|abort|readystatechange|search|show|finish|bounce";

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(?:"
        // a quote followed by a comment ends the original statement: admin'--  admin' #  x'/*
        + "'\\s*(?:--|#|/\\*)"
        // an always-true condition: ' OR '1'='1   1 OR 1=1   AND 'a'='a   (both sides equal)
        + "|\\b(?:OR|AND)\\b\\s+['\"]?(\\w+)['\"]?\\s*=\\s*['\"]?\\1\\b"
        // statements, in their statement shape
        + "|\\bUNION\\b\\s+(?:ALL\\s+)?SELECT\\b"
        // a column list directly before FROM ("select an option from the list" is a sentence, not a query)
        + "|\\bSELECT\\s+(?:DISTINCT\\s+)?(?:\\*|[\\w.\"`\\[\\]]+(?:\\([^)]*\\))?"
        + "(?:\\s*,\\s*[\\w.\"`\\[\\]]+(?:\\([^)]*\\))?){0,64})\\s+FROM\\b"
        + "|\\bINSERT\\s+INTO\\b"
        + "|\\bDELETE\\s+FROM\\b"
        + "|\\bUPDATE\\s+[\\w.\"`\\[\\]]+\\s+SET\\b"
        + "|\\b(?:DROP|ALTER|TRUNCATE|CREATE)\\s+(?:TABLE|DATABASE|SCHEMA|INDEX|VIEW|USER|PROCEDURE)\\b"
        // a second statement after a semicolon
        + "|;\\s*(?:DROP|DELETE|INSERT|UPDATE|SELECT|EXEC|EXECUTE|SHUTDOWN|DECLARE)\\b"
        // stored-procedure execution and time-delay calls used for blind injection
        + "|\\b(?:EXEC|EXECUTE)\\s*(?:\\(|\\s+(?:xp_|sp_))"
        + "|\\bWAITFOR\\s+DELAY\\b"
        + "|\\b(?:SLEEP|BENCHMARK|PG_SLEEP)\\s*\\("
        // an inline comment used to break up keywords
        + "|/\\*[\\s\\S]{0,500}?\\*/"
        + ")");

    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(?:"
        + "<\\s*/?\\s*(?:script|img|iframe|frame|frameset|object|embed|applet|svg|math|body|video|audio|details|"
        + "style|link|meta|base|form|input|button|marquee|template|source)[\\s/>]"
        + "|\\b(?:java|vb)script\\s*:"
        + "|\\bdata\\s*:\\s*text/html"
        + "|\\bon(?:" + EVENT_HANDLERS + ")\\s*="
        + "|\\balert\\s*\\("
        + "|\\bdocument\\s*\\.\\s*(?:cookie|write|location|domain)"
        + "|\\beval\\s*\\("
        + ")");

    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[<>\"';&\\r\\n\\u0085\\u2028\\u2029]");

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
     * with their HTML entity equivalents, and line breaks ({@code CR}, {@code LF}, and the Unicode line and
     * paragraph separators) with visible escapes ({@code \r}, {@code \n}, {@code \\uXXXX}), so user-supplied
     * input can be safely included in log messages. The line breaks matter most for a log: a value containing
     * one starts a second line that looks like an entry the application wrote.
     *
     * @param input the raw value (may be null)
     * @return sanitized string, or empty string if input is null
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        return DANGEROUS_CHARS.matcher(input)
            .replaceAll(m -> java.util.regex.Matcher.quoteReplacement(switch (m.group()) {
                case "<"  -> "&lt;";
                case ">"  -> "&gt;";
                case "\"" -> "&quot;";
                case "'"  -> "&#x27;";
                case ";"  -> "&#x3B;";
                case "&"  -> "&amp;";
                case "\r" -> "\\r";
                case "\n" -> "\\n";
                default   -> String.format("\\u%04x", (int) m.group().charAt(0));
            }));
    }
}
