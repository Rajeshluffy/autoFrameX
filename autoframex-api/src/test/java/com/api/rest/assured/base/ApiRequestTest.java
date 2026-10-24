package com.api.rest.assured.base;

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;

/**
 * {@link ApiRequest} is the per-call part of a request as an immutable value. These pin that it never changes
 * after it is built (so one request can be varied without one call's parameters leaking into the next, which
 * the builder's single-slot setters did), and that what it describes is what goes over the wire, including the
 * verbs the old client could not send.
 */
public class ApiRequestTest {

	private FakeHttpServer server;
	private RequestSpecBuilder base;

	@BeforeClass
	public void startServer() throws IOException {
		server = FakeHttpServer.start();
		server.on("/api", request -> FakeHttpServer.Reply.json(200, "{\"method\":\"" + request.method + "\"}"));
		base = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api");
	}

	@AfterClass(alwaysRun = true)
	public void stopServer() {
		server.close();
	}

	// ------------------------------------------------------------- immutability

	@Test
	public void everyMethodReturnsANewRequestAndLeavesTheOriginalAlone() {
		ApiRequest original = ApiRequest.get("incident");

		ApiRequest varied = original.queryParam("a", 1).header("X-One", "1").cookie("c", "v")
				.pathParam("p", "v").body("{}").contentType("application/json");

		Assert.assertNotSame(varied, original);
		Assert.assertTrue(original.queryParams().isEmpty());
		Assert.assertTrue(original.headers().isEmpty());
		Assert.assertTrue(original.cookies().isEmpty());
		Assert.assertTrue(original.pathParams().isEmpty());
		Assert.assertNull(original.body());
		Assert.assertNull(original.contentType());
	}

	@Test
	public void twoRequestsDerivedFromOneDoNotShareParameters() {
		ApiRequest list = ApiRequest.get("incident");

		ApiRequest byNumber = list.queryParam("sysparm_query", "number=INC1");
		ApiRequest all = list.queryParam("sysparm_limit", 10);

		Assert.assertTrue(byNumber.queryParams().containsKey("sysparm_query"));
		Assert.assertFalse(byNumber.queryParams().containsKey("sysparm_limit"));
		Assert.assertFalse(all.queryParams().containsKey("sysparm_query"));
	}

	@Test
	public void theReturnedMapsCannotBeUsedToChangeTheRequest() {
		ApiRequest request = ApiRequest.get("x").queryParam("a", 1).header("h", "v");

		Assert.assertThrows(UnsupportedOperationException.class, () -> request.queryParams().put("b", null));
		Assert.assertThrows(UnsupportedOperationException.class, () -> request.headers().put("b", "v"));
	}

	@Test
	public void toStringShowsNamesNeverValues() {
		ApiRequest request = ApiRequest.post("incident").queryParam("access_token", "SECRET-VALUE").body("{\"pw\":\"x\"}");

		Assert.assertTrue(request.toString().contains("access_token"));
		Assert.assertFalse(request.toString().contains("SECRET-VALUE"));
		Assert.assertFalse(request.toString().contains("\"pw\""));
	}

	@Test
	public void blankNamesAndNullPartsAreRejectedEarly() {
		Assert.assertThrows(IllegalArgumentException.class, () -> ApiRequest.get(null));
		Assert.assertThrows(IllegalArgumentException.class, () -> ApiRequest.get("x").queryParam(" ", 1));
		Assert.assertThrows(IllegalArgumentException.class, () -> ApiRequest.get("x").header("", "v"));
	}

	// -------------------------------------------------------------- on the wire

	@Test
	public void patchSendsTheBodyToTheFilledInPath() {
		ResponseAPI response = new RestAssuredBase().send(base,
				ApiRequest.patch("incident/{sysId}").pathParam("sysId", "abc123")
						.contentType("application/json").body("{\"state\":\"2\"}"));

		Assert.assertEquals(response.getStatusCode(), 200);
		Assert.assertEquals(server.last().method, "PATCH");
		Assert.assertEquals(server.last().path, "/api/incident/abc123");
		Assert.assertEquals(server.last().body, "{\"state\":\"2\"}");
	}

	@Test
	public void aQueryParameterCanBeRepeated() {
		new RestAssuredBase().send(base,
				ApiRequest.get("incident").queryParam("sysparm_fields", "number", "state").queryParam("sysparm_limit", 5));

		String query = server.last().query;
		Assert.assertTrue(query.contains("sysparm_fields=number") && query.contains("sysparm_fields=state"), query);
		Assert.assertTrue(query.contains("sysparm_limit=5"), query);
	}

	@Test
	public void headersAndCookiesAreSent() {
		new RestAssuredBase().send(base,
				ApiRequest.get("incident").header("X-Correlation-Id", "corr-1").cookie("session", "s-1"));

		Assert.assertEquals(server.last().headers.get("x-correlation-id"), "corr-1");
		Assert.assertTrue(server.last().headers.get("cookie").contains("session=s-1"), server.last().headers.toString());
	}

	@Test
	public void headAndOptionsAreSupported() {
		RestAssuredBase client = new RestAssuredBase();

		Assert.assertEquals(client.send(base, ApiRequest.head("incident")).getStatusCode(), 200);
		Assert.assertEquals(server.last().method, "HEAD");

		Assert.assertEquals(client.send(base, ApiRequest.options("incident")).getStatusCode(), 200);
		Assert.assertEquals(server.last().method, "OPTIONS");
	}

	@Test
	public void deleteAndPutStillWorkThroughSend() {
		RestAssuredBase client = new RestAssuredBase();

		client.send(base, ApiRequest.delete("incident/1"));
		Assert.assertEquals(server.last().method, "DELETE");

		client.send(base, ApiRequest.put("incident/1").contentType("application/json").body("{}"));
		Assert.assertEquals(server.last().method, "PUT");
	}

	@Test
	public void theOldPatchMethodOnTheClientWorksToo() {
		ResponseAPI response = new RestAssuredBase().patch(
				new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api")
						.setContentType(io.restassured.http.ContentType.JSON).build(),
				"incident/9", "{\"a\":1}");

		Assert.assertEquals(response.getStatusCode(), 200);
		Assert.assertEquals(server.last().method, "PATCH");
	}
}
