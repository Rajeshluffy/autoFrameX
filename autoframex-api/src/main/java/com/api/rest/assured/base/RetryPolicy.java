package com.api.rest.assured.base;

import java.util.EnumSet;
import java.util.Set;

import com.api.design.ApiMethod;
import com.api.design.ResponseAPI;
import com.framework.config.data.ConfigResolver;

/**
 * When the client repeats a call that hit a transient problem: an overloaded or restarting service
 * (502, 503, 504), rate limiting (429), or a connection that failed before any response came back.
 *
 * <p>Off by default. Repeating a call is only safe for calls that do not change anything, so a policy
 * covers {@code GET}, {@code HEAD} and {@code OPTIONS} only; a {@code POST} is never repeated by it (the one
 * exception in this module is the single retry after a 401, which means the server did not act on the
 * request). Turn it on for a run with {@code -DapiRetryMax=2}, or for one call with
 * {@code client.send(builder, request, RetryPolicy.forSafeCalls(2))}.
 *
 * <p>Waiting between attempts is exponential (500 ms, 1 s, 2 s, ... up to 10 s) unless the server says how
 * long with a {@code Retry-After} header in seconds, which is honored up to the same 10 s cap.
 */
public final class RetryPolicy {

    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(429, 502, 503, 504);
    private static final Set<ApiMethod> SAFE_METHODS = EnumSet.of(ApiMethod.GET, ApiMethod.HEAD, ApiMethod.OPTIONS);

    static final long DEFAULT_BASE_DELAY_MS = 500;
    static final long MAX_DELAY_MS = 10_000;

    private final int maxRetries;
    private final long baseDelayMs;

    private RetryPolicy(int maxRetries, long baseDelayMs) {
        this.maxRetries = Math.max(0, maxRetries);
        this.baseDelayMs = Math.max(0, baseDelayMs);
    }

    /** Never repeat a call. */
    public static RetryPolicy none() {
        return new RetryPolicy(0, 0);
    }

    /** Repeat GET, HEAD and OPTIONS up to {@code maxRetries} times after a transient failure. */
    public static RetryPolicy forSafeCalls(int maxRetries) {
        return new RetryPolicy(maxRetries, DEFAULT_BASE_DELAY_MS);
    }

    /** Like {@link #forSafeCalls(int)} with your own first delay (the later ones double). */
    public static RetryPolicy forSafeCalls(int maxRetries, long baseDelayMs) {
        return new RetryPolicy(maxRetries, baseDelayMs);
    }

    /** From {@code -DapiRetryMax} / {@code API_RETRY_MAX} (default 0 = off) and {@code -DapiRetryBaseMs}. */
    public static RetryPolicy fromConfig() {
        int max = ConfigResolver.resolveInt("apiRetryMax", "API_RETRY_MAX", null, 0);
        long base = ConfigResolver.resolveInt("apiRetryBaseMs", "API_RETRY_BASE_MS", null, (int) DEFAULT_BASE_DELAY_MS);
        return max <= 0 ? none() : new RetryPolicy(max, base);
    }

    public int maxRetries() {
        return maxRetries;
    }

    /**
     * Whether to repeat the call.
     *
     * @param attempt  how many repeats have already been made (0 for the first attempt's outcome)
     * @param response what came back, or {@code null} if there was no response
     * @param failure  why there was no response, or {@code null} if there was one
     */
    public boolean shouldRetry(ApiMethod method, int attempt, ResponseAPI response, ApiCallException failure) {
        if (attempt >= maxRetries || !SAFE_METHODS.contains(method)) {
            return false;
        }
        if (failure != null) {
            return true;
        }
        return response != null && TRANSIENT_STATUSES.contains(response.getStatusCode());
    }

    /** How long to wait before the next attempt. */
    public long delayMs(int attempt, ResponseAPI response) {
        if (response != null) {
            long fromServer = retryAfterMs(response.getHeader("Retry-After"));
            if (fromServer >= 0) {
                return Math.min(fromServer, MAX_DELAY_MS);
            }
        }
        long exponential = baseDelayMs << Math.min(attempt, 10);
        return Math.min(exponential, MAX_DELAY_MS);
    }

    /** {@code Retry-After} in seconds as milliseconds; -1 for an absent or unusable value (a date is ignored). */
    static long retryAfterMs(String header) {
        if (header == null || header.isBlank()) {
            return -1;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds < 0 ? -1 : seconds * 1000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Sleeps; if interrupted, keeps the interrupt flag and returns at once. */
    static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
