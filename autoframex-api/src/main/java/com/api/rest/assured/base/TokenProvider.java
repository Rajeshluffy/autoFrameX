package com.api.rest.assured.base;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * One OAuth token cache for the whole JVM, keyed by {@link OAuthSettings#cacheKey()}.
 *
 * <p>Each {@code RequestSpecBuilder} used to own its own {@link RequestAuthentication}, so the cache could
 * never hit: a service object created per scenario asked the token endpoint for a new token every time (and
 * kept it, expired or not, for its whole life). Here every builder asks the provider, so a run makes one
 * token request per distinct client and user until the token nears its expiry; threads asking for the same
 * token at the same moment wait for one request instead of making one each.
 *
 * <p>A request that failed with 401 calls {@link #invalidate} with the token it sent, which drops it only if
 * it is still the cached one: when several threads are rejected together, the first replaces the token and the
 * others reuse the new one.
 *
 * <p>All state is static and the class has no instance to hand out, so no caller can hold on to the cache.
 */
public final class TokenProvider {

    private static final ConcurrentMap<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private TokenProvider() {}

    /** The auth object for one key. All access is under its own monitor: {@link RequestAuthentication} is not thread-safe. */
    private static final class Entry {
        final RequestAuthentication auth = new RequestAuthentication();
    }

    /**
     * A valid token for these settings: the cached one while it has more than a short margin left, otherwise
     * a new one.
     *
     * @throws IllegalStateException with the server's reason when the token request is rejected
     */
    public static String token(OAuthSettings settings) {
        Entry entry = ENTRIES.computeIfAbsent(settings.cacheKey(), k -> new Entry());
        synchronized (entry) {
            entry.auth.setOAuthBaseUrl(settings.tokenUrl());
            return entry.auth.getOAuthToken(settings.grantType(), settings.clientId(), settings.clientSecret(),
                    settings.username(), settings.password(), settings.scope(), Collections.emptyMap());
        }
    }

    /**
     * Forgets the cached token if it is {@code rejectedToken} (or any token when that is {@code null}), so the
     * next {@link #token} call fetches a new one.
     */
    public static void invalidate(OAuthSettings settings, String rejectedToken) {
        Entry entry = ENTRIES.get(settings.cacheKey());
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (rejectedToken == null || rejectedToken.equals(entry.auth.currentToken())) {
                entry.auth.resetToken();
            }
        }
    }

    /** Drops every cached token. For tests and for a run that switches accounts. */
    public static void clear() {
        ENTRIES.clear();
    }
}
