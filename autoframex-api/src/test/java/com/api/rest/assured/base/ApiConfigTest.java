package com.api.rest.assured.base;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;
import com.framework.exception.FrameworkException;

/** The API under test described through the framework's configuration chain, with no project-specific plumbing. */
public class ApiConfigTest {

	private FakeHttpServer server;

	@BeforeMethod
	public void startServer() throws IOException {
		TokenProvider.clear();
		server = FakeHttpServer.start()
				.on("/oauth/token", r -> FakeHttpServer.Reply.json(200, "{\"access_token\":\"tok-1\",\"expires_in\":3600}"))
				.on("/api/ping", r -> FakeHttpServer.Reply.json(200, "{\"ok\":true}"));
	}

	@AfterMethod(alwaysRun = true)
	public void stopServer() {
		server.close();
		TokenProvider.clear();
		System.clearProperty("apiBaseUri");
		System.clearProperty("apiBasePath");
	}

	private Map<String, String> params(String... keyValues) {
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	private ResponseAPI ping(RequestSpecBuilder base) {
		return new RestAssuredBase().send(base, ApiRequest.get("ping"));
	}

	// ---------------------------------------------------------------- no sign-in

	@Test
	public void aBaseUriAndPathAreEnoughForAnOpenApi() {
		ApiConfig config = ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiBasePath", "/api"));
		Assert.assertEquals(config.authentication(), "none");
		Assert.assertEquals(ping(config.requestSpecBuilder()).getStatusCode(), 200);
		Assert.assertNull(server.last().headers.get("authorization"));
	}

	@Test
	public void systemPropertiesAreTheLastSource() {
		System.setProperty("apiBaseUri", server.baseUri());
		System.setProperty("apiBasePath", "/api");
		Assert.assertEquals(ping(ApiConfig.load().requestSpecBuilder()).getStatusCode(), 200);
	}

	@Test
	public void aTestNgParameterBeatsASystemProperty() {
		System.setProperty("apiBaseUri", "http://127.0.0.1:1");
		ApiConfig config = ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiBasePath", "/api"));
		Assert.assertEquals(ping(config.requestSpecBuilder()).getStatusCode(), 200);
	}

	@Test
	public void aTrailingSlashOnTheBaseUriMakesNoDoubleSlash() {
		ApiConfig config = ApiConfig.load(params("apiBaseUri", server.baseUri() + "/", "apiBasePath", "/api",
				"apiOAuthBasePath", "/oauth/token", "apiClientId", "c", "apiClientSecret", "s"));
		Assert.assertEquals(ping(config.requestSpecBuilder()).getStatusCode(), 200);
		Assert.assertEquals(server.hits("/oauth/token"), 1, "the token URL is host/oauth/token, not host//oauth/token");
	}

	@Test
	public void aMissingBaseUriSaysHowToSetIt() {
		try {
			ApiConfig.load(params()).requestSpecBuilder();
			Assert.fail("no base URI");
		} catch (FrameworkException e) {
			Assert.assertTrue(e.getMessage().contains("apiBaseUri") && e.getMessage().contains("API_BASE_URI"), e.getMessage());
		}
	}

	// -------------------------------------------------------------------- OAuth

	@Test
	public void aClientIdTurnsOnOAuthWithTheClientCredentialsGrant() {
		ApiConfig config = ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiBasePath", "/api",
				"apiOAuthBasePath", "/oauth/token", "apiClientId", "client-1", "apiClientSecret", "secret-1"));
		Assert.assertEquals(config.authentication(), "oauth");

