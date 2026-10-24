package com.api.design;

import io.restassured.common.mapper.TypeRef;
import java.util.List;
import java.util.Map;

public interface ResponseAPI {

    int getStatusCode();
    String getStatusMessage();
    String getBody();
    /**
     * All response headers, looked up without regard to case (HTTP header names are case-insensitive).
     * A header sent more than once has its values joined with {@code ", "}; use
     * {@link #getHeaderValues(String)} to get them separately (e.g. several {@code Set-Cookie}).
     */
    Map<String, String> getHeaders();

    /** The named header (case-insensitive), or {@code null} when the response has none. */
    String getHeader(String name);

    /** Every value of the named header (case-insensitive), in the order received; empty when absent. */
    List<String> getHeaderValues(String name);
    String getContentType();

    /**
     * Deserializes the response body to the given class using Jackson.
     * Example: response.getBodyAs(IncidentResponse.class)
     */
    <T> T getBodyAs(Class<T> clazz);

    /**
     * Extracts a scalar value from the response body using a GPath/JsonPath expression.
     * Example: response.getJsonPath("result.sys_id")
     */
    String getJsonPath(String path);

    /**
     * Validates the response body against the JSON schema at the given classpath location.
     * Example: response.verifySchema("schemas/incident_create_response_schema.json")
     */
    void verifySchema(String schemaFilePath);

    /**
     * The value at a JSON path, with its type kept: a number is read as a number, not its text. Missing path
     * gives {@code null}. {@code Object.class} returns the natural type (Number, String, List, Map).
     */
    <T> T path(String jsonPath, Class<T> type);

    /** The array at a JSON path as a list of {@code elementType}; empty when the path is missing. */
    <T> List<T> pathList(String jsonPath, Class<T> elementType);

    /** The body as a generic type, e.g. {@code response.as(new TypeRef<List<Incident>>() {})}. */
    <T> T as(TypeRef<T> type);

    /** How long the call took, in milliseconds. */
    long getResponseTimeMs();

    Map<String, String> getCookies();

    /** The raw body, for downloads and other non-text content. */
    byte[] getBodyBytes();

    /** {@code METHOD uri} of the call that produced this response, credentials masked; for diagnostics. */
    String describeRequest();

    /**
     * A {@code curl} command that repeats the call, credentials replaced; {@code null} when the client did not
     * record it. Shown in assertion failures so a red test can be reproduced from a terminal.
     */
    default String reproCommand() {
        return null;
    }
}
