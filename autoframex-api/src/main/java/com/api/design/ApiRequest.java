package com.api.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One API call, described as an immutable value: the verb, the path (with {@code {name}} placeholders), and
 * the path parameters, query parameters, headers, cookies, content type and body that belong to <em>this
 * call only</em>.
 *
 * <p>Every method returns a new instance, so a request can be shared and varied without one call's
 * parameters leaking into the next. That is what the mutable {@code RequestSpecBuilder} could not guarantee:
 * its single-slot setters stayed set after {@code build()}. The service-wide settings (base URI, auth,
 * default headers, timeouts) stay on the builder; the per-call part lives here.
 *
 * <pre>
 * ApiRequest.get("incident/{sysId}").pathParam("sysId", id)
 * ApiRequest.get("incident").queryParam("sysparm_query", "number=" + n).queryParam("sysparm_limit", 10)
 * ApiRequest.patch("incident/{sysId}").pathParam("sysId", id).body(payload)
 * </pre>
 */
public final class ApiRequest {

    private final ApiMethod method;
    private final String path;
    private final Map<String, Object> pathParams;
    private final Map<String, List<Object>> queryParams;
    private final Map<String, String> headers;
    private final Map<String, String> cookies;
    private final String contentType;
    private final Object body;

    private ApiRequest(ApiMethod method, String path, Map<String, Object> pathParams,
            Map<String, List<Object>> queryParams, Map<String, String> headers, Map<String, String> cookies,
            String contentType, Object body) {
        this.method = method;
        this.path = path;
        this.pathParams = pathParams;
        this.queryParams = queryParams;
        this.headers = headers;
        this.cookies = cookies;
        this.contentType = contentType;
        this.body = body;
    }

    // ------------------------------------------------------------------ factories

    public static ApiRequest of(ApiMethod method, String path) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null (use \"\" for the base path itself)");
        }
        return new ApiRequest(method, path, new LinkedHashMap<>(), new LinkedHashMap<>(),
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER), new LinkedHashMap<>(), null, null);
    }

    public static ApiRequest get(String path)     { return of(ApiMethod.GET, path); }
    public static ApiRequest post(String path)    { return of(ApiMethod.POST, path); }
    public static ApiRequest put(String path)     { return of(ApiMethod.PUT, path); }
    public static ApiRequest patch(String path)   { return of(ApiMethod.PATCH, path); }
    public static ApiRequest delete(String path)  { return of(ApiMethod.DELETE, path); }
    public static ApiRequest head(String path)    { return of(ApiMethod.HEAD, path); }
    public static ApiRequest options(String path) { return of(ApiMethod.OPTIONS, path); }

    // ------------------------------------------------------------- "with" methods

    /** Fills the {@code {name}} placeholder in the path. */
    public ApiRequest pathParam(String name, Object value) {
        requireName(name);
        Map<String, Object> copy = new LinkedHashMap<>(pathParams);
        copy.put(name, value);
        return new ApiRequest(method, path, copy, queryParams, headers, cookies, contentType, body);
    }

    /** Adds a query parameter; call again with the same name, or pass several values, to repeat it. */
    public ApiRequest queryParam(String name, Object... values) {
        requireName(name);
        Map<String, List<Object>> copy = new LinkedHashMap<>();
        queryParams.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        List<Object> list = copy.computeIfAbsent(name, k -> new ArrayList<>());
        if (values == null || values.length == 0) {
            list.add("");
        } else {
            Collections.addAll(list, values);
        }
        return new ApiRequest(method, path, pathParams, copy, headers, cookies, contentType, body);
    }

    public ApiRequest header(String name, String value) {
        requireName(name);
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(headers);
        copy.put(name, value);
        return new ApiRequest(method, path, pathParams, queryParams, copy, cookies, contentType, body);
    }

    public ApiRequest cookie(String name, String value) {
        requireName(name);
        Map<String, String> copy = new LinkedHashMap<>(cookies);
        copy.put(name, value);
        return new ApiRequest(method, path, pathParams, queryParams, headers, copy, contentType, body);
    }

    public ApiRequest contentType(String contentType) {
        return new ApiRequest(method, path, pathParams, queryParams, headers, cookies, contentType, body);
    }

    /** The request body: a POJO, a Map, a JSON string, or a byte array. */
    public ApiRequest body(Object body) {
        return new ApiRequest(method, path, pathParams, queryParams, headers, cookies, contentType, body);
    }

    // ----------------------------------------------------------------- accessors

    public ApiMethod method()                       { return method; }
    public String path()                            { return path; }
    public Map<String, Object> pathParams()         { return Collections.unmodifiableMap(pathParams); }
    public Map<String, List<Object>> queryParams()  { return Collections.unmodifiableMap(queryParams); }
    public Map<String, String> headers()            { return Collections.unmodifiableMap(headers); }
    public Map<String, String> cookies()           { return Collections.unmodifiableMap(cookies); }
    public String contentType()                     { return contentType; }
    public Object body()                            { return body; }

    /** Verb, path and the <em>names</em> of the query parameters. Values are left out: they may be credentials. */
    @Override
    public String toString() {
        return method + " " + path
                + (queryParams.isEmpty() ? "" : " ?" + String.join("&", queryParams.keySet()))
                + (body == null ? "" : " [body]");
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
