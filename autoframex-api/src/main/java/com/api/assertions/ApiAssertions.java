package com.api.assertions;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import com.api.design.ResponseAPI;
import com.api.rest.assured.base.ApiRedaction;

/**
 * Reusable assertions for an API response, so a service class no longer rebuilds "status, content type,
 * schema" from {@code MatcherAssert} for every endpoint.
 *
 * <p>Every failure is an {@link AssertionError} that says what was expected and carries what is needed to
 * start debugging without re-running: the request line, the status and response time, the content type, the
 * server's {@code X-Request-Id} when it sends one, and a short body preview with credentials masked.
 *
 * <pre>
 * ApiAssertions.that(response).created().contentType("application/json")
 *         .respondedWithin(Duration.ofSeconds(2))
 *         .jsonPath("result.state", equalTo("1"))
 *         .matchesSchema("schemas/incident_create_response_schema.json");
 *
 * ApiAssertions.that(denied).unauthorized().jsonPath("error.message", not(emptyOrNullString()));
 * </pre>
 *
 * <p>These assert on the numeric status and the media type, not on the reason phrase ("Created"):
 * the phrase is optional in HTTP and gateways and proxies rewrite it.
 */
public final class ApiAssertions {

    private static final int BODY_PREVIEW_CHARS = 500;

    private final ResponseAPI response;

    private ApiAssertions(ResponseAPI response) {
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }
        this.response = response;
    }

    public static ApiAssertions that(ResponseAPI response) {
        return new ApiAssertions(response);
    }

    // ---------------------------------------------------------------------- status

    public ApiAssertions status(int expected) {
        if (response.getStatusCode() != expected) {
            throw failure("Expected HTTP status " + expected + " but got " + response.getStatusCode());
        }
        return this;
    }

    public ApiAssertions statusIn(int... allowed) {
        int actual = response.getStatusCode();
        if (Arrays.stream(allowed).noneMatch(a -> a == actual)) {
            throw failure("Expected HTTP status to be one of " + Arrays.toString(allowed) + " but got " + actual);
        }
        return this;
    }

    /** Any 2xx. */
    public ApiAssertions successful() {
        int actual = response.getStatusCode();
        if (actual < 200 || actual > 299) {
            throw failure("Expected a successful (2xx) status but got " + actual);
        }
        return this;
    }

    public ApiAssertions ok()            { return status(200); }
    public ApiAssertions created()       { return status(201); }
    public ApiAssertions noContent()     { return status(204); }
    public ApiAssertions badRequest()    { return status(400); }
    public ApiAssertions unauthorized()  { return status(401); }
    public ApiAssertions forbidden()     { return status(403); }
    public ApiAssertions notFound()      { return status(404); }
    public ApiAssertions conflict()      { return status(409); }
    public ApiAssertions unprocessable() { return status(422); }

    // --------------------------------------------------------------------- headers

    /** The media type without parameters ({@code application/json; charset=UTF-8} matches {@code application/json}). */
    public ApiAssertions contentType(String mediaType) {
        String actual = response.getContentType();
        if (actual == null || !actual.equalsIgnoreCase(mediaType)) {
            throw failure("Expected Content-Type '" + mediaType + "' but got '" + (actual == null ? "" : actual) + "'");
        }
        return this;
    }

    public ApiAssertions hasHeader(String name) {
        if (response.getHeader(name) == null) {
            throw failure("Expected the response to have a '" + name + "' header, but it has none");
        }
        return this;
    }

    public ApiAssertions header(String name, Matcher<? super String> matcher) {
        String actual = response.getHeader(name);
        if (actual == null || !matcher.matches(actual)) {
            throw failure("Expected header '" + name + "' " + describe(matcher) + " but "
                    + (actual == null ? "the header is absent" : "was '" + ApiRedaction.mask(name + ": " + actual) + "'"));
        }
        return this;
    }

    // ------------------------------------------------------------------ performance

    public ApiAssertions respondedWithin(Duration max) {
        long actual = response.getResponseTimeMs();
        if (actual > max.toMillis()) {
            throw failure("Expected a response within " + max.toMillis() + " ms but it took " + actual + " ms");
        }
        return this;
    }

    // ------------------------------------------------------------------------ body

    /** The value at a JSON path matches, with its natural type (a number is a Number, an array a List). */
    public ApiAssertions jsonPath(String path, Matcher<?> matcher) {
        Object actual = response.path(path, Object.class);
        if (!matcher.matches(actual)) {
            StringDescription mismatch = new StringDescription();
            matcher.describeMismatch(actual, mismatch);
            throw failure("Expected JSON path '" + path + "' " + describe(matcher) + " but "
                    + ApiRedaction.mask(mismatch.toString()));
        }
        return this;
    }

    public ApiAssertions bodyContains(String text) {
        String body = response.getBody();
        if (body == null || !body.contains(text)) {
            throw failure("Expected the response body to contain '" + ApiRedaction.mask(text) + "'");
        }
        return this;
    }

    /** The body matches the JSON schema at this classpath location. */
    public ApiAssertions matchesSchema(String classpathSchema) {
        try {
            response.verifySchema(classpathSchema);
        } catch (AssertionError e) {
            String detail = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
            throw failure("Response does not match the schema '" + classpathSchema + "': " + ApiRedaction.mask(detail));
        }
        return this;
    }

    // ------------------------------------------------------------------ diagnostics

    private AssertionError failure(String headline) {
        StringBuilder message = new StringBuilder(headline)
                .append("\n  Request     : ").append(response.describeRequest())
                .append("\n  Status      : ").append(response.getStatusCode()).append(' ')
                .append(response.getStatusMessage()).append(" (").append(response.getResponseTimeMs()).append(" ms)")
                .append("\n  Content-Type: ").append(blankAsNone(response.getContentType()));
        String requestId = response.getHeader("X-Request-Id");
        if (requestId == null) {
            requestId = response.getHeader("X-Correlation-Id");
        }
        if (requestId != null) {
            message.append("\n  Request id  : ").append(ApiRedaction.mask(requestId));
        }
        message.append("\n  Body        : ").append(bodyPreview());
        String repro = response.reproCommand();
        if (repro != null) {
            message.append("\n  Repro       : ").append(repro);
        }
        return new AssertionError(message.toString());
    }

    /** The failure an assertion on {@code response} would raise, with {@code headline} as its first line. */
    static AssertionError failureFor(ResponseAPI response, String headline) {
        return new ApiAssertions(response).failure(headline);
    }

    private String bodyPreview() {
        String contentType = response.getContentType();
        if (contentType != null && !contentType.isBlank() && !isTextual(contentType)) {
            return "(" + contentType + ", not shown)";
        }
        String body = response.getBody();
        return body == null || body.isEmpty() ? "(empty)" : ApiRedaction.preview(body, BODY_PREVIEW_CHARS);
    }

    private static boolean isTextual(String contentType) {
        String type = contentType.toLowerCase(Locale.ROOT);
        return type.startsWith("text/") || type.contains("json") || type.contains("xml")
                || type.contains("x-www-form-urlencoded") || type.contains("html");
    }

    private static String blankAsNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private static String describe(Matcher<?> matcher) {
        return new StringDescription().appendDescriptionOf(matcher).toString();
    }
}
