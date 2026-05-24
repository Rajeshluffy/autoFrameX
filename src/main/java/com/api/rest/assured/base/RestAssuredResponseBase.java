package com.api.rest.assured.base;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

import java.util.HashMap;
import java.util.Map;

import com.api.design.ResponseAPI;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;

public class RestAssuredResponseBase implements ResponseAPI {

    private final Response response;

    public RestAssuredResponseBase(Response response) {
        this.response = response;
    }

    @Override
    public int getStatusCode() {
        return response.getStatusCode();
    }

    @Override
    public String getStatusMessage() {
        String[] parts = response.getStatusLine().split(" ", 3);
        return parts[parts.length - 1];
    }

    @Override
    public String getBody() {
        return response.asString();
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> map = new HashMap<>();
        Headers headers = response.getHeaders();
        for (Header header : headers) {
            map.put(header.getName(), header.getValue());
        }
        return map;
    }

    @Override
    public String getContentType() {
        String ct = response.getContentType();
        if (ct == null || ct.isBlank()) return "";
        return ct.split(";")[0].trim();
    }

    @Override
    public <T> T getBodyAs(Class<T> clazz) {
        // RestAssured uses Jackson internally when jackson-databind is on the classpath
        return response.as(clazz);
    }

    @Override
    public String getJsonPath(String path) {
        // Guard: attempting to parse HTML (or any non-JSON body) as JSON produces a
        // cryptic Groovy JsonException.  Catch the bad state early and surface:
        //  - the actual HTTP status code
        //  - the Content-Type header
        //  - the first 400 characters of the body
        // so the developer can distinguish auth failures, hibernated instances,
        // wrong URLs, etc. without decoding a Groovy stack trace.
        String ct = getContentType();
        if (!ct.toLowerCase().contains("json")) {
            String body = getBody();
            String preview = body.length() > 400 ? body.substring(0, 400) + "…" : body;
            throw new IllegalStateException(
                    "Cannot read JSON path '" + path + "' — response is not JSON.\n"
                    + "  Status      : " + getStatusCode() + " " + getStatusMessage() + "\n"
                    + "  Content-Type: " + (ct.isEmpty() ? "(none)" : ct) + "\n"
                    + "  Body preview: " + preview + "\n"
                    + "Common causes:\n"
                    + "  1. Authentication failed → check credentials in "
                    +      "service.now.app.config.properties\n"
                    + "  2. ServiceNow instance is hibernating → "
                    +      "wake it at developer.servicenow.com\n"
                    + "  3. Wrong base URI or base path in the properties file");
        }
        return response.jsonPath().getString(path);
    }

    @Override
    public void verifySchema(String schemaFilePath) {
        response.then().assertThat().body(matchesJsonSchemaInClasspath(schemaFilePath));
    }
}
