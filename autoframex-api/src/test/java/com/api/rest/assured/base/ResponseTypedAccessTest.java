package com.api.rest.assured.base;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;
import com.framework.exception.Categorized;

import io.restassured.common.mapper.TypeRef;

/**
 * Reading a response with its types kept, the diagnostics around a failed read, and what a call that gets no
 * response at all throws. {@code getJsonPath} could only return text (a number became "5", a missing path
 * became null), and a refused connection surfaced as a bare {@code ConnectException} that did not say which
 * call it was.
 */
public class ResponseTypedAccessTest {

	public static class Item {
		public String name;
		public int qty;
	}

	public static class Named {
		public String name;
	}

	private FakeHttpServer server;
	private RequestSpecBuilder base;

	@BeforeClass
	public void startServer() throws IOException {
		server = FakeHttpServer.start();
		server.on("/api/doc", request -> FakeHttpServer.Reply.json(200,
				"{\"n\":5,\"price\":9.5,\"flag\":true,\"name\":\"widget\",\"tags\":[\"a\",\"b\"],"
						+ "\"items\":[{\"name\":\"x\",\"qty\":1},{\"name\":\"y\",\"qty\":2}]}")
				.header("Set-Cookie", "sid=abc123; Path=/"));
		server.on("/api/list", request -> FakeHttpServer.Reply.json(200,
				"[{\"name\":\"x\",\"qty\":1},{\"name\":\"y\",\"qty\":2}]"));
		server.on("/api/extra", request -> FakeHttpServer.Reply.json(200, "{\"name\":\"a\",\"unexpected\":42}"));
		server.on("/api/login", request -> new FakeHttpServer.Reply(200, "<html>Sign in, access_token=LEAKED-123</html>")
				.header("Content-Type", "text/html"));
		base = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api");
	}

	@AfterClass(alwaysRun = true)
	public void stopServer() {
		server.close();
	}

	private ResponseAPI call(String path) {
		return new RestAssuredBase().send(base, ApiRequest.get(path));
	}

	// ---------------------------------------------------------------- typed access

	@Test
	public void pathKeepsTheTypeOfTheValue() {
		ResponseAPI response = call("doc");

		Assert.assertEquals(response.path("n", Integer.class), Integer.valueOf(5));
		Assert.assertEquals(response.path("price", Double.class), Double.valueOf(9.5));
		Assert.assertEquals(response.path("flag", Boolean.class), Boolean.TRUE);
		Assert.assertEquals(response.path("name", String.class), "widget");
		Assert.assertEquals(response.path("n", Object.class), 5, "Object.class gives the natural type, a Number");
	}

	@Test
	public void aMissingPathIsNullNotTheTextNull() {
		ResponseAPI response = call("doc");

		Assert.assertNull(response.path("does.not.exist", String.class));
		Assert.assertEquals(response.pathList("does.not.exist", String.class), List.of());
	}

	@Test
	public void pathListReturnsTheElementsOfAnArray() {
		ResponseAPI response = call("doc");

		Assert.assertEquals(response.pathList("tags", String.class), List.of("a", "b"));
		Assert.assertEquals(response.pathList("items.name", String.class), List.of("x", "y"));
		Assert.assertEquals(response.pathList("items.qty", Integer.class), List.of(1, 2));
	}

	@Test
	public void aGenericBodyIsReadWithATypeRef() {
		List<Item> items = call("list").as(new TypeRef<List<Item>>() { });

		Assert.assertEquals(items.size(), 2);
		Assert.assertEquals(items.get(1).name, "y");
		Assert.assertEquals(items.get(1).qty, 2);
	}

	@Test
	public void aNewFieldInTheResponseDoesNotBreakReadingTheKnownOnes() {
		Named named = call("extra").getBodyAs(Named.class);

		Assert.assertEquals(named.name, "a");
	}

	@Test
	public void timeCookiesAndRawBytesAreAvailable() {
		ResponseAPI response = call("doc");

		Assert.assertTrue(response.getResponseTimeMs() >= 0);
		Assert.assertEquals(response.getCookies().get("sid"), "abc123");
		Assert.assertTrue(response.getBodyBytes().length > 10);
		Assert.assertEquals(new String(response.getBodyBytes(), java.nio.charset.StandardCharsets.UTF_8), response.getBody());
	}

	// ------------------------------------------------------------- failed reads

	@Test
	public void readingJsonFromAnHtmlPageSaysWhatCameBackWithoutTheSecrets() {
		ResponseAPI response = call("login");

		IllegalStateException e = Assert.expectThrows(IllegalStateException.class, () -> response.path("n", Integer.class));

		Assert.assertTrue(e.getMessage().contains("not JSON"), e.getMessage());
		Assert.assertTrue(e.getMessage().contains("GET " + server.baseUri() + "/api/login"), "the request line: " + e.getMessage());
		Assert.assertTrue(e.getMessage().contains("text/html"), e.getMessage());
		Assert.assertFalse(e.getMessage().contains("LEAKED-123"), "the body preview is masked: " + e.getMessage());
		Assert.assertFalse(e.getMessage().toLowerCase().contains("servicenow"),
				"no project-specific advice in the generic module: " + e.getMessage());
	}

	@Test
	public void describeRequestNamesTheCallAndMasksTheQueryString() {
		ResponseAPI response = new RestAssuredBase().send(base, ApiRequest.get("doc").queryParam("access_token", "QUERY-SECRET"));

		Assert.assertTrue(response.describeRequest().startsWith("GET " + server.baseUri() + "/api/doc"), response.describeRequest());
		Assert.assertFalse(response.describeRequest().contains("QUERY-SECRET"), response.describeRequest());
	}

	// -------------------------------------------------------- no response at all

	@Test
	public void aRefusedConnectionThrowsAnApiCallExceptionThatNamesTheCall() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		RequestSpecBuilder unreachable = new RequestSpecBuilder().setBaseUri("http://127.0.0.1:" + closedPort).setBasePath("/api");

		ApiCallException e = Assert.expectThrows(ApiCallException.class,
				() -> new RestAssuredBase().send(unreachable, ApiRequest.get("doc").queryParam("access_token", "QUERY-SECRET")));

		Assert.assertEquals(e.method(), "GET");
		Assert.assertTrue(e.uri().contains("127.0.0.1:" + closedPort + "/api/doc"), e.uri());
		Assert.assertFalse(e.getMessage().contains("QUERY-SECRET"), "credentials in the URL are masked: " + e.getMessage());
		Assert.assertTrue(e.elapsedMs() >= 0);
		Assert.assertNotNull(e.getCause(), "the original exception is kept as the cause");
		Assert.assertEquals(((Categorized) e).category(), "API_TRANSPORT");
	}

	@Test
	public void redactionMasksCredentialsAndCutsLongText() {
		Assert.assertFalse(ApiRedaction.mask("https://x/api?access_token=abc&q=1").contains("abc"));
		Assert.assertFalse(ApiRedaction.mask("{\"password\":\"hunter2\"}").contains("hunter2"));
		Assert.assertEquals(ApiRedaction.preview("x".repeat(600), 100).length(), 103);
		Assert.assertEquals(ApiRedaction.preview(null, 100), "(none)");
	}
}
