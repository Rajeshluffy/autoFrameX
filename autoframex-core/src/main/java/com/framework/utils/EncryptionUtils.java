package com.framework.utils;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256/GCM encryption utility for protecting credentials and sensitive
 * values in test configuration and logs.
 *
 * <h3>Key management</h3>
 * The encryption key is resolved in priority order:
 * <ol>
 *   <li>System property {@code autoFrameX.encryption.key}</li>
 *   <li>Environment variable {@code AUTOFRAMEX_ENCRYPTION_KEY}</li>
 * </ol>
 * There is no built-in fallback key — {@link #encrypt}/{@link #decrypt} throw
 * {@link IllegalStateException} if neither source is set, rather than silently
 * encrypting with a key visible to anyone with repo access.
 *
 * <p>That configured value is a passphrase, not a raw key — it's run through
 * PBKDF2WithHmacSHA256 (per-call random salt, {@link #PBKDF2_ITERATIONS}
 * iterations) to derive the actual AES-256 key, rather than being
 * zero-padded/truncated directly into key bytes. Raw pad/truncate means a
 * normal human-chosen passphrase (say 12-16 characters) gives the resulting
 * "AES-256" key far less real entropy than the name implies, and offers no
 * computational cost to slow down brute-forcing a weak passphrase — exactly
 * what a KDF exists to fix.
 *
 * <h3>Format</h3>
 * Encrypted values are Base64-encoded strings containing a 16-byte random
 * salt and a 12-byte random IV prepended to the GCM ciphertext:
 * {@code Base64(salt || IV || ciphertext)}.
 */
public final class EncryptionUtils {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionUtils.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_SALT_LENGTH = 16;
    // Balances brute-force resistance against per-call latency for a
    // shared application secret (not a user-facing login password, which
    // would warrant OWASP's higher password-hashing minimums).
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int AES_KEY_LENGTH_BITS = 256;

    // Patterns for masking sensitive values in log strings.
    //
    // They must cope with three shapes: plain "password=abc" / "password: abc",
    // JSON bodies ("password":"abc"), and the same JSON after it has been
    // serialized *into* a JSON log line, where inner quotes arrive escaped
    // (\"password\":\"abc\") -- MaskingLogstashEncoder masks that whole line.
    // The old single pattern (key\s*[=:]\s*\S+) missed the quoted forms, and
    // against a compact JSON line its greedy \S+ swallowed every field after
    // the match, corrupting the log record.
    //
    // A match may only START at the beginning of a token ((?<![\w.-])). Without that, the
    // leading [\w.-]* is tried from every position of a long run of word characters (a
    // base64 screenshot, a long id) and each attempt scans to the end of the run: quadratic,
    // about 16 s for 20,000 characters, on every log line. Anchored, it is linear and
    // matches exactly what the unanchored form matched.
    private static final String KEY =
            "(?<![\\w.-])[\\w.-]*(?:password|passwd|pwd|secret|token|key|auth\\b)";
    private static final String OPT_QUOTE = "(?:\\\\?[\"'])?";

    /** Authorization: scheme credentials -- masks scheme and credentials together. */
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "(?i)(authorization" + OPT_QUOTE + "\\s*[=:]\\s*" + OPT_QUOTE + ")"
            + "(?:[A-Za-z]+\\s+)?[^\\s,;\"'\\\\}\\]]+");
    /** key="quoted value" or key=\"escaped quoted value\" -- value may contain spaces. */
    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile(
            "(?i)(" + KEY + OPT_QUOTE + "\\s*[=:]\\s*\\\\?\")((?:[^\"\\\\]|\\\\[^\"])*)");
    /** key=value or key: value -- value runs to the next delimiter, not to end of line. */
    private static final Pattern PLAIN_VALUE_PATTERN = Pattern.compile(
            "(?i)(" + KEY + OPT_QUOTE + "\\s*[=:]\\s*)([^\\s,;&\"'\\\\}\\]]+)");
    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9\\-._~+/]+=*)");

    private EncryptionUtils() {}

    // =========================================================================
    // ENCRYPT / DECRYPT
    // =========================================================================

    /**
     * Encrypts {@code plainText} using AES-256/GCM.
     *
     * @param plainText the value to encrypt
     * @return Base64-encoded ciphertext (salt and IV prepended), or {@code null} on error
     * @throws IllegalStateException if no encryption key is configured (this is a
     *         setup error, not a data error, so it is never turned into {@code null})
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        String passphrase = resolvePassphrase();
        try {
            byte[] salt = generateSalt();
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[salt.length + iv.length + cipherText.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(cipherText, 0, combined, salt.length + iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("Encryption failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts a value previously produced by {@link #encrypt}.
     *
     * @param encryptedBase64 Base64-encoded ciphertext
     * @return original plain-text value, or {@code null} if the value is corrupt or
     *         was encrypted with a different key
     * @throws IllegalStateException if no encryption key is configured
     */
    public static String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) return encryptedBase64;
        String passphrase = resolvePassphrase();
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            byte[] salt = new byte[PBKDF2_SALT_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - PBKDF2_SALT_LENGTH - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, salt, 0, PBKDF2_SALT_LENGTH);
            System.arraycopy(combined, PBKDF2_SALT_LENGTH, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, PBKDF2_SALT_LENGTH + GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Decryption failed: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // MASKING
    // =========================================================================

    /**
     * Masks sensitive values in a log string so credentials never appear in
     * reports or console output.
     *
     * <p>Examples:
     * <pre>
     *   "password=secret123"        → "password=***"
     *   {"password":"abc def"}      → {"password":"***"}
     *   "Bearer eyJhbGci..."        → "Bearer ***"
     * </pre>
     *
     * @param logLine raw log string that may contain sensitive data
     * @return sanitized string safe for logging
     */
    public static String maskSensitiveValues(String logLine) {
        if (logLine == null) return null;
        String masked = AUTH_HEADER_PATTERN.matcher(logLine).replaceAll("$1***");
        masked = BEARER_PATTERN.matcher(masked).replaceAll("$1***");
        masked = QUOTED_VALUE_PATTERN.matcher(masked).replaceAll("$1***");
        masked = PLAIN_VALUE_PATTERN.matcher(masked).replaceAll("$1***");
        return masked;
    }

    /**
     * Returns a masked representation of {@code value} for display in logs.
     * Shows the first 2 and last 2 characters with asterisks in between.
     *
     * @param value the sensitive value to mask
     * @return masked string, e.g. {@code "ab***yz"}
     */
    public static String mask(String value) {
        if (value == null || value.length() <= 4) return "***";
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static String resolvePassphrase() {
        String raw = System.getProperty("autoFrameX.encryption.key");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("AUTOFRAMEX_ENCRYPTION_KEY");
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "No encryption key configured. Set -DautoFrameX.encryption.key=... or the "
                    + "AUTOFRAMEX_ENCRYPTION_KEY environment variable before calling encrypt()/decrypt().");
        }
        return raw;
    }

    /** Derives the AES-256 key from the configured passphrase via PBKDF2WithHmacSHA256. */
    private static SecretKey deriveKey(String passphrase, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(
                passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** SecureRandom is thread-safe; one shared instance beats seeding a new one per call. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] generateSalt() {
        byte[] salt = new byte[PBKDF2_SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return salt;
    }

    private static byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        RANDOM.nextBytes(iv);
        return iv;
    }
}