		Assert.assertEquals(ping(config.requestSpecBuilder()).getStatusCode(), 200);
		Assert.assertEquals(server.last().headers.get("authorization"), "Bearer tok-1");
		Assert.assertTrue(server.received().get(0).body.contains("grant_type=client_credentials"), server.received().get(0).body);
	}

	@Test
	public void thePasswordGrantNeedsTheUserToo() {
		Map<String, String> p = params("apiBaseUri", server.baseUri(), "apiOAuthBasePath", "/oauth/token",
				"apiGrantType", "password", "apiClientId", "c", "apiClientSecret", "s");
		try {
			ApiConfig.load(p).requestSpecBuilder();
			Assert.fail("the password grant needs a user");
		} catch (FrameworkException e) {
			Assert.assertTrue(e.getMessage().contains("API_USERNAME") && e.getMessage().contains("API_PASSWORD"), e.getMessage());
		}
	}

	@Test
	public void aClientIdWithoutItsSecretOrTokenEndpointNamesWhatIsMissing() {
		try {
			ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiClientId", "c")).requestSpecBuilder();
			Assert.fail("half-configured OAuth");
		} catch (FrameworkException e) {
			Assert.assertTrue(e.getMessage().contains("API_CLIENT_SECRET"), e.getMessage());
			Assert.assertTrue(e.getMessage().contains("API_OAUTH_BASE_PATH"), e.getMessage());
		}
		Assert.assertEquals(server.hits("/oauth/token"), 0, "no token request is made with incomplete settings");
	}

	// ------------------------------------------------------------ bearer and basic

	@Test
	public void aBearerTokenIsSentAsItIs() {
		ApiConfig config = ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiBasePath", "/api",
				"apiBearerToken", "ready-made"));
		Assert.assertEquals(config.authentication(), "bearer");
		ping(config.requestSpecBuilder());
		Assert.assertEquals(server.last().headers.get("authorization"), "Bearer ready-made");
	}

	@Test
	public void aBearerTokenBeatsOAuthSoNoTokenIsRequested() {
		ApiConfig config = ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiBasePath", "/api",
				"apiBearerToken", "ready-made", "apiOAuthBasePath", "/oauth/token", "apiClientId", "c", "apiClientSecret", "s"));
		ping(config.requestSpecBuilder());
		Assert.assertEquals(server.hits("/oauth/token"), 0);
	}

	@Test
	public void usernameAndPasswordGiveBasicAuth() {
		ApiConfig config = ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiBasePath", "/api",
				"apiUsername", "alice", "apiPassword", "pw"));
		Assert.assertEquals(config.authentication(), "basic");
		ping(config.requestSpecBuilder());
		Assert.assertTrue(server.last().headers.get("authorization").startsWith("Basic "));
	}

	@Test
	public void aLoneUsernameIsRejected() {
		try {
			ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiUsername", "alice")).requestSpecBuilder();
			Assert.fail("a user without a password");
		} catch (FrameworkException e) {
			Assert.assertTrue(e.getMessage().contains("API_PASSWORD"), e.getMessage());
		}
	}

	// ----------------------------------------------------------------- secrets

	@Test
	public void toStringNeverShowsASecret() {
		String text = ApiConfig.load(params("apiBaseUri", "https://host", "apiClientId", "CLIENT-ID-VALUE",
				"apiClientSecret", "SECRET-VALUE-1", "apiUsername", "USER-VALUE", "apiPassword", "PASS-VALUE-1",
				"apiBearerToken", "TOKEN-VALUE-1")).toString();
		for (String secret : new String[] { "CLIENT-ID-VALUE", "SECRET-VALUE-1", "USER-VALUE", "PASS-VALUE-1", "TOKEN-VALUE-1" }) {
			Assert.assertFalse(text.contains(secret), text);
		}
		Assert.assertTrue(text.contains("baseUri=https://host"), text);
	}

	@Test
	public void blankValuesCountAsNotSet() {
		// Jenkins gives an empty environment variable for an empty build parameter.
		ApiConfig config = ApiConfig.load(params("apiBaseUri", server.baseUri(), "apiBasePath", "/api",
				"apiClientId", "  ", "apiBearerToken", ""));
		Assert.assertEquals(config.authentication(), "none");
	}
}
