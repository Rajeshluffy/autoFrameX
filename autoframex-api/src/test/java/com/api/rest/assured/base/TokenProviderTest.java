package com.api.rest.assured.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;

/**
 * The OAuth token cache is shared by every {@code RequestSpecBuilder}. Before, each builder had its own, so a
 * service object created per scenario asked the token endpoint for a new token every time and kept it,
 * expired or not, for its whole life. Also: a request rejected with 401 gets one new token and one retry.
 */
public class TokenProviderTest {

	private FakeHttpServer server;
	private final AtomicInteger tokenNumber = new AtomicInteger();

	@BeforeMethod
	public void startServer() throws IOException {
		TokenProvider.clear();
		tokenNumber.set(0);
		server = FakeHttpServer.start();
		// Every token request gets a new token: tok-1, tok-2, ...
		server.on("/oauth/token", request -> FakeHttpServer.Reply.json(200,
				"{\"access_token\":\"tok-" + tokenNumber.incrementAndGet() + "\",\"expires_in\":3600}"));
	}

	@AfterMethod(alwaysRun = true)
	public void stopServer() {
		server.close();
		TokenProvider.clear();
	}

	private RequestSpecBuilder oauthBuilder(String user) {
		return new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api")
				.setOAuth(server.baseUri(), "/oauth/token", "password", "client-1", "secret-1", user, "pw");
	}

	// ------------------------------------------------------------------- sharing

	@Test
	public void buildersForTheSameClientAndUserShareOneToken() {
		oauthBuilder("alice").build();
		oauthBuilder("alice").build();
		oauthBuilder("alice").build();

		Assert.assertEquals(server.hits("/oauth/token"), 1, "one token request for three builders");
	}

	@Test
	public void aDifferentUserGetsItsOwnToken() {
		oauthBuilder("alice").build();
		oauthBuilder("bob").build();

		Assert.assertEquals(server.hits("/oauth/token"), 2);
	}

	@Test
	public void aBuilderBuiltManyTimesAsksTheEndpointOnce() {
		RequestSpecBuilder builder = oauthBuilder("alice");
		for (int i = 0; i < 5; i++) {
			builder.build();
		}
		Assert.assertEquals(server.hits("/oauth/token"), 1);
	}

	@Test(timeOut = 30_000)
	public void threadsAskingAtTheSameMomentMakeOneRequest() throws Exception {
		server.on("/oauth/slow", request -> FakeHttpServer.Reply.json(200,
				"{\"access_token\":\"slow-token\",\"expires_in\":3600}").delay(300));
		OAuthSettings settings = new OAuthSettings(server.baseUri() + "/oauth/slow", "password", "c", "s", "u", "p", null);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<String>> tokens = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			tokens.add(pool.submit(() -> {
				go.await();
				return TokenProvider.token(settings);
			}));
		}
		go.countDown();
		for (Future<String> token : tokens) {
			Assert.assertEquals(token.get(10, TimeUnit.SECONDS), "slow-token");
		}
		pool.shutdownNow();

