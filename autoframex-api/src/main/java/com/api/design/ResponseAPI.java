package com.api.design;

import java.util.Map;

public interface ResponseAPI {

    int getStatusCode();
    String getStatusMessage();
    String getBody();
    Map<String, String> getHeaders();
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
