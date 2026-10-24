package com.api.rest.assured.base;

import static io.restassured.RestAssured.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

/**
 * Fetches and caches an OAuth access token.
 *
 * <p>A token request that does not succeed always throws, with the HTTP status and the
 * server's own {@code error} / {@code error_description}. It never returns {@code null}
 * and never caches a failure: callers used to get {@code null} back from a rejected request
 * and send every later call without credentials.
 *
 * <p>The cache lifetime is the server's {@code expires_in} (minus a safety margin) when it
 * sends one, and one hour otherwise.
 */
public class RequestAuthentication {

    private static final Logger logger = LoggerFactory.getLogger(RequestAuthentication.class);

    /** Used when the token response carries no {@code expires_in}. */
    static final int DEFAULT_TOKEN_VALIDITY_SECONDS = 3600;
    /** A token is treated as expired this long before the server says so, to cover clock drift and request time. */
    static final int EXPIRY_SKEW_SECONDS = 30;
    private static final int BODY_PREVIEW_CHARS = 200;

    private String token;
    private String oAuthBaseUrl;
    // Instance-level (not static) — each RequestAuthentication owns its own token
    // state so parallel test threads don't race on a shared expiry timestamp.
    private Instant tokenExpiryTime;

    public boolean isTokenValid() {
        return token != null && tokenExpiryTime != null && Instant.now().isBefore(tokenExpiryTime);
    }

    public RequestAuthentication setOAuthBaseUrl(String oAuthBaseUrl) {
        this.oAuthBaseUrl = oAuthBaseUrl;
        return this;
    }

    /**
     * @throws IllegalStateException if the token endpoint rejects the request, answers
     *                               something that is not a token response, or no URL is set
     */
    public String getOAuthToken(String grantType, String clientId, String clientSecret,
            String username, String password) {
        return getOAuthToken(grantType, clientId, clientSecret, username, password, Collections.emptyMap());
    }

    /** Same as above, sending {@code headers} with the token request. */
    public String getOAuthToken(String grantType, String clientId, String clientSecret,
            String username, String password, Map<String, String> headers) {
        return getOAuthToken(grantType, clientId, clientSecret, username, password, null, headers);
    }

    /**
     * Same as above, also sending {@code scope} when it is set. Only the fields that have a value are
     * sent: a {@code client_credentials} grant has no user, and the form used to carry a bare
     * {@code username&password} for it, which strict token endpoints reject.
     */
    public String getOAuthToken(String grantType, String clientId, String clientSecret,
            String username, String password, String scope, Map<String, String> headers) {
        if (!isTokenValid()) {
            Map<String, String> form = new LinkedHashMap<>();
            putIfPresent(form, "grant_type", grantType);
            putIfPresent(form, "client_id", clientId);
            putIfPresent(form, "client_secret", clientSecret);
            putIfPresent(form, "username", username);
            putIfPresent(form, "password", password);
            putIfPresent(form, "scope", scope);
            requestToken(form, headers);
        }
        return token;
    }

    private static void putIfPresent(Map<String, String> form, String name, String value) {
        if (value != null && !value.isBlank()) {
            form.put(name, value);
        }
    }

    /** The cached token, valid or not; {@code null} when none. For {@link TokenProvider}. */
    String currentToken() {
        return token;
    }

    /**
     * Exchanges a refresh token for a new access token, always (the cache is not consulted).
     *
     * @throws IllegalStateException on any failure, like {@link #getOAuthToken}
     */
    public String refreshOAuthToken(String grantType, String clientId, String clientSecret,
            String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", grantType);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("refresh_token", refreshToken);
        requestToken(form, Collections.emptyMap());
        return token;
    }

    public void resetToken() {
        token = null;
        tokenExpiryTime = null;
    }

    /** Seconds until the cached token expires; {@code 0} when there is none. */
    long secondsUntilExpiry() {
        if (tokenExpiryTime == null) {
            return 0;
        }
        return Math.max(0, Duration.between(Instant.now(), tokenExpiryTime).getSeconds());
    }

    // -------------------------------------------------------------------------

    private void requestToken(Map<String, String> form, Map<String, String> headers) {
        if (oAuthBaseUrl == null || oAuthBaseUrl.isBlank()) {
            throw new IllegalStateException("OAuth token URL is not set - call setOAuthBaseUrl first");
        }
        resetToken(); // whatever happens next, a stale token must not survive a failed refresh

        Response response = given()
                .config(ApiTimeouts.applyDefaults(null))
                .contentType(ContentType.URLENC)
                .headers(headers == null ? Collections.emptyMap() : headers)
                .formParams(form)
                .when()
                .post(oAuthBaseUrl);

        String body = response.asString();
        JsonPath json = parseJson(body);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OAuth token request to " + oAuthBaseUrl + " failed: HTTP "
                    + response.statusCode() + describeError(json, body));
        }

        String accessToken = json == null ? null : json.getString("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("OAuth token response from " + oAuthBaseUrl
                    + " (HTTP " + response.statusCode() + ") contains no access_token" + describeError(json, body));
        }

        token = accessToken;
        tokenExpiryTime = Instant.now().plusSeconds(validitySeconds(json));
        logger.debug("OAuth token acquired from {}, valid for {}s", oAuthBaseUrl, secondsUntilExpiry());
    }

    /** The server's {@code expires_in} minus the safety margin; one hour when it is absent or unusable. */
    static long validitySeconds(JsonPath json) {
        long expiresIn = -1;
        try {
            String raw = json == null ? null : json.getString("expires_in");
            if (raw != null && !raw.isBlank()) {
                expiresIn = Long.parseLong(raw.trim());
            }
        } catch (RuntimeException e) {
            logger.debug("Ignoring unusable expires_in in the token response: {}", e.toString());
        }
        return validitySeconds(expiresIn);
    }

    static long validitySeconds(long expiresIn) {
        if (expiresIn <= 0) {
            return DEFAULT_TOKEN_VALIDITY_SECONDS;
        }
        // A very short-lived token: use half its life rather than a negative or instantly-expired window.
        return expiresIn > EXPIRY_SKEW_SECONDS ? expiresIn - EXPIRY_SKEW_SECONDS : Math.max(1, expiresIn / 2);
    }

    private static JsonPath parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonPath json = new JsonPath(body);
            json.get("access_token"); // JsonPath parses lazily; this forces the parse
            return json;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@code : error - description} from the server, or a short preview of the body. Never echoes request credentials. */
    private static String describeError(JsonPath json, String body) {
        if (json != null) {
            String error = json.getString("error");
            String description = json.getString("error_description");
            if (error != null || description != null) {
                return ": " + (error == null ? "" : error) + (description == null ? "" : " - " + description);
            }
        }
        if (body == null || body.isBlank()) {
            return " (empty body)";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return ": " + (flat.length() <= BODY_PREVIEW_CHARS ? flat : flat.substring(0, BODY_PREVIEW_CHARS) + "...");
    }
}
