package com.framework.utils;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Locks in two review findings: (1) a missing encryption key must surface as an
 * {@link IllegalStateException} instead of being swallowed into a {@code null}
 * result, and (2) log masking must cover JSON bodies, JSON-escaped-inside-JSON
 * log lines and Authorization headers without eating neighbouring fields.
 */
public class EncryptionUtilsTest {

    private static final String KEY_PROP = "autoFrameX.encryption.key";
    private String originalKey;

    @BeforeMethod
    public void saveKey() {
        originalKey = System.getProperty(KEY_PROP);
        System.clearProperty(KEY_PROP);
    }

    @AfterMethod(alwaysRun = true)
    public void restoreKey() {
        if (originalKey == null) {
            System.clearProperty(KEY_PROP);
        } else {
            System.setProperty(KEY_PROP, originalKey);
        }
    }

    // ---- key handling ------------------------------------------------------

    @Test
    public void encryptWithoutKeyThrowsInsteadOfReturningNull() {
        skipIfKeyInEnvironment();
        assertThrows(IllegalStateException.class, () -> EncryptionUtils.encrypt("value"));
    }

    @Test
    public void decryptWithoutKeyThrowsInsteadOfReturningNull() {
        skipIfKeyInEnvironment();
        assertThrows(IllegalStateException.class, () -> EncryptionUtils.decrypt("c29tZXRoaW5n"));
    }

    @Test
    public void roundTripRestoresPlainText() {
        System.setProperty(KEY_PROP, "unit-test-passphrase");
        String encrypted = EncryptionUtils.encrypt("s3cr3t value");
        assertNotEquals(encrypted, "s3cr3t value");
        assertEquals(EncryptionUtils.decrypt(encrypted), "s3cr3t value");
    }

    @Test
    public void encryptionIsRandomisedPerCall() {
        System.setProperty(KEY_PROP, "unit-test-passphrase");
        assertNotEquals(EncryptionUtils.encrypt("same"), EncryptionUtils.encrypt("same"));
    }

    @Test
    public void decryptWithDifferentKeyReturnsNull() {
        System.setProperty(KEY_PROP, "key-one");
        String encrypted = EncryptionUtils.encrypt("value");
        System.setProperty(KEY_PROP, "key-two");
        assertNull(EncryptionUtils.decrypt(encrypted));
    }

    // ---- masking -----------------------------------------------------------

    @Test
    public void masksPlainKeyValueForms() {
        assertEquals(EncryptionUtils.maskSensitiveValues("password=secret123"), "password=***");
        assertEquals(EncryptionUtils.maskSensitiveValues("login password: abc next"),
                "login password: *** next");
    }

    @Test
    public void masksQuotedJsonValueIncludingSpaces() {
        assertEquals(
                EncryptionUtils.maskSensitiveValues("{\"password\":\"abc def\",\"user\":\"bob\"}"),
                "{\"password\":\"***\",\"user\":\"bob\"}");
    }

    @Test
    public void masksJsonThatIsEscapedInsideAJsonLogLine() {
        String line = "{\"message\":\"{\\\"password\\\":\\\"abc\\\"}\",\"logger\":\"x\"}";
        String expected = "{\"message\":\"{\\\"password\\\":\\\"***\\\"}\",\"logger\":\"x\"}";
        assertEquals(EncryptionUtils.maskSensitiveValues(line), expected);
    }

    @Test
    public void doesNotSwallowFieldsFollowingTheSecretInACompactJsonLine() {
        assertEquals(
                EncryptionUtils.maskSensitiveValues("{\"message\":\"login password=abc\",\"level\":\"INFO\"}"),
                "{\"message\":\"login password=***\",\"level\":\"INFO\"}");
    }

    @Test
    public void masksAuthorizationHeaderSchemeAndCredentials() {
        assertEquals(EncryptionUtils.maskSensitiveValues("Authorization: Bearer abc.def-ghi"),
                "Authorization: ***");
        assertEquals(EncryptionUtils.maskSensitiveValues("Authorization: Basic dXNlcjpwYXNz"),
                "Authorization: ***");
    }

    @Test
    public void masksStandaloneBearerToken() {
        assertEquals(EncryptionUtils.maskSensitiveValues("sent Bearer eyJhbGciOi.payload"),
                "sent Bearer ***");
    }

    @Test
    public void leavesUnrelatedTextUntouched() {
        String harmless = "Author=Bob opened the page; status=200";
        assertEquals(EncryptionUtils.maskSensitiveValues(harmless), harmless);
        assertNull(EncryptionUtils.maskSensitiveValues(null));
    }

    @Test
    public void masksKeysThatCarryAPrefix() {
        assertEquals(EncryptionUtils.maskSensitiveValues("dbPassword=hunter2"), "dbPassword=***");
        assertEquals(EncryptionUtils.maskSensitiveValues("x-api-key: abc123"), "x-api-key: ***");
        assertEquals(EncryptionUtils.maskSensitiveValues("{\"api.token\":\"abc\"}"), "{\"api.token\":\"***\"}");
    }

    /**
     * The key pattern used to be tried from every position of a long run of word characters, and each
     * attempt scanned to the end of the run: 20,000 characters took about 16 seconds, per log line.
     * A long base64 screenshot or id in a log line must not stall the test run.
     */
    @Test(timeOut = 10_000)
    public void aLongUnbrokenRunIsMaskedInLinearTime() {
        String longRun = "x".repeat(200_000);
        long started = System.nanoTime();
        String masked = EncryptionUtils.maskSensitiveValues(longRun + " password=abc");
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertTrue(masked.endsWith("password=***"), "the secret after the long run is still masked");
        assertTrue(elapsedMs < 3_000, "200,000 characters took " + elapsedMs + " ms");
    }

    @Test(timeOut = 10_000)
    public void aLongRunInsideAJsonBodyDoesNotHideOrDelayTheSecret() {
        String body = "{\"image\":\"" + "A".repeat(100_000) + "\",\"token\":\"abc123\"}";
        String masked = EncryptionUtils.maskSensitiveValues(body);
        assertTrue(masked.contains("\"token\":\"***\""), masked.substring(masked.length() - 40));
        assertFalse(masked.contains("abc123"));
    }

    private static void skipIfKeyInEnvironment() {
        String env = System.getenv("AUTOFRAMEX_ENCRYPTION_KEY");
        if (env != null && !env.isBlank()) {
            throw new SkipException("AUTOFRAMEX_ENCRYPTION_KEY is set in the environment");
        }
    }
}
