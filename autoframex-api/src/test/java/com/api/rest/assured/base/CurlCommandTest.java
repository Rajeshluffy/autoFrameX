package com.api.rest.assured.base;

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.api.assertions.ApiAssertions;
import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;

/** The curl command in a failed assertion: enough to repeat the call, never a credential. */
public class CurlCommandTest {

	private FakeHttpServer server;
	private RequestSpecBuilder base;

	@BeforeClass
	public void startServer() throws IOException {
		server = FakeHttpServer.start().on("/api/", r -> FakeHttpServer.Reply.json(500, "{\"error\":\"boom\"}"));
		base = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api");
	}

	@AfterClass(alwaysRun = true)
	public void stopServer() {
		server.close();
	}

	private ResponseAPI post() {
		return new RestAssuredBase().send(base, ApiRequest.post("incident")
				.header("Authorization", "Bearer SECRET-TOKEN-XYZ")
				.header("X-Api-Key", "KEY-123456")
				.header("X-Trace", "trace-1")
				.contentType("application/json")
				.queryParam("access_token", "QUERY-SECRET")
				.body("{\"short_description\":\"it's broken\",\"password\":\"hunter2hunter2\"}"));
	}

	@Test
	public void theCommandRepeatsTheCall() {
		String curl = post().reproCommand();
		Assert.assertNotNull(curl);
		Assert.assertTrue(curl.startsWith("curl -X POST '" + server.baseUri() + "/api/incident"), curl);
		Assert.assertTrue(curl.contains("-H 'X-Trace: trace-1'"), curl);
		Assert.assertTrue(curl.contains("--data-raw '"), curl);
		Assert.assertTrue(curl.contains("it'\\''s broken"), "an embedded quote must be escaped for the shell: " + curl);
	}

	@Test
	public void noCredentialAppearsInIt() {
		String curl = post().reproCommand();
		Assert.assertFalse(curl.contains("SECRET-TOKEN-XYZ"), curl);
		Assert.assertFalse(curl.contains("KEY-123456"), curl);
		Assert.assertFalse(curl.contains("QUERY-SECRET"), curl);
		Assert.assertFalse(curl.contains("hunter2hunter2"), curl);
		Assert.assertTrue(curl.contains("Authorization: <redacted>"), curl);
	}

	@Test
	public void aFailedAssertionShowsIt() {
		AssertionError error = null;
		try {
			ApiAssertions.that(post()).status(200);
		} catch (AssertionError e) {
			error = e;
		}
		Assert.assertNotNull(error, "a 500 is not OK");
		Assert.assertTrue(error.getMessage().contains("Repro       : curl -X POST"), error.getMessage());
		Assert.assertFalse(error.getMessage().contains("SECRET-TOKEN-XYZ"), error.getMessage());
	}

	@Test
	public void quotingSurvivesAShell() {
		Assert.assertEquals(CurlCommand.quote("a b"), "'a b'");
		Assert.assertEquals(CurlCommand.quote("it's"), "'it'\\''s'");
	}
}
