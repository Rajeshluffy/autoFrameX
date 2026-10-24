package com.api.rest.assured.base;

import java.util.Locale;
import java.util.Set;

import io.restassured.http.Header;
import io.restassured.specification.FilterableRequestSpecification;

/**
 * A {@code curl} command that repeats a call, for pasting from a failed test into a terminal.
 *
 * <p>Credentials never appear in it: the value of {@code Authorization}, cookie and key headers is replaced
 * with {@code <redacted>}, and everything else goes through {@link ApiRedaction#mask(String)}. A person who
 * reproduces the call fills the credential in themselves.
 */
final class CurlCommand {

    private static final Set<String> SECRET_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "x-api-key", "api-key", "x-auth-token");

    private static final int MAX_BODY_CHARS = 2000;

    private CurlCommand() {}

    static String from(FilterableRequestSpecification spec) {
        StringBuilder curl = new StringBuilder("curl -X ").append(spec.getMethod());
        curl.append(' ').append(quote(ApiRedaction.mask(spec.getURI())));
        for (Header header : spec.getHeaders()) {
            String value = SECRET_HEADERS.contains(header.getName().toLowerCase(Locale.ROOT))
                    ? "<redacted>" : ApiRedaction.mask(header.getValue());
            curl.append(" -H ").append(quote(header.getName() + ": " + value));
        }
        Object body = spec.getBody();
        if (body != null) {
            String text = ApiRedaction.mask(body.toString());
            if (text.length() > MAX_BODY_CHARS) {
                text = text.substring(0, MAX_BODY_CHARS) + "...";
            }
            curl.append(" --data-raw ").append(quote(text));
        }
        return curl.toString();
    }

    /** Single quotes, which a POSIX shell does not interpret; an embedded quote is closed, escaped, reopened. */
    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
