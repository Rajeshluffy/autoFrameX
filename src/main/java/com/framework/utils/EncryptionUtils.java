package com.framework.utils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
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
 * <h3>Format</h3>
 * Encrypted values are Base64-encoded strings containing a 12-byte random IV
 * prepended to the GCM ciphertext: {@code Base64(IV || ciphertext)}.
 */
public final class EncryptionUtils {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionUtils.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // Patterns for masking sensitive values in log strings
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?i)(password|passwd|pwd|secret|token|key|auth)\\s*[=:]\\s*\\S+");
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
     * @return Base64-encoded ciphertext (IV prepended), or {@code null} on error
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

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
     * @return original plain-text value, or {@code null} on error
     */
    public static String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) return encryptedBase64;
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
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
     *   "password=secret123"   → "password=***"
     *   "Bearer eyJhbGci..."   → "Bearer ***"
     * </pre>
     *
     * @param logLine raw log string that may contain sensitive data
     * @return sanitized string safe for logging
     */
    public static String maskSensitiveValues(String logLine) {
        if (logLine == null) return null;
        String masked = PASSWORD_PATTERN.matcher(logLine).replaceAll("$1=***");
        masked = BEARER_PATTERN.matcher(masked).replaceAll("$1***");
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

    private static SecretKey resolveKey() {
        String raw = System.getProperty("autoFrameX.encryption.key");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("AUTOFRAMEX_ENCRYPTION_KEY");
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "No encryption key configured. Set -DautoFrameX.encryption.key=... or the "
                    + "AUTOFRAMEX_ENCRYPTION_KEY environment variable before calling encrypt()/decrypt().");
        }
        // Pad or truncate to exactly 32 bytes for AES-256
        byte[] keyBytes = new byte[32];
        byte[] rawBytes = raw.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(rawBytes, 0, keyBytes, 0, Math.min(rawBytes.length, 32));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
