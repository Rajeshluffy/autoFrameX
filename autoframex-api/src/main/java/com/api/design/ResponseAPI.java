package com.api.design;

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
}
