package com.api.rest.assured.base;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.api.design.ResponseAPI;
import com.sun.net.httpserver.HttpServer;

/**
 * HTTP header names are case-insensitive and a header may be sent more than once. The old
 * {@code HashMap<name, value>} made {@code get("content-type")} miss {@code Content-Type} and kept only
 * the last of several {@code Set-Cookie} headers.
 */
public class ResponseHeadersTest {

	private HttpServer server;
	private ResponseAPI response;

	@BeforeClass
	public void callAServerThatSendsRepeatedHeaders() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			exchange.getRequestBody().readAllBytes();
			byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.getResponseHeaders().add("Set-Cookie", "a=1");
			exchange.getResponseHeaders().add("Set-Cookie", "b=2");
			exchange.getResponseHeaders().add("X-Request-Id", "req-42");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();

		response = new RestAssuredBase().get(
				new RequestSpecBuilder().setBaseUri("http://127.0.0.1:" + server.getAddress().getPort()).build(), "/x");
	}

	@AfterClass(alwaysRun = true)
	public void stop() {
		server.stop(0);
	}

	@Test
	public void theHeaderMapIsCaseInsensitive() {
		Assert.assertEquals(response.getHeaders().get("x-request-id"), "req-42");
		Assert.assertEquals(response.getHeaders().get("X-REQUEST-ID"), "req-42");
		Assert.assertEquals(response.getHeaders().get("content-type"), "application/json");
	}

	@Test
	public void aRepeatedHeaderKeepsEveryValueInTheMap() {
		String cookies = response.getHeaders().get("set-cookie");
		Assert.assertTrue(cookies.contains("a=1") && cookies.contains("b=2"), cookies);
	}

	@Test
	public void getHeaderValuesReturnsThemSeparately() {
		Assert.assertEquals(response.getHeaderValues("SET-COOKIE").size(), 2);
		Assert.assertTrue(response.getHeaderValues("Set-Cookie").contains("a=1"));
		Assert.assertTrue(response.getHeaderValues("Set-Cookie").contains("b=2"));
	}

	@Test
	public void getHeaderReturnsOneValueOrNull() {
		Assert.assertEquals(response.getHeader("x-request-id"), "req-42");
		Assert.assertNull(response.getHeader("x-not-there"));
		Assert.assertTrue(response.getHeaderValues("x-not-there").isEmpty());
	}
}
