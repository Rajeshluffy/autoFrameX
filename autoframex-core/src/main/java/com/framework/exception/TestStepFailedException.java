package com.framework.exception;

/**
 * Thrown by {@link com.framework.utils.Reporter#reportStep} when a step is
 * reported with {@code "FAIL"} status.
 *
 * <p>Previously a bare {@link RuntimeException} — callers couldn't distinguish
 * "this test step was reported as failed" from any other unchecked exception
 * in a catch block.
 */
public class TestStepFailedException extends FrameworkException {

    private static final long serialVersionUID = 1L;

    public TestStepFailedException(String message) {
        super(message);
    }
}
