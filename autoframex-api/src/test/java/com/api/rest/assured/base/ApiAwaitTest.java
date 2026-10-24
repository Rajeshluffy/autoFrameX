package com.api.rest.assured.base;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.api.assertions.ApiAwait;
import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;

/** Waiting for an API to reach a state, instead of asserting on the first answer or sleeping a fixed time. */
public class ApiAwaitTest {

	private static final Duration FAST = Duration.ofMillis(20);

	private FakeHttpServer server;
	private RequestSpecBuilder base;
	private final AtomicInteger searchCalls = new AtomicInteger();

	@BeforeClass
	public void startServer() throws IOException {
		server = FakeHttpServer.start()
				.on("/api/search", r -> searchCalls.incrementAndGet() < 4
						? FakeHttpServer.Reply.json(200, "{\"result\":[]}")
						: FakeHttpServer.Reply.json(200, "{\"result\":[{\"number\":\"INC1\"}]}"))
				.on("/api/never", r -> FakeHttpServer.Reply.json(200, "{\"result\":[]}").header("X-Request-Id", "req-9"))
				.on("/api/created", r -> FakeHttpServer.Reply.json(201, "{}"));
		base = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api");
	}

	@AfterClass(alwaysRun = true)
	public void stopServer() {
		server.close();
	}

	private ResponseAPI get(String path) {
		return new RestAssuredBase().send(base, ApiRequest.get(path));
	}

	@Test
	public void returnsAsSoonAsTheConditionHolds() {
		searchCalls.set(0);
		ResponseAPI found = ApiAwait.until("incident is searchable", () -> get("search"),
				r -> !r.path("result", java.util.List.class).isEmpty(), Duration.ofSeconds(10), FAST);
		Assert.assertEquals(found.path("result[0].number", String.class), "INC1");
		Assert.assertEquals(searchCalls.get(), 4, "three empty answers and then the record");
	}

	@Test
	public void doesNotWaitWhenItIsAlreadyTrue() {
		long started = System.nanoTime();
		ApiAwait.untilStatus("created", () -> get("created"), 201, Duration.ofSeconds(10));
		Assert.assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 2000);
	}

	@Test
	public void aTimeoutCarriesTheLastResponse() {
		AssertionError error = null;
		try {
			ApiAwait.until("incident INC9 is searchable", () -> get("never"),
					r -> !r.path("result", java.util.List.class).isEmpty(), Duration.ofMillis(300), FAST);
		} catch (AssertionError e) {
			error = e;
		}
		Assert.assertNotNull(error, "the condition never held");
		String message = error.getMessage();
		Assert.assertTrue(message.contains("waiting for: incident INC9 is searchable"), message);
		Assert.assertTrue(message.contains("attempts"), message);
		Assert.assertTrue(message.contains("Request id  : req-9"), message);
		Assert.assertTrue(message.contains("Repro       : curl"), message);
	}

	@Test
	public void aServiceThatNeverAnswersIsReportedAsSuch() throws IOException {
		FakeHttpServer gone = FakeHttpServer.start();
		RequestSpecBuilder unreachable = new RequestSpecBuilder().setBaseUri(gone.baseUri()).setBasePath("/api");
		gone.close();
		AssertionError error = null;
		try {
			ApiAwait.until("service is back", () -> new RestAssuredBase().send(unreachable, ApiRequest.get("x")),
					r -> true, Duration.ofMillis(200), FAST);
		} catch (AssertionError e) {
			error = e;
		}
		Assert.assertNotNull(error);
		Assert.assertTrue(error.getMessage().contains("No response was ever received"), error.getMessage());
	}

	@Test
	public void aTransportFailureIsTreatedAsNotYet() throws IOException {
		AtomicInteger calls = new AtomicInteger();
		ResponseAPI response = ApiAwait.until("recovers", () -> {
			if (calls.incrementAndGet() < 3) {
				throw new ApiCallException("GET", "http://x", 1, new IOException("reset"));
			}
			return get("created");
		}, r -> r.getStatusCode() == 201, Duration.ofSeconds(10), FAST);
		Assert.assertEquals(response.getStatusCode(), 201);
		Assert.assertEquals(calls.get(), 3);
	}
}
