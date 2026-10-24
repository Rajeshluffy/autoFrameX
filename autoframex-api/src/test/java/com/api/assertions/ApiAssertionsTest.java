package com.api.assertions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.time.Duration;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;
import com.api.rest.assured.base.RestAssuredBase;
import com.api.rest.assured.base.RequestSpecBuilder;

import io.restassured.http.ContentType;

/**
 * Reusable response assertions, and the failure message that comes with them: what was expected, the request,
 * the status and time, the server's request id and a masked body preview, so a red test can be diagnosed from the
 * report without running it again.
 */
public class ApiAssertionsTest {

	private ServerHandle server;

	/** Test-local access to the fake server, which lives in another package. */
	private static final class ServerHandle {
		final com.sun.net.httpserver.HttpServer http;

		ServerHandle() throws IOException {
			http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		}

		String baseUri() {
			return "http://127.0.0.1:" + http.getAddress().getPort();
		}
	}

	private RequestSpecBuilder base;

	@BeforeClass
	public void startServer() throws IOException {
		server = new ServerHandle();
		respond("/api/ok", 200, "application/json", "{\"id\":1,\"name\":\"first\",\"n\":5,\"items\":[{\"v\":1},{\"v\":2}]}", 0);
		respond("/api/created", 201, "application/json", "{\"id\":2}", 0);
		respond("/api/denied", 401, "application/json", "{\"error\":\"denied\",\"access_token\":\"LEAKED-TOKEN-123\"}", 0);
		respond("/api/slow", 200, "application/json", "{\"id\":3}", 250);
		respond("/api/badschema", 200, "application/json", "{\"id\":\"not-a-number\"}", 0);
		respond("/api/binary", 200, "application/octet-stream", "BINARYBYTES", 0);
		server.http.start();
		base = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api").setAccept(ContentType.JSON);
	}

	private void respond(String path, int status, String contentType, String body, long delayMs) {
		server.http.createContext(path, exchange -> {
			exchange.getRequestBody().readAllBytes();
			if (delayMs > 0) {
				try {
					Thread.sleep(delayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", contentType);
			exchange.getResponseHeaders().add("X-Request-Id", "req-" + status);
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
	}

	@AfterClass(alwaysRun = true)
	public void stopServer() {
		server.http.stop(0);
	}

	private ResponseAPI call(String path) {
		return new RestAssuredBase().send(base, ApiRequest.get(path));
	}

	private static AssertionError failureOf(Runnable assertion) {
		try {
			assertion.run();
		} catch (AssertionError e) {
			return e;
		}
		throw new AssertionError("the assertion should have failed");
	}

	// ------------------------------------------------------------------- passing

	@Test
	public void aChainOfPassingAssertionsReturnsNormally() {
		ApiAssertions.that(call("ok")).ok().successful().statusIn(200, 201).contentType("application/json")
				.hasHeader("X-Request-Id").header("x-request-id", equalTo("req-200"))
				.jsonPath("name", equalTo("first")).jsonPath("n", equalTo(5))
				.jsonPath("items", hasSize(2)).jsonPath("items[1].v", greaterThan(1))
				.bodyContains("first").respondedWithin(Duration.ofSeconds(10));
		ApiAssertions.that(call("created")).created();
		ApiAssertions.that(call("denied")).unauthorized();
	}

	@Test
	public void schemaValidationPassesForAMatchingBody() {
		ApiAssertions.that(call("created")).matchesSchema("schemas/thing_schema.json");
	}

	// ------------------------------------------------------------------- failing

	@Test
	public void aWrongStatusFailsWithTheRequestTheStatusAndTheBody() {
		AssertionError e = failureOf(() -> ApiAssertions.that(call("denied")).status(200));

		String message = e.getMessage();
		Assert.assertTrue(message.contains("Expected HTTP status 200 but got 401"), message);
		Assert.assertTrue(message.contains("GET " + server.baseUri() + "/api/denied"), "the request line: " + message);
		Assert.assertTrue(message.contains("Request id  : req-401"), message);
		Assert.assertTrue(message.contains("Content-Type: application/json"), message);
		Assert.assertTrue(message.contains("denied"), "a body preview: " + message);
	}

	@Test
	public void theBodyPreviewNeverContainsACredential() {
		AssertionError e = failureOf(() -> ApiAssertions.that(call("denied")).status(200));

		Assert.assertFalse(e.getMessage().contains("LEAKED-TOKEN-123"),
				"the token in the response body must be masked in the failure message: " + e.getMessage());
	}

	@Test
	public void aBinaryBodyIsNotPrinted() {
		AssertionError e = failureOf(() -> ApiAssertions.that(call("binary")).status(201));

		Assert.assertFalse(e.getMessage().contains("BINARYBYTES"), e.getMessage());
		Assert.assertTrue(e.getMessage().contains("not shown"), e.getMessage());
	}

	@Test
	public void aSlowResponseFailsTheTimeAssertionWithTheMeasuredTime() {
		AssertionError e = failureOf(() -> ApiAssertions.that(call("slow")).respondedWithin(Duration.ofMillis(50)));

		Assert.assertTrue(e.getMessage().contains("within 50 ms"), e.getMessage());
		Assert.assertTrue(e.getMessage().contains(" ms)"), e.getMessage());
	}

	@Test
	public void aWrongJsonValueSaysWhatWasFound() {
		AssertionError e = failureOf(() -> ApiAssertions.that(call("ok")).jsonPath("name", equalTo("second")));

		Assert.assertTrue(e.getMessage().contains("JSON path 'name'"), e.getMessage());
		Assert.assertTrue(e.getMessage().contains("first"), "the actual value: " + e.getMessage());
	}

	@Test
	public void aMissingHeaderAndAWrongContentTypeFailClearly() {
		Assert.assertTrue(failureOf(() -> ApiAssertions.that(call("ok")).hasHeader("X-Nope")).getMessage().contains("X-Nope"));
		Assert.assertTrue(failureOf(() -> ApiAssertions.that(call("ok")).contentType("application/xml"))
				.getMessage().contains("application/xml"));
		Assert.assertTrue(failureOf(() -> ApiAssertions.that(call("ok")).header("X-Request-Id", containsString("zzz")))
				.getMessage().contains("X-Request-Id"));
	}

	@Test
	public void aBodyThatBreaksTheSchemaNamesTheSchema() {
		AssertionError e = failureOf(() -> ApiAssertions.that(call("badschema")).matchesSchema("schemas/thing_schema.json"));

		Assert.assertTrue(e.getMessage().contains("schemas/thing_schema.json"), e.getMessage());
		Assert.assertTrue(e.getMessage().contains("GET " + server.baseUri()), "the request line: " + e.getMessage());
	}

	@Test
	public void nullResponseIsRejectedEarly() {
		Assert.assertThrows(IllegalArgumentException.class, () -> ApiAssertions.that(null));
	}
}
