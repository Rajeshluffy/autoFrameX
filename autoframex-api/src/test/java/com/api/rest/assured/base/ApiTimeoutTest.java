package com.api.rest.assured.base;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;

/**
 * REST Assured sets no timeouts, so a server that accepts the connection and then says nothing blocked the
 * calling thread until the CI job's own timeout. The "server" here is a listening socket that never answers.
 */
public class ApiTimeoutTest {

	private ServerSocket silentServer;
	private String savedConnect;
	private String savedRead;

	@BeforeMethod
	public void startASilentServer() throws IOException {
		silentServer = new ServerSocket(0);   // accepts connections in the OS backlog, never reads or answers
		savedConnect = System.clearProperty("apiConnectTimeoutMs");
		savedRead = System.clearProperty("apiReadTimeoutMs");
	}

	@AfterMethod(alwaysRun = true)
	public void stop() throws IOException {
		silentServer.close();
		restore("apiConnectTimeoutMs", savedConnect);
		restore("apiReadTimeoutMs", savedRead);
	}

	private static void restore(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}

	private String silentUri() {
		return "http://127.0.0.1:" + silentServer.getLocalPort();
	}

	private static long millisTaken(Runnable call) {
		long start = System.nanoTime();
		try {
			call.run();
			Assert.fail("the call to a server that never answers should have failed");
		} catch (AssertionError e) {
			throw e;
		} catch (Exception expected) {
			// Through the client the timeout arrives as an ApiCallException (naming the call) whose cause is the
			// socket timeout; the token request is made without the client, so there it is the raw exception.
			Throwable timeout = expected instanceof ApiCallException ? expected.getCause() : expected;
			Assert.assertTrue(timeout instanceof java.net.SocketTimeoutException,
					"expected a socket timeout, got " + expected);
		}
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
	}

	// ------------------------------------------------------------------ defaults

	@Test
	public void theDefaultsAreTenSecondsToConnectAndThirtyToRead() {
		RestAssuredConfig config = ApiTimeouts.applyDefaults(null);
		Assert.assertEquals(config.getHttpClientConfig().params().get("http.connection.timeout"), 10_000);
		Assert.assertEquals(config.getHttpClientConfig().params().get("http.socket.timeout"), 30_000);
	}

	@Test
	public void aTimeoutSomeoneConfiguredOnPurposeIsNotOverwritten() {
		RestAssuredConfig custom = RestAssured.config()
				.httpClient(HttpClientConfig.httpClientConfig().setParam("http.socket.timeout", 1234));

		RestAssuredConfig applied = ApiTimeouts.applyDefaults(custom);

		Assert.assertEquals(applied.getHttpClientConfig().params().get("http.socket.timeout"), 1234);
		Assert.assertEquals(applied.getHttpClientConfig().params().get("http.connection.timeout"), 10_000);
	}

	@Test
	public void aNonPositiveSettingFallsBackToTheDefaultInsteadOfWaitingForever() {
		System.setProperty("apiReadTimeoutMs", "0");
		Assert.assertEquals(ApiTimeouts.readMs(), 30_000);
		System.setProperty("apiReadTimeoutMs", "-5");
		Assert.assertEquals(ApiTimeouts.readMs(), 30_000);
		System.setProperty("apiReadTimeoutMs", "750");
		Assert.assertEquals(ApiTimeouts.readMs(), 750);
	}

	// --------------------------------------------------------- the calls give up

	@Test(timeOut = 30_000)
	public void aCallThroughTheClientGivesUpWhenTheServerNeverAnswers() {
		System.setProperty("apiReadTimeoutMs", "500");
		RequestSpecification spec = new RequestSpecBuilder().setBaseUri(silentUri()).build();

		long taken = millisTaken(() -> new RestAssuredBase().get(spec, "/never"));

		Assert.assertTrue(taken < 10_000, "should give up after about the 500 ms read timeout, took " + taken + " ms");
	}

	@Test(timeOut = 30_000)
	public void aSpecBuiltWithoutTheBuilderGetsTheDefaultsToo() {
		System.setProperty("apiReadTimeoutMs", "500");
		RequestSpecification plain = RestAssured.given().baseUri(silentUri());

		long taken = millisTaken(() -> new RestAssuredBase().get(plain, "/never"));

		Assert.assertTrue(taken < 10_000, "took " + taken + " ms");
	}

	@Test(timeOut = 30_000)
	public void timeoutsSetOnTheBuilderBeatTheDefaults() {
		RequestSpecification spec = new RequestSpecBuilder().setBaseUri(silentUri()).setTimeouts(200, 400).build();

		long taken = millisTaken(() -> new RestAssuredBase().get(spec, "/never"));

		Assert.assertTrue(taken < 10_000, "should give up after about 400 ms, took " + taken + " ms");
	}

	@Test(timeOut = 30_000)
	public void theTokenRequestAlsoGivesUp() {
		System.setProperty("apiReadTimeoutMs", "500");
		RequestAuthentication auth = new RequestAuthentication().setOAuthBaseUrl(silentUri() + "/token");

		long taken = millisTaken(() -> auth.getOAuthToken("password", "id", "secret", "user", "pass"));

		Assert.assertTrue(taken < 10_000, "took " + taken + " ms");
	}
}
