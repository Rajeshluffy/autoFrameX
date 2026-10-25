package com.framework.observability;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

/**
 * The test-event file is archived and shipped, and its failure message is whatever the exception said.
 * Credentials in that message must not reach the file.
 */
public class TestEventMaskingTest {

    private static String failureMessageOf(String raw) {
        return TestEvent.builder().testId("t").failureMessage(raw).build().getFailureMessage();
    }

    @Test
    public void aCredentialInAFailureMessageIsMasked() {
        String message = failureMessageOf("Login failed: password=hunter2hunter2 for user bob");
        assertFalse(message.contains("hunter2hunter2"), message);
        assertTrue(message.contains("password=***"), message);
        assertTrue(message.contains("user bob"), "the rest of the message is kept: " + message);
    }

    @Test
    public void aTokenInAUrlAndABearerHeaderAreMasked() {
        String message = failureMessageOf("GET https://host/api?access_token=ABCDEF123456 failed; sent Bearer eyJhbGciOi.payload");
        assertFalse(message.contains("ABCDEF123456"), message);
        assertFalse(message.contains("eyJhbGciOi.payload"), message);
    }

    @Test
    public void aMessageWithoutCredentialsIsUnchangedAndNullStaysNull() {
        assertEquals(failureMessageOf("expected [200] but found [404]"), "expected [200] but found [404]");
        assertNull(failureMessageOf(null));
    }
}
