package com.api.rest.assured.base;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import io.restassured.common.mapper.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

import java.util.HashMap;
import java.util.Map;

import com.api.design.ResponseAPI;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;

public class RestAssuredResponseBase implements ResponseAPI {

    private static final ObjectMapper LENIENT = new ObjectMapper().findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Response response;
    private final String requestLine;
    private final String curl;

    public RestAssuredResponseBase(Response response) {
        this(response, null);
    }

    public RestAssuredResponseBase(Response response, String requestLine) {
        this(response, requestLine, null);
    }

    public RestAssuredResponseBase(Response response, String requestLine, String curl) {
        this.response = response;
        this.requestLine = requestLine == null ? "(request not recorded)" : requestLine;
        this.curl = curl;
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
        // Case-insensitive, and a repeated header keeps every value: a plain HashMap<name, value> made
        // get("content-type") miss "Content-Type" and silently kept only the last Set-Cookie.
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Headers headers = response.getHeaders();
        for (Header header : headers) {
            map.merge(header.getName(), header.getValue(), (first, next) -> first + ", " + next);
        }
        return map;
    }

    @Override
    public String getHeader(String name) {
        List<String> values = getHeaderValues(name);
        return values.isEmpty() ? null : String.join(", ", values);
    }

    @Override
    public List<String> getHeaderValues(String name) {
        return new ArrayList<>(response.getHeaders().getValues(name));
    }

    @Override
    public String getContentType() {
        String ct = response.getContentType();
        if (ct == null || ct.isBlank()) return "";
        return ct.split(";")[0].trim();
    }

    @Override
    public <T> T getBodyAs(Class<T> clazz) {
        return read(clazz.getName(), () -> LENIENT.readValue(getBody(), clazz));
    }

    @Override
    public String getJsonPath(String path) {
        requireJson(path);
        return response.jsonPath().getString(path);
    }

    @Override
    public <T> T path(String jsonPath, Class<T> type) {
        requireJson(jsonPath);
        if (type == Object.class) {
            return type.cast(response.jsonPath().get(jsonPath));
        }
        return response.jsonPath().getObject(jsonPath, type);
    }

    @Override
    public <T> List<T> pathList(String jsonPath, Class<T> elementType) {
        requireJson(jsonPath);
        List<T> list = response.jsonPath().getList(jsonPath, elementType);
        return list == null ? Collections.emptyList() : list;
    }

    @Override
    public <T> T as(TypeRef<T> type) {
        return read(type.getType().getTypeName(),
                () -> LENIENT.readValue(getBody(), LENIENT.getTypeFactory().constructType(type.getType())));
    }

    /**
     * Reads the body with Jackson, ignoring fields the target type does not have: a service adding a field
     * must not break every test that reads the fields it knows. A failure says what was being read and from
     * which call, with the start of the body (credentials masked), not just Jackson's position in the text.
     */
    private <T> T read(String target, BodyReader<T> reader) {
        try {
            return reader.read();
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage().lines().findFirst().orElse("");
            throw new IllegalStateException("Cannot read the response body as " + target + ": " + reason + "\n"
                    + "  Request     : " + requestLine + "\n"
                    + "  Status      : " + getStatusCode() + " " + getStatusMessage() + "\n"
                    + "  Content-Type: " + (getContentType().isEmpty() ? "(none)" : getContentType()) + "\n"
                    + "  Body preview: " + ApiRedaction.preview(getBody(), 400), e);
        }
    }

    @FunctionalInterface
    private interface BodyReader<T> {
        T read() throws Exception;
    }

    @Override
    public long getResponseTimeMs() {
        return response.time();
    }

    @Override
    public Map<String, String> getCookies() {
        return new HashMap<>(response.getCookies());
    }

    @Override
    public byte[] getBodyBytes() {
        return response.asByteArray();
    }

    @Override
    public String describeRequest() {
        return requestLine;
    }

    @Override
    public String reproCommand() {
        return curl;
    }

    /**
     * Reading a JSON path from an HTML login page or an empty body fails inside Groovy with an unreadable
     * exception. Stop early and say what actually came back (status, content type, the start of the body with
     * credentials masked), so a rejected login, a wrong URL or a gateway error page is obvious.
     */
    private void requireJson(String path) {
        String contentType = getContentType();
        if (!contentType.toLowerCase().contains("json")) {
            throw new IllegalStateException(
                    "Cannot read JSON path '" + path + "' - the response is not JSON.\n"
                    + "  Request     : " + requestLine + "\n"
                    + "  Status      : " + getStatusCode() + " " + getStatusMessage() + "\n"
                    + "  Content-Type: " + (contentType.isEmpty() ? "(none)" : contentType) + "\n"
                    + "  Body preview: " + ApiRedaction.preview(getBody(), 400) + "\n"
                    + "Common causes: the credentials were rejected, the base URI or path is wrong, the "
                    + "service is asleep or redirected to a login page, or a gateway answered with its own "
                    + "error page.");
        }
    }

    @Override
    public void verifySchema(String schemaFilePath) {
        response.then().assertThat().body(matchesJsonSchemaInClasspath(schemaFilePath));
    }
}
