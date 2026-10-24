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

    /** The call this thread made last, so the client can attach it to the response for diagnostics. */
    private static final ThreadLocal<String> LAST_REQUEST = new ThreadLocal<>();

    /** The masked {@code METHOD uri} of the call this thread just made, then forgets it. */
    static String consumeLastRequestLine() {
        String line = LAST_REQUEST.get();
        LAST_REQUEST.remove();
        return line;
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
            FilterableResponseSpecification responseSpec, FilterContext ctx) {

        long startNanos = PerfClock.start();
        // The Extent report and exception messages are not behind the logging layer's masking, and a
        // URI can carry ?access_token=...; mask it once here.
        String requestLine = ApiRedaction.mask(requestSpec.getMethod() + " " + requestSpec.getURI());
        LAST_REQUEST.set(requestLine);
        Response response;
        try {
            response = ctx.next(requestSpec, responseSpec);
        } catch (Exception e) {
            // No response at all (refused, reset, timed out). Nothing was logged or reported for it, and
            // the raw exception did not say which call it was or how long it ran.
            long failedAfterMs = PerfClock.elapsedMs(startNanos);
            Reporter.reportApiStep(String.format("API %s — FAILED after %dms: %s",
                    requestLine, failedAfterMs, e.getClass().getSimpleName()), "warning");
            throw new ApiCallException(requestSpec.getMethod(), ApiRedaction.mask(requestSpec.getURI()),
                    failedAfterMs, e);
        }
        long elapsedMs = PerfClock.elapsedMs(startNanos);
        Reporter.reportApiStep(String.format("API %s — %dms (status %d)",
                requestLine, elapsedMs, response.getStatusCode()),
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
