package com.api.rest.assured.base;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.api.design.ApiMethod;
import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;

/** Repeating a call that hit a transient problem: only safe calls, only transient failures, bounded waits. */
public class RetryPolicyTest {

	private FakeHttpServer server;
	private RequestSpecBuilder base;
	private final AtomicInteger flakyCalls = new AtomicInteger();
	private final AtomicInteger postCalls = new AtomicInteger();

	@BeforeClass
	public void startServer() throws IOException {
		server = FakeHttpServer.start()
				.on("/api/flaky", r -> flakyCalls.incrementAndGet() <= 2
						? FakeHttpServer.Reply.json(503, "{\"error\":\"busy\"}")
						: FakeHttpServer.Reply.json(200, "{\"ok\":true}"))
				.on("/api/down", r -> FakeHttpServer.Reply.json(503, "{\"error\":\"down\"}"))
				.on("/api/missing", r -> FakeHttpServer.Reply.json(404, "{\"error\":\"nope\"}"))
				.on("/api/limited", r -> FakeHttpServer.Reply.json(429, "{}").header("Retry-After", "2"))
				.on("/api/create", r -> {
					postCalls.incrementAndGet();
					return FakeHttpServer.Reply.json(503, "{\"error\":\"busy\"}");
				});
		base = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api");
	}

	@AfterClass(alwaysRun = true)
	public void stopServer() {
		server.close();
	}

	private ResponseAPI get(String path, RetryPolicy policy) {
		return new RestAssuredBase().send(base, ApiRequest.get(path), policy);
	}

	// ------------------------------------------------------------- decisions

	@Test
	public void onlySafeMethodsAreEverRepeated() {
		RetryPolicy policy = RetryPolicy.forSafeCalls(3, 1);
		ResponseAPI unavailable = get("down", RetryPolicy.none());
		Assert.assertTrue(policy.shouldRetry(ApiMethod.GET, 0, unavailable, null));
		Assert.assertTrue(policy.shouldRetry(ApiMethod.HEAD, 0, unavailable, null));
		Assert.assertTrue(policy.shouldRetry(ApiMethod.OPTIONS, 0, unavailable, null));
		Assert.assertFalse(policy.shouldRetry(ApiMethod.POST, 0, unavailable, null), "a POST may have been acted on");
		Assert.assertFalse(policy.shouldRetry(ApiMethod.PUT, 0, unavailable, null));
		Assert.assertFalse(policy.shouldRetry(ApiMethod.PATCH, 0, unavailable, null));
		Assert.assertFalse(policy.shouldRetry(ApiMethod.DELETE, 0, unavailable, null));
	}

	@Test
	public void aClientErrorIsNotTransientButANoResponseFailureIs() {
		RetryPolicy policy = RetryPolicy.forSafeCalls(2, 1);
		Assert.assertFalse(policy.shouldRetry(ApiMethod.GET, 0, get("missing", RetryPolicy.none()), null));
		Assert.assertTrue(policy.shouldRetry(ApiMethod.GET, 0, null,
				new ApiCallException("GET", "http://x", 5, new IOException("reset"))));
	}

	@Test
	public void stopsWhenTheAttemptsAreUsedUp() {
		RetryPolicy policy = RetryPolicy.forSafeCalls(2, 1);
		ResponseAPI unavailable = get("down", RetryPolicy.none());
		Assert.assertTrue(policy.shouldRetry(ApiMethod.GET, 1, unavailable, null));
		Assert.assertFalse(policy.shouldRetry(ApiMethod.GET, 2, unavailable, null));
		Assert.assertFalse(RetryPolicy.none().shouldRetry(ApiMethod.GET, 0, unavailable, null));
	}

	@Test
	public void waitsDoubleAndAreCapped() {
		RetryPolicy policy = RetryPolicy.forSafeCalls(10, 500);
		Assert.assertEquals(policy.delayMs(0, null), 500);
		Assert.assertEquals(policy.delayMs(1, null), 1000);
		Assert.assertEquals(policy.delayMs(2, null), 2000);
		Assert.assertEquals(policy.delayMs(9, null), RetryPolicy.MAX_DELAY_MS);
	}

	@Test
	public void theServerSaysHowLongToWait() {
		RetryPolicy policy = RetryPolicy.forSafeCalls(3, 1);
		Assert.assertEquals(policy.delayMs(0, get("limited", RetryPolicy.none())), 2000, "Retry-After: 2");
		Assert.assertEquals(RetryPolicy.retryAfterMs("120"), 120_000);
		Assert.assertEquals(RetryPolicy.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"), -1, "a date is ignored");
		Assert.assertEquals(RetryPolicy.retryAfterMs("-5"), -1);
		Assert.assertEquals(RetryPolicy.retryAfterMs(null), -1);
		Assert.assertEquals(RetryPolicy.retryAfterMs(" "), -1);
	}

	// ------------------------------------------------------- through the client

	@Test
	public void aFlakyGetIsRepeatedUntilItAnswers() {
		flakyCalls.set(0);
		ResponseAPI response = get("flaky", RetryPolicy.forSafeCalls(3, 1));
		Assert.assertEquals(response.getStatusCode(), 200);
		Assert.assertEquals(server.hits("/api/flaky"), 3, "two 503s and then the 200");
	}

	@Test
	public void whenTheRetriesRunOutTheLastResponseIsReturned() {
		int before = server.hits("/api/down");
		ResponseAPI response = get("down", RetryPolicy.forSafeCalls(2, 1));
		Assert.assertEquals(response.getStatusCode(), 503);
		Assert.assertEquals(server.hits("/api/down") - before, 3, "the first call and two repeats");
	}

	@Test
	public void aPostIsNeverRepeatedByThePolicy() {
		postCalls.set(0);
		ResponseAPI response = new RestAssuredBase().send(base, ApiRequest.post("create").body("{}"),
				RetryPolicy.forSafeCalls(5, 1));
		Assert.assertEquals(response.getStatusCode(), 503);
		Assert.assertEquals(postCalls.get(), 1);
	}

	@Test
	public void aClientErrorIsReturnedAtOnce() {
		int before = server.hits("/api/missing");
		Assert.assertEquals(get("missing", RetryPolicy.forSafeCalls(5, 1)).getStatusCode(), 404);
		Assert.assertEquals(server.hits("/api/missing") - before, 1);
	}

	@Test
	public void byDefaultNothingIsRepeated() {
		int before = server.hits("/api/down");
		Assert.assertEquals(new RestAssuredBase().send(base, ApiRequest.get("down")).getStatusCode(), 503);
		Assert.assertEquals(server.hits("/api/down") - before, 1, "-DapiRetryMax is not set");
	}

	@Test
	public void aCallWithNoResponseIsRepeatedThenThrown() throws IOException {
		FakeHttpServer gone = FakeHttpServer.start();
		RequestSpecBuilder unreachable = new RequestSpecBuilder().setBaseUri(gone.baseUri()).setBasePath("/api");
		gone.close();
		try {
			new RestAssuredBase().send(unreachable, ApiRequest.get("anything"), RetryPolicy.forSafeCalls(1, 1));
			Assert.fail("a refused connection must surface as ApiCallException");
		} catch (ApiCallException expected) {
			Assert.assertEquals(expected.method(), "GET");
		}
	}
}
