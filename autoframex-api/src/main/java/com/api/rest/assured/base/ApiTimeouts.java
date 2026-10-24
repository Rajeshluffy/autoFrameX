package com.api.rest.assured.base;

import java.util.Map;

import com.framework.config.data.ConfigResolver;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;

/**
 * Connect and read timeouts for every HTTP call this module makes.
 *
 * <p>REST Assured sets none, so a server that accepts the connection and then goes quiet blocked the
 * calling test thread until the CI job's own timeout. The defaults are 10 s to connect and 30 s waiting
 * for data; override them per run with {@code -DapiConnectTimeoutMs=...} / {@code -DapiReadTimeoutMs=...}
 * (or the {@code API_CONNECT_TIMEOUT_MS} / {@code API_READ_TIMEOUT_MS} variables), or per spec with
 * {@link RequestSpecBuilder#setTimeouts(int, int)}. A value of 0 or less is ignored: an unbounded wait is
 * exactly what this class exists to prevent.
 */
public final class ApiTimeouts {

    static final int DEFAULT_CONNECT_MS = 10_000;
    static final int DEFAULT_READ_MS = 30_000;

    private static final String CONNECT_PARAM = "http.connection.timeout";
    private static final String READ_PARAM = "http.socket.timeout";

    private ApiTimeouts() {}

    public static int connectMs() {
        return positiveOrDefault(ConfigResolver.resolveInt(
                "apiConnectTimeoutMs", "API_CONNECT_TIMEOUT_MS", null, DEFAULT_CONNECT_MS), DEFAULT_CONNECT_MS);
    }

    public static int readMs() {
        return positiveOrDefault(ConfigResolver.resolveInt(
                "apiReadTimeoutMs", "API_READ_TIMEOUT_MS", null, DEFAULT_READ_MS), DEFAULT_READ_MS);
    }

    /**
     * {@code base} with the framework timeouts added for whichever of the two it does not already set.
     * A timeout someone configured on purpose is left alone.
     *
     * @param base the config to extend; {@code null} means REST Assured's current default config
     */
    public static RestAssuredConfig applyDefaults(RestAssuredConfig base) {
        RestAssuredConfig config = base == null ? RestAssured.config() : base;
        HttpClientConfig http = config.getHttpClientConfig();
        Map<String, ?> params = http.params();
        if (!params.containsKey(CONNECT_PARAM)) {
            http = http.setParam(CONNECT_PARAM, connectMs());
        }
        if (!params.containsKey(READ_PARAM)) {
            http = http.setParam(READ_PARAM, readMs());
        }
        return config.httpClient(http);
    }

    /**
     * {@code base} with the given timeouts (a {@code null} or non-positive value means "not set"),
     * then the framework defaults for anything still missing.
     */
    public static RestAssuredConfig with(RestAssuredConfig base, Integer connectMs, Integer readMs) {
        RestAssuredConfig config = base == null ? RestAssured.config() : base;
        HttpClientConfig http = config.getHttpClientConfig();
        if (connectMs != null && connectMs > 0) {
            http = http.setParam(CONNECT_PARAM, connectMs);
        }
        if (readMs != null && readMs > 0) {
            http = http.setParam(READ_PARAM, readMs);
        }
        return applyDefaults(config.httpClient(http));
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
