package com.framework.security;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Locks in that a security check can no longer pass because nothing was checked.
 *
 * <p>Each test talks to a throw-away HTTP server that imitates the few ZAP JSON API
 * endpoints {@link ZapSecurityUtils} uses, so no ZAP install is needed. The replies
 * are modelled on ZAP's documented API; they have not been compared against a live
 * ZAP in this environment.
 *
 * <p>Failure modes covered, each of which used to end in a green test:
 * <ul>
 *   <li>the alert list read before the passive-scan queue had drained;</li>
 *   <li>an HTTP error or a ZAP {@code {"code","message"}} error body read as "no alerts";</li>
 *   <li>a reply without an alert list read as "no alerts";</li>
 *   <li>ZAP having recorded no traffic at all.</li>
 * </ul>
 */
public class ZapSecurityUtilsTest {

	private HttpServer server;
	private String zap;
	/** path -> queued replies; the last reply repeats once the queue is down to one. */
	private final ConcurrentMap<String, Deque<Reply>> routes = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, AtomicInteger> hits = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> lastQuery = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> lastApiKey = new ConcurrentHashMap<>();

	private static final class Reply {
		final int status;
		final String body;

		Reply(int status, String body) {
			this.status = status;
			this.body = body;
		}
	}

	@BeforeMethod
	public void startFakeZap() throws IOException {
		routes.clear();
		hits.clear();
		lastQuery.clear();
		lastApiKey.clear();
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			hits.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
			lastQuery.put(path, String.valueOf(exchange.getRequestURI().getQuery()));
			lastApiKey.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("X-ZAP-API-Key")));
			Deque<Reply> queue = routes.get(path);
			Reply reply;
			if (queue == null || queue.isEmpty()) {
				reply = new Reply(404, "{\"code\":\"does_not_exist\",\"message\":\"no such endpoint " + path + "\"}");
			} else {
				synchronized (queue) {
					reply = queue.size() > 1 ? queue.poll() : queue.peek();
				}
			}
			byte[] bytes = reply.body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(reply.status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		server.start();
		zap = "http://localhost:" + server.getAddress().getPort();
	}

	@AfterMethod(alwaysRun = true)
	public void stopFakeZap() {
		if (server != null) {
			server.stop(0);
		}
	}

	private void on(String path, int status, String... bodies) {
		Deque<Reply> queue = new ArrayDeque<>();
		for (String body : bodies) {
			queue.add(new Reply(status, body));
		}
		routes.put(path, queue);
	}

	private void queueEmpty() {
		on("/JSON/pscan/view/recordsToScan/", 200, "{\"recordsToScan\":\"0\"}");
	}

	private static String alerts(String... alertJson) {
		return "{\"alerts\":[" + String.join(",", alertJson) + "]}";
	}

	private static String alert(String name, String risk) {
		return "{\"alert\":\"" + name + "\",\"name\":\"" + name + "\",\"risk\":\"" + risk + "\",\"url\":\"http://app/x\"}";
	}

	// -------------------------------------------------------------------------
	// passive scan: start / wait
	// -------------------------------------------------------------------------

	@Test
	public void passiveScanStartsTheSpiderAndReturnsWithoutWaiting() {
		on("/JSON/pscan/action/enableAllScanners/", 200, "{\"Result\":\"OK\"}");
		on("/JSON/spider/action/scan/", 200, "{\"scan\":\"7\"}");

		ZapSecurityUtils.startPassiveScan(zap, "http://app/login");

		Assert.assertEquals(hits.get("/JSON/spider/action/scan/").get(), 1);
		Assert.assertTrue(lastQuery.get("/JSON/spider/action/scan/").contains("url="), lastQuery.toString());
		Assert.assertNull(hits.get("/JSON/spider/view/status/"), "start must not wait for the crawl");
	}

	@Test
	public void startingTheSpiderFailsLoudlyWhenZapRefusesOrSendsNoScanId() {
		on("/JSON/pscan/action/enableAllScanners/", 200, "{\"Result\":\"OK\"}");

		on("/JSON/spider/action/scan/", 400, "{\"code\":\"url_not_found\",\"message\":\"URL is not valid\"}");
		IllegalStateException refused = Assert.expectThrows(IllegalStateException.class,
				() -> ZapSecurityUtils.startPassiveScan(zap, "http://app/login"));
		Assert.assertTrue(refused.getMessage().contains("HTTP 400"), refused.getMessage());

		on("/JSON/spider/action/scan/", 200, "{\"Result\":\"OK\"}");
		IllegalStateException noId = Assert.expectThrows(IllegalStateException.class,
				() -> ZapSecurityUtils.startPassiveScan(zap, "http://app/login"));
		Assert.assertTrue(noId.getMessage().contains("scan id"), noId.getMessage());
	}

	@Test
	public void runPassiveScanWaitsForTheSpiderAndThenForTheAnalysisQueue() {
		on("/JSON/pscan/action/enableAllScanners/", 200, "{\"Result\":\"OK\"}");
		on("/JSON/spider/action/scan/", 200, "{\"scan\":\"3\"}");
		on("/JSON/spider/view/status/", 200, "{\"status\":\"20\"}", "{\"status\":\"70\"}", "{\"status\":\"100\"}");
		on("/JSON/pscan/view/recordsToScan/", 200, "{\"recordsToScan\":\"12\"}", "{\"recordsToScan\":\"0\"}");

		ZapSecurityUtils.runPassiveScan(zap, "http://app/login", 60);

		Assert.assertEquals(hits.get("/JSON/spider/view/status/").get(), 3, "polled until the crawl reported 100");
		Assert.assertTrue(hits.get("/JSON/pscan/view/recordsToScan/").get() >= 3,
				"queue read as 12, then 0, then 0 again - empty has to hold on two polls in a row");
	}

	@Test
	public void waitForSpiderTimesOutInsteadOfReturningEarly() {
		on("/JSON/spider/view/status/", 200, "{\"status\":\"40\"}");
		IllegalStateException timeout = Assert.expectThrows(IllegalStateException.class,
				() -> ZapSecurityUtils.waitForSpiderComplete(zap, "3", 0, 10));
		Assert.assertTrue(timeout.getMessage().contains("did not complete"), timeout.getMessage());
	}

	@Test
	public void waitForPassiveScanTimesOutWhenTheQueueNeverDrains() {
		on("/JSON/pscan/view/recordsToScan/", 200, "{\"recordsToScan\":\"5\"}");
		IllegalStateException timeout = Assert.expectThrows(IllegalStateException.class,
				() -> ZapSecurityUtils.waitForPassiveScanComplete(zap, 0, 10));
		Assert.assertTrue(timeout.getMessage().contains("5 message(s) still queued"), timeout.getMessage());
	}

	// -------------------------------------------------------------------------
	// assertNoCriticalAlerts: the false greens
	// -------------------------------------------------------------------------

	@Test
	public void passesWhenTrafficWasRecordedAndThereAreNoHighRiskAlerts() {
		queueEmpty();
		on("/JSON/core/view/numberOfMessages/", 200, "{\"numberOfMessages\":\"42\"}");
		on("/JSON/alert/view/alerts/", 200, alerts(alert("Cookie without Secure flag", "Medium"), alert("Server leaks version", "Low")));

		ZapSecurityUtils.assertNoCriticalAlerts(zap, null, 5, 10);
	}

	@Test
	public void failsAndNamesTheAlertWhenThereIsAHighRiskAlert() {
		queueEmpty();
		on("/JSON/core/view/numberOfMessages/", 200, "{\"numberOfMessages\":\"42\"}");
		on("/JSON/alert/view/alerts/", 200, alerts(
				alert("Cross Site Scripting (Reflected)", "High"),
				alert("Cross Site Scripting (Reflected)", "High"),
				alert("Cookie without Secure flag", "Medium")));

		AssertionError failure = Assert.expectThrows(AssertionError.class,
				() -> ZapSecurityUtils.assertNoCriticalAlerts(zap, null, 5, 10));
		Assert.assertTrue(failure.getMessage().contains("1 High-risk"), "distinct names: " + failure.getMessage());
		Assert.assertTrue(failure.getMessage().contains("Cross Site Scripting (Reflected)"), failure.getMessage());
	}

	@Test
	public void doesNotPassWhenZapHasRecordedNoTraffic() {
		queueEmpty();
		on("/JSON/core/view/numberOfMessages/", 200, "{\"numberOfMessages\":\"0\"}");
		on("/JSON/alert/view/alerts/", 200, alerts());

		AssertionError failure = Assert.expectThrows(AssertionError.class,
				() -> ZapSecurityUtils.assertNoCriticalAlerts(zap, "http://app/login", 5, 10));
		Assert.assertTrue(failure.getMessage().contains("no traffic"), failure.getMessage());
		Assert.assertTrue(failure.getMessage().contains("http://app/login"), failure.getMessage());
		Assert.assertNull(hits.get("/JSON/alert/view/alerts/"), "an empty alert list must not even be consulted");
	}

	@Test
	public void doesNotReadAlertsUntilThePassiveQueueHasDrained() {
		on("/JSON/pscan/view/recordsToScan/", 200, "{\"recordsToScan\":\"30\"}", "{\"recordsToScan\":\"9\"}", "{\"recordsToScan\":\"0\"}");
		on("/JSON/core/view/numberOfMessages/", 200, "{\"numberOfMessages\":\"42\"}");
		on("/JSON/alert/view/alerts/", 200, alerts());

		ZapSecurityUtils.assertNoCriticalAlerts(zap, null, 10, 10);

		Assert.assertTrue(hits.get("/JSON/pscan/view/recordsToScan/").get() >= 4,
				"it polled 30, 9, 0, 0 before looking at the alerts");
	}

	@Test
	public void failsInsteadOfPassingWhenTheQueueNeverDrains() {
		on("/JSON/pscan/view/recordsToScan/", 200, "{\"recordsToScan\":\"30\"}");
		Assert.expectThrows(IllegalStateException.class, () -> ZapSecurityUtils.assertNoCriticalAlerts(zap, null, 0, 10));
		Assert.assertNull(hits.get("/JSON/alert/view/alerts/"));
	}

	@Test
	public void anErrorReplyIsNeverReadAsNoAlerts() {
		queueEmpty();
		on("/JSON/core/view/numberOfMessages/", 200, "{\"numberOfMessages\":\"42\"}");

		on("/JSON/alert/view/alerts/", 500, "{\"message\":\"internal error\"}");
		IllegalStateException serverError = Assert.expectThrows(IllegalStateException.class,
				() -> ZapSecurityUtils.assertNoCriticalAlerts(zap, null, 5, 10));
		Assert.assertTrue(serverError.getMessage().contains("HTTP 500"), serverError.getMessage());

		on("/JSON/alert/view/alerts/", 200, "{\"code\":\"bad_view\",\"message\":\"unknown view\"}");
		IllegalStateException errorBody = Assert.expectThrows(IllegalStateException.class,
				() -> ZapSecurityUtils.assertNoCriticalAlerts(zap, null, 5, 10));
		Assert.assertTrue(errorBody.getMessage().contains("reported an error"), errorBody.getMessage());

		on("/JSON/alert/view/alerts/", 200, "{\"somethingElse\":[]}");
		IllegalStateException wrongShape = Assert.expectThrows(IllegalStateException.class,
				() -> ZapSecurityUtils.assertNoCriticalAlerts(zap, null, 5, 10));
		Assert.assertTrue(wrongShape.getMessage().contains("no 'alerts' list"), wrongShape.getMessage());

		on("/JSON/alert/view/alerts/", 200, "this is not json");
		Assert.expectThrows(IllegalStateException.class, () -> ZapSecurityUtils.assertNoCriticalAlerts(zap, null, 5, 10));
	}

	@Test
	public void alertsAreScopedToTheTargetWhenOneIsGiven() {
		queueEmpty();
		on("/JSON/core/view/numberOfMessages/", 200, "{\"numberOfMessages\":\"3\"}");
		on("/JSON/alert/view/alerts/", 200, alerts());

		ZapSecurityUtils.assertNoCriticalAlerts(zap, "http://app/login", 5, 10);

		Assert.assertTrue(lastQuery.get("/JSON/alert/view/alerts/").contains("baseurl="), lastQuery.toString());
		Assert.assertTrue(lastQuery.get("/JSON/core/view/numberOfMessages/").contains("baseurl="), lastQuery.toString());
	}

	// -------------------------------------------------------------------------
	// alert grouping
	// -------------------------------------------------------------------------

	@Test
	public void alertsAreGroupedByRiskWithDistinctNamesAndHighestFirst() {
		on("/JSON/alert/view/alerts/", 200, alerts(
				alert("Server leaks version", "Low"),
				alert("SQL Injection", "High"),
				alert("SQL Injection", "High"),
				alert("Missing header", "Medium"),
				"{\"name\":\"Info only\",\"riskcode\":\"0\"}"));

		Map<String, List<String>> byRisk = ZapSecurityUtils.getAlertsByRisk(zap);

		Assert.assertEquals(List.copyOf(byRisk.keySet()), List.of("High", "Medium", "Low", "Informational"));
		Assert.assertEquals(byRisk.get("High"), List.of("SQL Injection"));
		Assert.assertEquals(byRisk.get("Informational"), List.of("Info only"), "falls back to riskcode and name when risk/alert are absent");
	}

	// -------------------------------------------------------------------------
	// active scan and report
	// -------------------------------------------------------------------------

	@Test
	public void activeScanNeedsAScanIdAndWaitsForTheStatus() {
		on("/JSON/ascan/action/scan/", 200, "{\"Result\":\"OK\"}");
		Assert.expectThrows(IllegalStateException.class, () -> ZapSecurityUtils.startActiveScan(zap, "http://app"));

		on("/JSON/ascan/action/scan/", 200, "{\"scan\":\"11\"}");
		Assert.assertEquals(ZapSecurityUtils.startActiveScan(zap, "http://app"), "11");

		on("/JSON/ascan/view/status/", 400, "{\"code\":\"does_not_exist\",\"message\":\"Scan not found\"}");
		Assert.expectThrows(IllegalStateException.class, () -> ZapSecurityUtils.waitForActiveScanComplete(zap, "99", 5));
	}

	@Test
	public void htmlReportIsWrittenAndFailuresAreNotSwallowed() throws IOException {
		Path dir = Files.createTempDirectory("zap-report");
		try {
			Path target = dir.resolve("nested").resolve("report.html");

			on("/OTHER/core/other/htmlreport/", 200, "<html>report</html>");
			ZapSecurityUtils.generateHtmlReport(zap, target.toString());
			Assert.assertEquals(Files.readString(target), "<html>report</html>", "missing folders are created");

			on("/OTHER/core/other/htmlreport/", 500, "boom");
			Assert.expectThrows(IllegalStateException.class,
					() -> ZapSecurityUtils.generateHtmlReport(zap, dir.resolve("a.html").toString()));
			Assert.assertFalse(Files.exists(dir.resolve("a.html")), "an error page must not be saved as the report");

			on("/OTHER/core/other/htmlreport/", 200, "");
			Assert.expectThrows(IllegalStateException.class,
					() -> ZapSecurityUtils.generateHtmlReport(zap, dir.resolve("b.html").toString()));
		} finally {
			try (var walk = Files.walk(dir)) {
				walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}

	@Test
	public void theApiKeyIsSentAsAHeaderNotInTheUrl() {
		queueEmpty();
		String previous = System.getProperty("zap.api.key");
		System.setProperty("zap.api.key", "s3cret-key");
		try {
			ZapSecurityUtils.waitForPassiveScanComplete(zap, 5, 1);

			Assert.assertEquals(lastApiKey.get("/JSON/pscan/view/recordsToScan/"), "s3cret-key");
			Assert.assertFalse(lastQuery.get("/JSON/pscan/view/recordsToScan/").contains("s3cret-key"),
					"the key must not appear in the URL, where it would end up in logs");
		} finally {
			if (previous == null) {
				System.clearProperty("zap.api.key");
			} else {
				System.setProperty("zap.api.key", previous);
			}
		}
	}

	@Test
	public void withoutAKeyNoKeyHeaderIsSent() {
		queueEmpty();
		String previous = System.getProperty("zap.api.key");
		System.clearProperty("zap.api.key");
		try {
			ZapSecurityUtils.waitForPassiveScanComplete(zap, 5, 1);

			Assert.assertEquals(lastApiKey.get("/JSON/pscan/view/recordsToScan/"), "null");
		} finally {
			if (previous != null) {
				System.setProperty("zap.api.key", previous);
			}
		}
	}

	@Test
	public void verifyReachableReturnsTheVersionAndNamesAnUnreachableZap() {
		on("/JSON/core/view/version/", 200, "{\"version\":\"2.15.0\"}");
		Assert.assertEquals(ZapSecurityUtils.verifyReachable(zap), "2.15.0");

		server.stop(0);
		IllegalStateException e = Assert.expectThrows(IllegalStateException.class, () -> ZapSecurityUtils.verifyReachable(zap));
		Assert.assertTrue(e.getMessage().contains(zap), e.getMessage());
	}

	@Test
	public void verifyReachableRejectsAnErrorReply() {
		on("/JSON/core/view/version/", 403, "{\"code\":\"bad_api_key\",\"message\":\"Bad API key\"}");
		IllegalStateException e = Assert.expectThrows(IllegalStateException.class, () -> ZapSecurityUtils.verifyReachable(zap));
		Assert.assertTrue(e.getMessage().contains("403"), e.getMessage());
	}
}
