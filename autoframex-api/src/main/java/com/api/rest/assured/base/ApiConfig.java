package com.api.rest.assured.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.framework.config.data.ConfigResolver;
import com.framework.exception.FrameworkException;

import io.restassured.http.ContentType;

/**
 * The API under test, described through the framework's normal configuration chain: TestNG parameter, then
 * environment variable, then {@code -D} system property. A project no longer needs its own properties class
 * and hand-written plumbing to say where the API is and how to sign in; Jenkins sets environment variables
 * (secrets from its credential store), a developer sets {@code -D} flags, and the code is the same:
 * <pre>
 * RequestSpecBuilder base = ApiConfig.load().requestSpecBuilder();
 * ResponseAPI response = new RestAssuredBase().send(base, ApiRequest.get("incident"));
 * </pre>
 *
 * <table>
 *   <caption>Settings (TestNG parameter / {@code -D} name, environment variable)</caption>
 *   <tr><td>{@code apiBaseUri}</td><td>{@code API_BASE_URI}</td><td>required, e.g. {@code https://host}</td></tr>
 *   <tr><td>{@code apiBasePath}</td><td>{@code API_BASE_PATH}</td><td>prefix of every call, e.g. {@code /api/now/table}</td></tr>
 *   <tr><td>{@code apiOAuthBasePath}</td><td>{@code API_OAUTH_BASE_PATH}</td><td>token endpoint, e.g. {@code /oauth_token.do}</td></tr>
 *   <tr><td>{@code apiGrantType}</td><td>{@code API_GRANT_TYPE}</td><td>default {@code client_credentials}</td></tr>
 *   <tr><td>{@code apiClientId}</td><td>{@code API_CLIENT_ID}</td><td>turns on OAuth</td></tr>
 *   <tr><td>{@code apiClientSecret}</td><td>{@code API_CLIENT_SECRET}</td><td>secret</td></tr>
 *   <tr><td>{@code apiUsername}</td><td>{@code API_USERNAME}</td><td>OAuth {@code password} grant, or Basic auth</td></tr>
 *   <tr><td>{@code apiPassword}</td><td>{@code API_PASSWORD}</td><td>secret</td></tr>
 *   <tr><td>{@code apiScope}</td><td>{@code API_SCOPE}</td><td>optional OAuth scope</td></tr>
 *   <tr><td>{@code apiBearerToken}</td><td>{@code API_BEARER_TOKEN}</td><td>a ready-made token; secret</td></tr>
 * </table>
 *
 * <p>One sign-in scheme is used, in this order: a bearer token, OAuth (when a client id is set), Basic
 * (username and password), or none. A half-filled scheme (a client id without its secret) fails with the
 * names of the missing settings rather than sending requests that will be rejected.
 *
 * <p>Put secrets in environment variables, not {@code -D}: system properties are visible in the process list.
 * {@link #toString()} never prints a secret.
 */
public final class ApiConfig {

    private static final String DEFAULT_GRANT = "client_credentials";

    private final String baseUri;
    private final String basePath;
    private final String oauthBasePath;
    private final String grantType;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final String scope;
    private final String bearerToken;

    private ApiConfig(Map<String, String> params) {
        this.baseUri = trimTrailingSlashes(read("apiBaseUri", "API_BASE_URI", params));
        this.basePath = read("apiBasePath", "API_BASE_PATH", params);
        this.oauthBasePath = read("apiOAuthBasePath", "API_OAUTH_BASE_PATH", params);
        String grant = read("apiGrantType", "API_GRANT_TYPE", params);
        this.grantType = grant == null ? DEFAULT_GRANT : grant;
        this.clientId = read("apiClientId", "API_CLIENT_ID", params);
        this.clientSecret = read("apiClientSecret", "API_CLIENT_SECRET", params);
        this.username = read("apiUsername", "API_USERNAME", params);
        this.password = read("apiPassword", "API_PASSWORD", params);
        this.scope = read("apiScope", "API_SCOPE", params);
        this.bearerToken = read("apiBearerToken", "API_BEARER_TOKEN", params);
    }

    /** Reads the settings from the environment and system properties. */
    public static ApiConfig load() {
        return new ApiConfig(null);
    }

    /** Reads the settings, letting TestNG {@code <parameter>}s (or any map) override the environment. */
    public static ApiConfig load(Map<String, String> params) {
        return new ApiConfig(params);
    }

