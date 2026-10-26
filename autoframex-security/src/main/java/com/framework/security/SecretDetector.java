package com.framework.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

/**
 * Scans strings and configuration maps for hardcoded secrets.
 * All patterns are compiled once at class load for efficiency.
 *
 * <p>A configuration map is checked two ways: each <em>value</em> against the secret patterns, and each
 * <em>key</em> by name. The second matters most for ordinary configuration: {@code db.password=hunter2} has a
 * value that matches no pattern (it is short and has no {@code password=} in it), but the key says what it is.
 * A key whose last word is a credential word ({@code password}, {@code secret}, {@code token}, an {@code api key},
 * ...) is reported when its value is a literal. A placeholder ({@code ${DB_PASSWORD}}, {@code <your-password>},
 * {@code ENC(...)}, {@code ***}), a blank, or a boolean is not a hardcoded secret and is left alone, so
 * {@code autoFrameX.api.capture.mask.secrets=false} is not a finding.
 *
 * <p>The scan methods never log the suspicious value itself — only the key name —
 * to prevent secrets from appearing in test output or CI logs.
 */
public final class SecretDetector {

    private static final Logger logger = LoggerFactory.getLogger(SecretDetector.class);

    /** Text that stands in for a value rather than being one: a variable reference, a template, a mask. */
    private static final String NOT_A_LITERAL = "(?!\\$\\{|\\$[A-Za-z_]|<|\\{\\{|%[A-Za-z_]|\\*{3}|ENC\\()";

    // key=value, key: value or "key":"value" where key suggests a credential and value is a literal
    private static final Pattern CREDENTIAL_KV_PATTERN = Pattern.compile(
        "(?i)(password|passwd|pwd|secret|token|api[_-]?key|apikey|auth[_-]?token|"
        + "access[_-]?key|private[_-]?key|client[_-]?secret)[\"']?\\s*[=:]\\s*[\"']?"
        + NOT_A_LITERAL + "[^\\s\"',;&]{4,}");

    // AWS access key ID format
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");

    // PEM private key header
    private static final Pattern PEM_PATTERN = Pattern.compile(
        "-----BEGIN\\s+(RSA\\s+|EC\\s+|OPENSSH\\s+)?PRIVATE KEY-----");

    // JDBC / connection string with embedded user:pass@host
    private static final Pattern CONNECTION_STRING_PATTERN = Pattern.compile(
        // Lengths are bounded: with "[^:]+" and "[^@]{4,}" a long string of "jdbc:jdbc:..." (no '@') made every
        // start position scan to the end, which took 12 s on 50,000 characters.
        "(?i)(jdbc:|mongodb(\\+srv)?://|redis://|amqp://|postgresql://)[^:\\s]{1,200}:[^@\\s]{4,200}@");

    // jdbc:...?user=a&password=b — the password as a URL parameter instead of user:pass@host
    private static final Pattern JDBC_PARAMETER_PATTERN = Pattern.compile(
        "(?i)jdbc:[^\\s]{0,500}[?&;](password|pwd)=" + NOT_A_LITERAL + "[^&;\\s]+");

    // Long base64 string that could be an encoded credential (40+ chars)
    private static final Pattern BASE64_CREDENTIAL_PATTERN = Pattern.compile(
        "(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{40,}={0,2}(?![A-Za-z0-9+/])");

    private static final List<Pattern> ALL_PATTERNS = List.of(
        CREDENTIAL_KV_PATTERN,
        AWS_KEY_PATTERN,
        PEM_PATTERN,
        CONNECTION_STRING_PATTERN,
        JDBC_PARAMETER_PATTERN,
        BASE64_CREDENTIAL_PATTERN
    );

    /** A last word that is a credential by itself. */
    private static final Set<String> CREDENTIAL_WORDS = Set.of(
        "password", "passwd", "pwd", "passphrase", "secret", "token", "credential", "credentials", "apikey");

    /** "key" is a credential only after one of these ({@code api.key}, {@code encryption.key}), not in {@code primary.key}. */
    private static final Set<String> KEY_QUALIFIERS = Set.of(
        "api", "access", "private", "secret", "encryption", "signing", "auth", "license", "client", "master");

    private static final Pattern PLACEHOLDER = Pattern.compile(
        "(?s)\\$\\{.*\\}|\\$[A-Za-z_][A-Za-z0-9_]*|<[^<>]*>|\\{\\{.*\\}\\}|%[A-Za-z_][A-Za-z0-9_.]*%|\\*+|ENC\\(.*\\)");

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
     * Returns the keys of {@code configMap} that hold a hardcoded secret: a value that matches a secret pattern,
     * or a credential-named key (see the class documentation) whose value is a literal.
     */
    public static List<String> scan(Map<String, String> configMap) {
        List<String> suspicious = new ArrayList<>();
        if (configMap == null) return suspicious;
        configMap.forEach((key, value) -> {
            if (scan(value) || isCredentialKeyWithLiteral(key, value)) suspicious.add(key);
        });
        return suspicious;
    }

    /** Whether {@code key} names a credential by its last word, and {@code value} is a literal rather than a stand-in. */
    static boolean isCredentialKeyWithLiteral(String key, String value) {
        if (key == null || value == null || value.isBlank()) return false;
        if (!namesACredential(key)) return false;
        String trimmed = value.trim();
        if (PLACEHOLDER.matcher(trimmed).matches()) return false;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return !(lower.equals("true") || lower.equals("false") || lower.equals("null") || lower.equals("none"));
    }

    private static boolean namesACredential(String key) {
        List<String> words = words(key);
        if (words.isEmpty()) return false;
        String last = words.get(words.size() - 1);
        if (CREDENTIAL_WORDS.contains(last)) return true;
        return last.equals("key") && words.size() > 1 && KEY_QUALIFIERS.contains(words.get(words.size() - 2));
    }

    /** {@code db.clientSecret} and {@code DB_CLIENT_SECRET} both become {@code [db, client, secret]}. */
    private static List<String> words(String key) {
        List<String> words = new ArrayList<>();
        for (String part : key.split("[^A-Za-z0-9]+")) {
            for (String word : part.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
                if (!word.isEmpty()) words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        return words;
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
