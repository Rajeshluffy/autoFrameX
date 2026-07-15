package com.api.rest.assured.base;

import static io.restassured.RestAssured.*;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.Map;

public class RequestAuthentication {

    private String token;
    private String oAuthBaseUrl;
    // Instance-level (not static) — each RequestAuthentication owns its own token
    // state so parallel test threads don't race on a shared expiry timestamp.
    private Instant tokenExpiryTime;
    private static final int TOKEN_VALIDITY_SECONDS = 3600;

    public boolean isTokenValid() {
        return token != null && tokenExpiryTime != null && Instant.now().isBefore(tokenExpiryTime);
    }

    public RequestAuthentication setOAuthBaseUrl(String oAuthBaseUrl) {
        this.oAuthBaseUrl = oAuthBaseUrl;
        return this;
    }

    public String getOAuthToken(String grantType, String clientId, String clientSecret,
            String username, String password) {
        if (!isTokenValid()) {
            token = given()
                    .contentType(ContentType.URLENC)
                    .formParam("grant_type", grantType)
                    .formParam("client_id", clientId)
                    .formParam("client_secret", clientSecret)
                    .formParam("username", username)
                    .formParam("password", password)
                    .when()
                    .post(oAuthBaseUrl)
                    .then()
                    .extract()
                    .path("access_token");
            tokenExpiryTime = Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS);
        }
        return token;
    }

    public String getOAuthToken(String grantType, String clientId, String clientSecret,
            String username, String password, Map<String, String> headers) {
        if (!isTokenValid()) {
            token = given()
                    .contentType(ContentType.URLENC)
                    .headers(headers)
                    .formParam("grant_type", grantType)
                    .formParam("client_id", clientId)
                    .formParam("client_secret", clientSecret)
                    .formParam("username", username)
                    .formParam("password", password)
                    .when()
                    .post(oAuthBaseUrl)
                    .then()
                    .extract()
                    .path("access_token");
            tokenExpiryTime = Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS);
        }
        return token;
    }

    public String refreshOAuthToken(String grantType, String clientId, String clientSecret,
            String refreshToken) {
        token = given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type", grantType)
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .formParam("refresh_token", refreshToken)
                .when()
                .post(oAuthBaseUrl)
                .then()
                .extract()
                .path("access_token");
        tokenExpiryTime = Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS);
        return token;
    }

    public void resetToken() {
        token = null;
        tokenExpiryTime = null;
    }
}
