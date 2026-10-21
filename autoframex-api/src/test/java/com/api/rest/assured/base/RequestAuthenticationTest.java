package com.api.rest.assured.base;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;

import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;

/**
 * Authentication used to fail quietly: a rejected token request produced {@code null}, so every
 * later call went out without credentials, and a spec could carry Basic auth <em>and</em> an
 * OAuth token. These tests run against a throw-away HTTP server standing in for the token
 * endpoint, so they need no network.
 */
public class RequestAuthenticationTest {

	private HttpServer server;
	private String baseUri;
	private final Deque<String[]> replies = new ArrayDeque<>(); // {status, body}
	private final AtomicInteger tokenRequests = new AtomicInteger();
	private volatile String lastRequestBody = "";

	@BeforeMethod
	public void startFakeTokenEndpoint() throws IOException {
		replies.clear();
		tokenRequests.set(0);
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/oauth/token", exchange -> {
			tokenRequests.incrementAndGet();
			lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String[] reply;
			synchronized (replies) {
				reply = replies.size() > 1 ? replies.poll() : replies.peek();
			}
			byte[] bytes = reply[1].getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(Integer.parseInt(reply[0]), bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		server.start();
		baseUri = "http://localhost:" + server.getAddress().getPort();
	}

	@AfterMethod(alwaysRun = true)
	public void stopFakeTokenEndpoint() {
		if (server != null) {
			server.stop(0);
		}
	}

	private void reply(int status, String body) {
		replies.add(new String[] {String.valueOf(status), body});
	}

	private RequestAuthentication auth() {
		return new RequestAuthentication().setOAuthBaseUrl(baseUri + "/oauth/token");
	}

	// -------------------------------------------------------------------------
	// the token request
	// -------------------------------------------------------------------------

	@Test
	public void aSuccessfulRequestReturnsTheTokenAndSendsTheFormFields() {
		reply(200, "{\"access_token\":\"tok-1\",\"expires_in\":7200}");

		String token = auth().getOAuthToken("password", "client-1", "secret-1", "alice", "pw-1");

		Assert.assertEquals(token, "tok-1");
		Assert.assertTrue(lastRequestBody.contains("grant_type=password"), lastRequestBody);
		Assert.assertTrue(lastRequestBody.contains("username=alice"), lastRequestBody);
	}

	@Test
	public void theTokenIsCachedForTheServersExpiresInNotAFixedHour() {
		reply(200, "{\"access_token\":\"tok-1\",\"expires_in\":7200}");
		RequestAuthentication auth = auth();

		auth.getOAuthToken("password", "c", "s", "u", "p");
		auth.getOAuthToken("password", "c", "s", "u", "p");

		Assert.assertEquals(tokenRequests.get(), 1, "second call reuses the cached token");
		Assert.assertTrue(auth.secondsUntilExpiry() > 3600,
				"7200 s from the server minus the 30 s margin, not the old fixed 3600: " + auth.secondsUntilExpiry());
	}

	@Test
	public void withoutExpiresInTheTokenIsCachedForTheDefaultHour() {
		reply(200, "{\"access_token\":\"tok-1\"}");
		RequestAuthentication auth = auth();
		auth.getOAuthToken("password", "c", "s", "u", "p");
		Assert.assertTrue(auth.secondsUntilExpiry() <= RequestAuthentication.DEFAULT_TOKEN_VALIDITY_SECONDS);
		Assert.assertTrue(auth.secondsUntilExpiry() > RequestAuthentication.DEFAULT_TOKEN_VALIDITY_SECONDS - 60);
	}

	@Test
	public void shortLivedTokensAreCachedForHalfTheirLifeNotZero() {
		Assert.assertEquals(RequestAuthentication.validitySeconds(7200), 7170);
		Assert.assertEquals(RequestAuthentication.validitySeconds(31), 1);
		Assert.assertEquals(RequestAuthentication.validitySeconds(20), 10, "under the margin: half its life");
		Assert.assertEquals(RequestAuthentication.validitySeconds(1), 1);
		Assert.assertEquals(RequestAuthentication.validitySeconds(0), RequestAuthentication.DEFAULT_TOKEN_VALIDITY_SECONDS);
		Assert.assertEquals(RequestAuthentication.validitySeconds(-5), RequestAuthentication.DEFAULT_TOKEN_VALIDITY_SECONDS);
	}

	// -------------------------------------------------------------------------
	// failures are loud and never cached
	// -------------------------------------------------------------------------

	@Test
	public void aRejectedRequestThrowsWithTheStatusAndTheServersReasonButNeverTheSecrets() {
		reply(401, "{\"error\":\"invalid_client\",\"error_description\":\"Client authentication failed\"}");

		IllegalStateException failure = Assert.expectThrows(IllegalStateException.class,
				() -> auth().getOAuthToken("password", "client-1", "top-secret-value", "alice", "hunter2"));

		Assert.assertTrue(failure.getMessage().contains("HTTP 401"), failure.getMessage());
		Assert.assertTrue(failure.getMessage().contains("invalid_client"), failure.getMessage());
		Assert.assertTrue(failure.getMessage().contains("Client authentication failed"), failure.getMessage());
		Assert.assertFalse(failure.getMessage().contains("top-secret-value"), "client secret must not leak into the message");
		Assert.assertFalse(failure.getMessage().contains("hunter2"), "password must not leak into the message");
	}

	@Test
	public void aSuccessStatusWithoutAnAccessTokenIsAFailureToo() {
		reply(200, "{\"token_type\":\"bearer\"}");
		IllegalStateException failure = Assert.expectThrows(IllegalStateException.class,
				() -> auth().getOAuthToken("password", "c", "s", "u", "p"));
		Assert.assertTrue(failure.getMessage().contains("no access_token"), failure.getMessage());
	}

	@Test
	public void aNonJsonErrorPageStillGivesAReadableFailure() {
		reply(502, "<html>Bad Gateway</html>");
		IllegalStateException failure = Assert.expectThrows(IllegalStateException.class,
				() -> auth().getOAuthToken("password", "c", "s", "u", "p"));
		Assert.assertTrue(failure.getMessage().contains("HTTP 502"), failure.getMessage());
		Assert.assertTrue(failure.getMessage().contains("Bad Gateway"), failure.getMessage());
	}

	@Test
	public void aFailedRequestIsNotCachedSoTheNextCallTriesAgain() {
		reply(500, "{\"error\":\"server_error\"}");
		reply(200, "{\"access_token\":\"tok-2\",\"expires_in\":3600}");
		RequestAuthentication auth = auth();

		Assert.expectThrows(IllegalStateException.class, () -> auth.getOAuthToken("password", "c", "s", "u", "p"));
		Assert.assertFalse(auth.isTokenValid(), "nothing cached after a failure");

		Assert.assertEquals(auth.getOAuthToken("password", "c", "s", "u", "p"), "tok-2");
		Assert.assertEquals(tokenRequests.get(), 2);
	}

	@Test
	public void aFailedRefreshDropsTheOldTokenInsteadOfKeepingAStaleOne() {
		reply(200, "{\"access_token\":\"tok-1\",\"expires_in\":3600}");
		reply(400, "{\"error\":\"invalid_grant\",\"error_description\":\"refresh token expired\"}");
		RequestAuthentication auth = auth();
		auth.getOAuthToken("password", "c", "s", "u", "p");
		Assert.assertTrue(auth.isTokenValid());

		IllegalStateException failure = Assert.expectThrows(IllegalStateException.class,
				() -> auth.refreshOAuthToken("refresh_token", "c", "s", "r"));

		Assert.assertTrue(failure.getMessage().contains("invalid_grant"), failure.getMessage());
		Assert.assertFalse(auth.isTokenValid());
	}

	@Test
	public void aMissingTokenUrlIsReportedAsSuch() {
		IllegalStateException failure = Assert.expectThrows(IllegalStateException.class,
				() -> new RequestAuthentication().getOAuthToken("password", "c", "s", "u", "p"));
		Assert.assertTrue(failure.getMessage().contains("setOAuthBaseUrl"), failure.getMessage());
	}

	// -------------------------------------------------------------------------
	// RequestSpecBuilder: one scheme per request
	// -------------------------------------------------------------------------

	@Test
	public void oauthDoesNotAlsoConfigureBasicAuthWithTheResourceOwnersCredentials() {
		reply(200, "{\"access_token\":\"tok-1\",\"expires_in\":3600}");

		RequestSpecification spec = new RequestSpecBuilder()
				.setOAuth(baseUri, "/oauth/token", "password", "client-1", "secret-1", "alice", "hunter2")
				.build();

		FilterableRequestSpecification filterable = (FilterableRequestSpecification) spec;
		String scheme = filterable.getAuthenticationScheme().getClass().getSimpleName().toLowerCase();
		Assert.assertTrue(scheme.contains("oauth2"), "the OAuth token is the scheme in use, was: " + scheme);
		Assert.assertFalse(scheme.contains("basic"), "no Basic auth with alice's password next to it");
	}

	@Test
	public void aRejectedOauthRequestFailsTheBuilderWithTheReasonInsteadOfBuildingAnUnauthenticatedSpec() {
		reply(401, "{\"error\":\"invalid_client\"}");

		IllegalStateException failure = Assert.expectThrows(IllegalStateException.class,
				() -> new RequestSpecBuilder().setOAuth(baseUri, "/oauth/token", "password", "c", "s", "u", "p"));

		Assert.assertTrue(failure.getMessage().contains("invalid_client"), failure.getMessage());
	}

	@Test
	public void aFailedRefreshExchangeFailsTheBuilderInsteadOfSilentlySkippingAuth() {
		reply(400, "{\"error\":\"invalid_grant\"}");
		RequestSpecBuilder builder = new RequestSpecBuilder();
		builder.auth.setOAuthBaseUrl(baseUri + "/oauth/token");

		Assert.expectThrows(IllegalStateException.class,
				() -> builder.setBearerToken("refresh_token", "c", "s", "expired-refresh"));
	}

	@Test
	public void basicAuthAloneStillWorksAsBefore() {
		RequestSpecification spec = new RequestSpecBuilder().setBasicAuth("alice", "hunter2").build();

		String scheme = ((FilterableRequestSpecification) spec).getAuthenticationScheme().getClass().getSimpleName().toLowerCase();
		Assert.assertTrue(scheme.contains("basic"), scheme);
	}

	@Test
	public void anExplicitBearerTokenWinsOverBasicAuthAndOnlyOneCredentialIsSent() {
		RequestSpecification spec = new RequestSpecBuilder()
				.setBasicAuth("alice", "hunter2")
				.setBearerToken("abc123")
				.build();

		FilterableRequestSpecification filterable = (FilterableRequestSpecification) spec;
		Assert.assertEquals(filterable.getHeaders().getValue("Authorization"), "Bearer abc123");
		String scheme = filterable.getAuthenticationScheme().getClass().getSimpleName().toLowerCase();
		Assert.assertFalse(scheme.contains("basic"), "Basic auth must not ride along with the bearer token: " + scheme);
		Assert.assertEquals(filterable.getHeaders().getValues("Authorization").size(), 1);
	}
}
