package com.framework.utils;

/**
 * The one thing the page-action helpers (click, type, wait, ...) need from a
 * test's {@link Reporter}: a way to log a step. Depending on this instead of the
 * whole Reporter keeps them from holding on to a large, stateful object they only
 * ever call {@code reportStep} on, and lets them be tested with a one-line stub.
 */
public interface StepReporter {

    /** Logs a step, optionally with a screenshot. {@code status} is "pass", "fail", "warning" or "info". */
    void reportStep(String desc, String status, boolean bSnap);

    /** Logs a step with a screenshot. */
    void reportStep(String desc, String status);
}
