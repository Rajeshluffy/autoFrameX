package com.api.rest.assured.base;

import com.framework.exception.Categorized;
import com.framework.exception.FrameworkException;

/**
 * An API call that produced no response at all: connection refused, DNS failure, a connect or read timeout, a
 * reset. A response with an error <em>status</em> is not this; it is returned like any other response.
 *
 * <p>Carries the method, the URI (credentials in the query string masked) and how long the call ran, which the
 * raw {@code SocketTimeoutException} or {@code ConnectException} did not say.
 */
public class ApiCallException extends FrameworkException implements Categorized {

    private static final long serialVersionUID = 1L;

    private final String method;
    private final String uri;
    private final long elapsedMs;

    public ApiCallException(String method, String uri, long elapsedMs, Throwable cause) {
        super("API call failed: " + method + " " + uri + " after " + elapsedMs + " ms - "
                + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        this.method = method;
        this.uri = uri;
        this.elapsedMs = elapsedMs;
    }

    public String method()   { return method; }
    public String uri()      { return uri; }
    public long elapsedMs()  { return elapsedMs; }

    @Override
    public String category() {
        return "API_TRANSPORT";
    }
}
