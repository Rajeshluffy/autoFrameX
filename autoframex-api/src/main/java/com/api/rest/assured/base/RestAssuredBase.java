package com.api.rest.assured.base;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.api.design.ApiClient;
import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;

/**
 * Stateless REST Assured client — every call builds and returns its own
 * {@link Response} with no instance fields, so one instance is safe to share
 * across threads/scenarios without any synchronization contract to document
 * or enforce.
 *
 * <p>Two ways to make a call. The spec-based methods ({@code get(spec, endpoint)} and friends) take a
 * ready-built {@link RequestSpecification}. {@link #send(RequestSpecBuilder, ApiRequest)} takes the service's
 * base settings and an immutable {@link ApiRequest}, and is the one that survives an expired token: a 401 on
 * an OAuth-authenticated call gets a new token and one retry.
 */
public class RestAssuredBase implements ApiClient {

	private static final Logger logger = LoggerFactory.getLogger(RestAssuredBase.class);

	private RequestSpecification given(RequestSpecification requestSpecification) {
		// A spec that came from RequestSpecBuilder already carries timeouts; one built another way
		// (plain RestAssured.given()) gets the framework defaults here, so no call here can wait forever.
		RestAssuredConfig current = requestSpecification instanceof FilterableRequestSpecification
				? ((FilterableRequestSpecification) requestSpecification).getConfig() : null;
		return RestAssured.given()
				          .spec(requestSpecification)
				          .config(ApiTimeouts.applyDefaults(current))
				          .filter(new RestAssuredListener());
	}

	/** Wraps the response together with a description of the call that produced it, for failure diagnostics. */
	private ResponseAPI wrap(Response response) {
		return new RestAssuredResponseBase(response, RestAssuredListener.consumeLastRequestLine());
	}

	@Override
	public ResponseAPI get(RequestSpecification request, String endPoint) {
		return wrap(given(request).get(endPoint));
	}

	@Override
	public ResponseAPI post(RequestSpecification request, String endPoint) {
		return wrap(given(request).post(endPoint));
	}

	@Override
	public ResponseAPI post(RequestSpecification request, String endPoint, Object body) {
		return wrap(given(request).body(body).post(endPoint));
	}

	@Override
	public ResponseAPI put(RequestSpecification request, String endPoint, Object body) {
		// Use RestAssured's Jackson auto-serialization (same as post) — not Gson.
		// RestAssured picks up jackson-databind from the classpath and serializes
		// the POJO when Content-Type: application/json is set on the request spec.
		return wrap(given(request).body(body).put(endPoint));
	}

	@Override
	public ResponseAPI patch(RequestSpecification request, String endPoint, Object body) {
		return wrap(given(request).body(body).patch(endPoint));
	}

	@Override
	public ResponseAPI delete(RequestSpecification request, String endPoint) {
		return wrap(given(request).delete(endPoint));
	}

	// ---------------------------------------------------------------------------------------------
	// ApiRequest based calls
	// ---------------------------------------------------------------------------------------------

	/**
	 * Sends {@code request} with the service-wide settings of {@code base}.
	 *
	 * <p>If the server answers 401 and the builder authenticates with an OAuth token, the rejected token is
	 * dropped, a new one is fetched and the call is made once more. A 401 means the server did not act on the
	 * request, so repeating it is safe even for a POST. A second 401 is returned as it is.
	 *
	 * @throws ApiCallException when there is no response at all (refused, reset, timed out)
	 */
	public ResponseAPI send(RequestSpecBuilder base, ApiRequest request) {
		ResponseAPI first = execute(base.build(), request);
		if (first.getStatusCode() == 401 && base.hasOAuth() && base.refreshOAuthToken()) {
			logger.info("{} was rejected with 401; retrying once with a new OAuth token", request);
			return execute(base.build(), request);
		}
		return first;
	}

	private ResponseAPI execute(RequestSpecification spec, ApiRequest request) {
		RequestSpecification call = given(spec);
		if (!request.pathParams().isEmpty()) {
			call.pathParams(request.pathParams());
		}
		request.queryParams().forEach((name, values) -> call.queryParam(name, values.toArray()));
		if (!request.headers().isEmpty()) {
			call.headers(new HashMap<String, Object>(request.headers()));
		}
		for (Map.Entry<String, String> cookie : request.cookies().entrySet()) {
			call.cookie(cookie.getKey(), cookie.getValue());
		}
		if (request.contentType() != null) {
			call.contentType(request.contentType());
		}
		if (request.body() != null) {
			call.body(request.body());
		}
		return wrap(call.request(Method.valueOf(request.method().name()), request.path()));
	}
}
