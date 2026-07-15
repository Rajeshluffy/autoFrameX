package com.api.rest.assured.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

public class RestAssuredListener implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestAssuredListener.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
            FilterableResponseSpecification responseSpec, FilterContext ctx) {

        Response response = ctx.next(requestSpec, responseSpec);

        LOGGER.info(String.join("\n",
                "============ Request ============",
                "Method  : " + requestSpec.getMethod(),
                "URI     : " + requestSpec.getURI(),
                "Headers : " + requestSpec.getHeaders().asList(),
                "Payload : " + prettyPrint(requestSpec.getBody()),
                "================================="));

        LOGGER.info(String.join("\n",
                "============ Response ============",
                "Status  : " + response.getStatusLine(),
                "C-Type  : " + response.getHeader("Content-Type"),
                "Body    :",
                response.getBody().asPrettyString(),
                "=================================="));

        return response;
    }

    private String prettyPrint(Object body) {
        if (body == null) return "NULL";
        try {
            Object parsed = MAPPER.readValue(body.toString(), Object.class);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return body.toString();
        }
    }
}