    private static String read(String paramKey, String envKey, Map<String, String> params) {
        return ConfigResolver.resolveString(paramKey, envKey, params, null);
    }

    /** {@code https://host/} and {@code https://host} are the same base; a trailing slash made {@code //} in token URLs. */
    private static String trimTrailingSlashes(String uri) {
        if (uri == null) {
            return null;
        }
        int end = uri.length();
        while (end > 0 && uri.charAt(end - 1) == '/') {
            end--;
        }
        return uri.substring(0, end);
    }

    public String baseUri()  { return baseUri; }
    public String basePath() { return basePath; }

    /** Which sign-in scheme the settings select: {@code bearer}, {@code oauth}, {@code basic} or {@code none}. */
    public String authentication() {
        if (bearerToken != null) {
            return "bearer";
        }
        if (clientId != null) {
            return "oauth";
        }
        if (username != null || password != null) {
            return "basic";
        }
        return "none";
    }

    /**
     * A builder holding the service-wide settings: base URI and path, Accept: JSON, and the sign-in scheme.
     * For OAuth the first token is fetched here (and shared across the run by the token cache), so wrong
     * credentials fail at set-up with the server's own reason.
     *
     * @throws FrameworkException when the base URI is missing, or a sign-in scheme is only partly configured
     */
    public RequestSpecBuilder requestSpecBuilder() {
        if (baseUri == null) {
            throw new FrameworkException("No API base URI configured. Set 'apiBaseUri' (TestNG parameter or "
                    + "-DapiBaseUri=...) or the API_BASE_URI environment variable.");
        }
        RequestSpecBuilder builder = new RequestSpecBuilder().setBaseUri(baseUri).setAccept(ContentType.JSON);
        if (basePath != null) {
            builder.setBasePath(basePath);
        }

        switch (authentication()) {
            case "bearer":
                builder.setBearerToken(bearerToken);
                break;
            case "oauth":
                requireOAuthSettings();
                builder.setOAuthScope(scope);
                builder.setOAuth(baseUri, oauthBasePath, grantType, clientId, clientSecret, username, password);
                break;
            case "basic":
                requireAll("Basic authentication", missingBasic());
                // Preemptive: an API does not normally answer 401 with a challenge first, so waiting for one
                // would send the request with no credentials at all.
                builder.setPreemptive(true).setBasicAuth(username, password);
                break;
            default:
                break;
        }
        return builder;
    }

    private void requireOAuthSettings() {
        List<String> missing = new ArrayList<>();
        if (clientSecret == null) {
            missing.add("apiClientSecret / API_CLIENT_SECRET");
        }
        if (oauthBasePath == null) {
            missing.add("apiOAuthBasePath / API_OAUTH_BASE_PATH");
        }
        if ("password".equalsIgnoreCase(grantType)) {
            if (username == null) {
                missing.add("apiUsername / API_USERNAME (the 'password' grant needs it)");
            }
            if (password == null) {
                missing.add("apiPassword / API_PASSWORD (the 'password' grant needs it)");
            }
        }
        requireAll("OAuth (apiClientId is set)", missing);
    }

    private List<String> missingBasic() {
        List<String> missing = new ArrayList<>();
        if (username == null) {
            missing.add("apiUsername / API_USERNAME");
        }
        if (password == null) {
            missing.add("apiPassword / API_PASSWORD");
        }
        return missing;
    }

    private static void requireAll(String scheme, List<String> missing) {
        if (!missing.isEmpty()) {
            throw new FrameworkException(scheme + " is only partly configured. Also set: " + String.join("; ", missing));
        }
    }

    /** Never contains a secret: those are shown only as {@code <set>}. */
    @Override
    public String toString() {
        return "ApiConfig{baseUri=" + baseUri + ", basePath=" + basePath + ", authentication=" + authentication()
                + ", oauthBasePath=" + oauthBasePath + ", grantType=" + grantType
                + ", clientId=" + (clientId == null ? "-" : "<set>")
                + ", clientSecret=" + (clientSecret == null ? "-" : "<set>")
                + ", username=" + (username == null ? "-" : "<set>")
                + ", password=" + (password == null ? "-" : "<set>")
                + ", bearerToken=" + (bearerToken == null ? "-" : "<set>") + "}";
    }
}