		Assert.assertEquals(server.hits("/oauth/slow"), 1, "the other seven threads waited for the first request");
	}

	@Test
	public void anExpiringTokenIsReplacedOnTheNextBuild() throws Exception {
		server.on("/oauth/short", request -> FakeHttpServer.Reply.json(200,
				"{\"access_token\":\"short-" + tokenNumber.incrementAndGet() + "\",\"expires_in\":1}"));
		RequestSpecBuilder builder = new RequestSpecBuilder().setBaseUri(server.baseUri())
				.setOAuth(server.baseUri(), "/oauth/short", "password", "c", "s", "u", "p");
		builder.build();
		Assert.assertEquals(server.hits("/oauth/short"), 1);

		Thread.sleep(1300);   // a token with 1 s to live is treated as valid for 1 s
		builder.build();

		Assert.assertEquals(server.hits("/oauth/short"), 2,
				"the builder must not keep sending the token it fetched at set-up after it has expired");
	}

	// ------------------------------------------------------------ what is sent

	@Test
	public void aClientCredentialsGrantSendsNoEmptyUserFields() {
		OAuthSettings settings = new OAuthSettings(server.baseUri() + "/oauth/token", "client_credentials",
				"my-id", "my-secret", null, null, "read write");

		TokenProvider.token(settings);

		String form = server.last().body;
		Assert.assertTrue(form.contains("grant_type=client_credentials"), form);
		Assert.assertTrue(form.contains("client_id=my-id"), form);
		Assert.assertTrue(form.contains("scope=read+write") || form.contains("scope=read%20write"), form);
		Assert.assertFalse(form.contains("username"), "no bare username parameter: " + form);
		Assert.assertFalse(form.contains("password"), "no bare password parameter: " + form);
	}

	@Test
	public void theCacheKeyAndToStringNeverContainTheSecrets() {
		OAuthSettings settings = new OAuthSettings("http://t/token", "password", "client-1", "TOP-SECRET", "alice",
				"hunter2", null);

		Assert.assertFalse(settings.cacheKey().contains("TOP-SECRET") || settings.cacheKey().contains("hunter2"));
		Assert.assertFalse(settings.toString().contains("TOP-SECRET") || settings.toString().contains("hunter2"));
	}

	// ----------------------------------------------------- 401: new token, one retry

	private void apiAcceptsOnly(String bearer) {
		server.on("/api/thing", request -> bearer.equals(request.headers.get("authorization"))
				? FakeHttpServer.Reply.json(200, "{\"ok\":true}")
				: FakeHttpServer.Reply.json(401, "{\"error\":\"expired\"}"));
	}

	@Test
	public void aRejectedTokenIsReplacedAndTheCallRepeatedOnce() {
		apiAcceptsOnly("Bearer tok-2");   // the first token (tok-1) is "expired"
		RequestSpecBuilder builder = oauthBuilder("alice");

		ResponseAPI response = new RestAssuredBase().send(builder, ApiRequest.get("thing"));

		Assert.assertEquals(response.getStatusCode(), 200);
		Assert.assertEquals(server.hits("/api/thing"), 2, "rejected once, then accepted");
		Assert.assertEquals(server.hits("/oauth/token"), 2, "one token at set-up, one after the 401");
	}

	@Test
	public void aSecond401IsReturnedNotRetriedAgain() {
		apiAcceptsOnly("Bearer never");
		RequestSpecBuilder builder = oauthBuilder("alice");

		ResponseAPI response = new RestAssuredBase().send(builder, ApiRequest.get("thing"));

		Assert.assertEquals(response.getStatusCode(), 401);
		Assert.assertEquals(server.hits("/api/thing"), 2, "exactly one retry, not a loop");
	}

	@Test
	public void aStaticBearerTokenIsNotRetriedOn401() {
		apiAcceptsOnly("Bearer never");
		RequestSpecBuilder builder = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api")
				.setBearerToken("fixed-token");

		ResponseAPI response = new RestAssuredBase().send(builder, ApiRequest.get("thing"));

		Assert.assertEquals(response.getStatusCode(), 401);
		Assert.assertEquals(server.hits("/api/thing"), 1, "there is no way to get a different token");
		Assert.assertEquals(server.hits("/oauth/token"), 0);
	}

	@Test
	public void aPostIsRepeatedWithItsBodyAfterA401() {
		server.on("/api/thing", request -> "Bearer tok-2".equals(request.headers.get("authorization"))
				? FakeHttpServer.Reply.json(201, "{\"id\":7}")
				: FakeHttpServer.Reply.json(401, "{}"));
		RequestSpecBuilder builder = oauthBuilder("alice");

		ResponseAPI response = new RestAssuredBase().send(builder,
				ApiRequest.post("thing").contentType("application/json").body("{\"name\":\"x\"}"));

		Assert.assertEquals(response.getStatusCode(), 201);
		Assert.assertEquals(server.last().body, "{\"name\":\"x\"}", "the retry carries the same body");
	}
}
