package com.api.rest.assured.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.utils.PerfClock;
import com.framework.utils.Reporter;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * REST Assured {@link Filter} registered on every request built via
 * {@code RestAssuredBase.given()} — the single choke point every API verb
 * (get/post/put/delete) passes through, which makes this the natural place
 * for automatic per-call timing capture (framework-3.1 performance-capture
 * design): every call the test issues gets one Extent report line with its
 * elapsed time, with no change needed to test code.
 */
public class RestAssuredListener implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestAssuredListener.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
            FilterableResponseSpecification responseSpec, FilterContext ctx) {

        long startNanos = PerfClock.start();
        Response response = ctx.next(requestSpec, responseSpec);
        long elapsedMs = PerfClock.elapsedMs(startNanos);

        Reporter.reportApiStep(String.format("API %s %s — %dms (status %d)",
                requestSpec.getMethod(), requestSpec.getURI(), elapsedMs, response.getStatusCode()),
                response.getStatusCode() < 400 ? "info" : "warning");

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
