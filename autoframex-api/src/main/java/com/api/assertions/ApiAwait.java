package com.api.assertions;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.api.design.ResponseAPI;
import com.api.rest.assured.base.ApiCallException;

/**
 * Waits for an API to reach a state, instead of asserting on the first response or sleeping a fixed time.
 *
 * <p>Many services answer a create before the record is searchable, and queue work for later. A test that
 * fetches straight after the create is flaky; a {@code Thread.sleep(5000)} is slow when it works and still
 * flaky when it does not. This calls again at a short interval and returns as soon as the condition holds:
 * <pre>
 * ResponseAPI found = ApiAwait.until("incident " + number + " is searchable",
 *         () -&gt; service.fetchByNumber(number),
 *         r -&gt; r.getStatusCode() == 200 &amp;&amp; !r.path("result", java.util.List.class).isEmpty(),
 *         Duration.ofSeconds(20));
 * </pre>
 *
 * <p>A call that fails with no response ({@link ApiCallException}) counts as "not yet", since the service
 * may be restarting; if it never recovers the failure message says so. When time runs out the
 * {@link AssertionError} carries the last response (request, status, masked body) like any other assertion.
 */
public final class ApiAwait {

    static final Duration DEFAULT_INTERVAL = Duration.ofMillis(500);

    private ApiAwait() {}

    public static ResponseAPI until(String description, Supplier<ResponseAPI> call, Predicate<ResponseAPI> done,
            Duration timeout) {
        return until(description, call, done, timeout, DEFAULT_INTERVAL);
    }

    public static ResponseAPI until(String description, Supplier<ResponseAPI> call, Predicate<ResponseAPI> done,
            Duration timeout, Duration interval) {
        long deadline = System.nanoTime() + timeout.toNanos();
        int attempts = 0;
        ResponseAPI last = null;
        ApiCallException lastFailure = null;

        while (true) {
            attempts++;
            try {
                last = call.get();
                lastFailure = null;
                if (done.test(last)) {
                    return last;
                }
            } catch (ApiCallException e) {
                lastFailure = e;
            }

            if (System.nanoTime() + interval.toNanos() > deadline) {
                throw timedOut(description, timeout, attempts, last, lastFailure);
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for: " + description, e);
            }
        }
    }

    /** Waits until the call answers with this status. */
    public static ResponseAPI untilStatus(String description, Supplier<ResponseAPI> call, int status, Duration timeout) {
        return until(description + " (status " + status + ")", call, r -> r.getStatusCode() == status, timeout);
    }

    private static AssertionError timedOut(String description, Duration timeout, int attempts, ResponseAPI last,
            ApiCallException lastFailure) {
        String headline = "Timed out after " + timeout.toMillis() + " ms (" + attempts + " attempts) waiting for: "
                + description;
        if (last == null) {
            return new AssertionError(headline + "\n  No response was ever received"
                    + (lastFailure == null ? "" : ": " + lastFailure.getMessage()), lastFailure);
        }
        AssertionError error = ApiAssertions.failureFor(last, headline + "\n  Last response:");
        if (lastFailure != null) {
            error.addSuppressed(lastFailure);
        }
        return error;
    }
}
