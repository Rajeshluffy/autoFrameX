package com.api.rest.assured.base;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;

/**
 * A throw-away HTTP server for tests: no network, no WireMock. Register a handler per path; every request is
 * recorded so a test can assert on what was actually sent (method, query, headers, body).
 */
final class FakeHttpServer implements AutoCloseable {

	/** What the server received. Header names are lower-cased. */
	static final class Received {
		final String method;
		final String path;
		final String query;
		final Map<String, String> headers;
		final String body;

		Received(String method, String path, String query, Map<String, String> headers, String body) {
			this.method = method;
			this.path = path;
			this.query = query == null ? "" : query;
			this.headers = headers;
			this.body = body;
		}
	}

	/** What the server answers. */
	static final class Reply {
		final int status;
		final String body;
		final Map<String, String> headers = new LinkedHashMap<>();
		long delayMs;

		Reply(int status, String body) {
			this.status = status;
			this.body = body == null ? "" : body;
			this.headers.put("Content-Type", "application/json");
		}

		static Reply json(int status, String body) {
			return new Reply(status, body);
		}

		Reply header(String name, String value) {
			headers.put(name, value);
			return this;
		}

		Reply delay(long millis) {
			this.delayMs = millis;
			return this;
		}
	}

	private final HttpServer server;
	private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
	private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

	private FakeHttpServer(HttpServer server) {
		this.server = server;
	}

	static FakeHttpServer start() throws IOException {
		FakeHttpServer fake = new FakeHttpServer(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
		fake.server.start();
		return fake;
	}

	/** Answers every request to {@code path} (and below) with whatever {@code handler} returns for it. */
	FakeHttpServer on(String path, Function<Received, Reply> handler) {
		hits.put(path, new AtomicInteger());
		server.createContext(path, exchange -> {
			Map<String, String> headers = new LinkedHashMap<>();
			exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(", ", v)));
			Received request = new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
					exchange.getRequestURI().getRawQuery(), headers,
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			received.add(request);
			hits.get(path).incrementAndGet();

			Reply reply = handler.apply(request);
			if (reply.delayMs > 0) {
				try {
					Thread.sleep(reply.delayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = reply.body.getBytes(StandardCharsets.UTF_8);
			for (Map.Entry<String, String> header : reply.headers.entrySet()) {
				exchange.getResponseHeaders().add(header.getKey(), header.getValue());
			}
			boolean noBody = bytes.length == 0 || "HEAD".equals(exchange.getRequestMethod());
			exchange.sendResponseHeaders(reply.status, noBody ? -1 : bytes.length);
			if (!noBody) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		return this;
	}

	String baseUri() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	int hits(String path) {
		AtomicInteger count = hits.get(path);
		return count == null ? 0 : count.get();
	}

	List<Received> received() {
		synchronized (received) {
			return new ArrayList<>(received);
		}
	}

	Received last() {
		List<Received> all = received();
		return all.get(all.size() - 1);
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
