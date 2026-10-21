package com.framework.selenium.api.actions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.selenium.api.actions.ApiCaptureActions.CapturedApi;
import com.framework.utils.Reporter;

/**
 * Browser-free coverage for {@link ApiCaptureActions}: the cURL rendering,
 * request filtering, naming and file layout are all
 * static and testable without a DevTools session. The live CDP attach/detach
 * against a real Chrome is exercised by any UI suite with
 * {@code autoFrameX.api.capture.enabled} switched on by the project, so it is
 * deliberately not duplicated here.
 */
public class ApiCaptureActionsTest {

	// -------------------------------------------------------------------------
	// cURL rendering
	// -------------------------------------------------------------------------

	@Test
	public void buildCurlRendersMethodUrlHeadersAndBodyWithContinuations() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put(":authority", "api.example.com"); // HTTP/2 pseudo-header - must be dropped
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "*/*");

		String curl = ApiCaptureActions.buildCurl("POST", "https://api.example.com/v1/login",
				headers, "{\"user\":\"demo\"}", false);

		Assert.assertEquals(curl,
				"curl --location --request POST 'https://api.example.com/v1/login' \\\n"
				+ "--header 'Content-Type: application/json' \\\n"
				+ "--header 'Accept: */*' \\\n"
				+ "--data-raw '{\"user\":\"demo\"}'");
		Assert.assertFalse(curl.contains(":authority"), "HTTP/2 pseudo-headers must not be emitted");
		Assert.assertFalse(curl.endsWith("\\"), "No dangling line continuation");
	}

	@Test
	public void buildCurlWithoutHeadersOrBodyIsASingleLine() {
		String curl = ApiCaptureActions.buildCurl("GET", "https://example.com/", null, null, false);
		Assert.assertEquals(curl, "curl --location --request GET 'https://example.com/'");
	}

	@Test
	public void buildCurlEscapesSingleQuotesForTheShell() {
		String curl = ApiCaptureActions.buildCurl("POST", "https://x.io/q?name=O'Brien",
				Map.of("X-Note", "it's"), "{\"t\":\"don't\"}", false);
		Assert.assertTrue(curl.contains("'https://x.io/q?name=O'\\''Brien'"), curl);
		Assert.assertTrue(curl.contains("--header 'X-Note: it'\\''s'"), curl);
		Assert.assertTrue(curl.contains("--data-raw '{\"t\":\"don'\\''t\"}'"), curl);
	}

	@Test
	public void buildCurlMasksSensitiveHeadersAndBodyFieldsOnlyWhenAsked() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer abc.def.ghi");
		headers.put("Cookie", "session=xyz");
		headers.put("Accept", "application/json");
		String body = "{\"username\":\"demo\",\"password\":\"s3cret\"}";

		String raw = ApiCaptureActions.buildCurl("POST", "https://x.io/login", headers, body, false);
		Assert.assertTrue(raw.contains("Bearer abc.def.ghi"), "unmasked by default - files are for replay");
		Assert.assertTrue(raw.contains("s3cret"));

		String masked = ApiCaptureActions.buildCurl("POST", "https://x.io/login", headers, body, true);
		Assert.assertTrue(masked.contains("--header 'Authorization: ***'"), masked);
		Assert.assertTrue(masked.contains("--header 'Cookie: ***'"), masked);
		Assert.assertTrue(masked.contains("--header 'Accept: application/json'"), "non-secret headers untouched");
		Assert.assertFalse(masked.contains("s3cret"), "password field in the body must be masked: " + masked);
	}

	// -------------------------------------------------------------------------
	// Filtering
	// -------------------------------------------------------------------------

	@Test
	public void isApiRequestKeepsXhrFetchAndDocuments() {
		Assert.assertTrue(ApiCaptureActions.isApiRequest("https://x.io/api/items", "XHR"));
		Assert.assertTrue(ApiCaptureActions.isApiRequest("https://x.io/api/items", "Fetch"));
		Assert.assertTrue(ApiCaptureActions.isApiRequest("https://x.io/", "Document"));
		Assert.assertTrue(ApiCaptureActions.isApiRequest("wss://x.io/socket", "WebSocket"));
		Assert.assertTrue(ApiCaptureActions.isApiRequest("https://x.io/graphql", null), "untyped non-asset is kept");
	}

	@Test
	public void isApiRequestDropsByResourceTypeEvenWithoutAnExtension() {
		Assert.assertFalse(ApiCaptureActions.isApiRequest("https://fonts.googleapis.com/css?family=Roboto", "Stylesheet"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("https://x.io/img/123", "Image"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("https://x.io/bundle", "Script"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("https://x.io/api/items", "Preflight"), "OPTIONS preflights are noise");
	}

	@Test
	public void isApiRequestDropsByExtensionWhenTypeIsMissingOrOther() {
		Assert.assertFalse(ApiCaptureActions.isApiRequest("https://x.io/app.js", null));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("https://x.io/style.CSS?v=2", "Other"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("https://x.io/logo.svg#icon", "Other"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("https://x.io/font.woff2", "Other"));
	}

	@Test
	public void isApiRequestDropsBrowserInternalAndInlineUrls() {
		Assert.assertFalse(ApiCaptureActions.isApiRequest("data:image/png;base64,AAAA", "Image"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("blob:https://x.io/uuid", "Other"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("about:blank", "Document"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("chrome-extension://abc/page.html", "Document"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest("", "XHR"));
		Assert.assertFalse(ApiCaptureActions.isApiRequest(null, "XHR"));
	}

	// -------------------------------------------------------------------------
	// Naming
	// -------------------------------------------------------------------------

	@Test
	public void endpointNameUsesLastPathSegmentOrHost() {
		Assert.assertEquals(ApiCaptureActions.endpointName("https://x.io/api/v2/incident?limit=5"), "incident");
		Assert.assertEquals(ApiCaptureActions.endpointName("https://x.io/api/v2/incident/"), "incident", "trailing slash ignored");
		Assert.assertEquals(ApiCaptureActions.endpointName("https://x.io/"), "x_io");
		Assert.assertEquals(ApiCaptureActions.endpointName("https://x.io"), "x_io");
		Assert.assertEquals(ApiCaptureActions.endpointName("https://x.io/a%20b/c:d"), "c_d", "unsafe characters replaced");
		Assert.assertEquals(ApiCaptureActions.endpointName("not a url"), "unknown_endpoint");
		Assert.assertEquals(ApiCaptureActions.endpointName("https://x.io/delay/1"), "delay_1", "a bare id keeps its parent segment");
		Assert.assertEquals(ApiCaptureActions.endpointName("https://x.io/api/orders/3f2a9c1e-0b7d-4e55-9a31-5c2d8e7f4b10"),
				"orders_3f2a9c1e-0b7d-4e55-9a31-5c2d8e7f4b10");
		Assert.assertEquals(ApiCaptureActions.endpointName("https://x.io/42"), "42", "nothing to prepend");
	}

	@Test
	public void folderNameCombinesMethodDataKeyAndZeroPaddedInvocation() {
		Assert.assertEquals(ApiCaptureActions.folderNameFor("verifyLogin", "ACC001", 1), "verifyLogin_ACC001_01");
		Assert.assertEquals(ApiCaptureActions.folderNameFor("verifyLogin", "user@corp.com", 12), "verifyLogin_user_corp_com_12");
		Assert.assertEquals(ApiCaptureActions.folderNameFor("verifyLogin", "", 3), "verifyLogin_nodata_03");
		Assert.assertEquals(ApiCaptureActions.folderNameFor(null, "targeted", 7), "test_targeted_07");
	}

	@Test
	public void invocationCounterIsPerTestMethodAndMonotonic() {
		String testCase = "ApiCaptureActionsTest-" + System.nanoTime();
		Assert.assertEquals(ApiCaptureActions.nextInvocation(testCase, "a"), 1);
		Assert.assertEquals(ApiCaptureActions.nextInvocation(testCase, "a"), 2);
		Assert.assertEquals(ApiCaptureActions.nextInvocation(testCase, "b"), 1, "a different method starts at 1");
		Assert.assertEquals(ApiCaptureActions.nextInvocation(testCase, "a"), 3);
	}

	@Test
	public void captureRootSitsUnderTheRunReportFolder() {
		Path root = ApiCaptureActions.captureRoot();
		Assert.assertEquals(root.getFileName().toString(), ApiCaptureActions.CAPTURE_DIR);
		Assert.assertTrue(root.toString().replace('\\', '/').contains("reports/"),
				"capture folder must live inside reports/: " + root);
	}

	// -------------------------------------------------------------------------
	// Preflight filtering and failure reporting
	// -------------------------------------------------------------------------

	@Test
	public void isPreflightRecognisesCorsOptionsRequestsOnly() {
		Map<String, String> preflight = new LinkedHashMap<>();
		preflight.put("Access-Control-Request-Method", "POST");
		preflight.put("Origin", "https://app.example.com");
		Assert.assertTrue(ApiCaptureActions.isPreflight("OPTIONS", preflight));
		Assert.assertTrue(ApiCaptureActions.isPreflight("options", Map.of("access-control-request-method", "PUT")),
				"header name match is case-insensitive");

		Assert.assertFalse(ApiCaptureActions.isPreflight("OPTIONS", Map.of("Accept", "*/*")),
				"a hand-written OPTIONS call without the CORS header is a real API call");
		Assert.assertFalse(ApiCaptureActions.isPreflight("POST", preflight));
		Assert.assertFalse(ApiCaptureActions.isPreflight("OPTIONS", null));
	}

	@Test
	public void requestWithoutResponseExplainsWhyInTheCollectedFile() throws IOException {
		Path dir = Files.createTempDirectory("apicapture-failed");
		try {
			CapturedApi aborted = new CapturedApi(1, "r1", "POST", "https://x.io/ae/api/session/authenticate", "XHR",
					Map.of(), "{}", false);
			aborted.setFailure("net::ERR_ABORTED (canceled)");
			ApiCaptureActions.writeCaptureFiles(dir.resolve("t"), List.of(aborted), false, "label");

			String collected = Files.readString(dir.resolve("t").resolve(ApiCaptureActions.COLLECTED_FILE));
			Assert.assertTrue(collected.contains("### 001 | POST | - | XHR | https://x.io/ae/api/session/authenticate"), collected);
			Assert.assertTrue(collected.contains("# no response: net::ERR_ABORTED (canceled)"), collected);
		} finally {
			deleteRecursively(dir);
		}
	}

	// -------------------------------------------------------------------------
	// File layout
	// -------------------------------------------------------------------------

	@Test
	public void writeCaptureFilesCreatesOneFilePerRequestPlusTheCollectedFile() throws IOException {
		Path dir = Files.createTempDirectory("apicapture-test");
		try {
			CapturedApi first = new CapturedApi(1, "r1", "GET", "https://x.io/api/items?page=1", "XHR",
					Map.of("Accept", "application/json"), null, false);
			first.setStatus(200);
			CapturedApi second = new CapturedApi(2, "r2", "POST", "https://x.io/api/items", "Fetch",
					Map.of("Content-Type", "application/json"), "{\"name\":\"n\"}", false);
			second.setStatus(201);
			CapturedApi third = new CapturedApi(3, "r3", "POST", "https://x.io/upload", "XHR",
					Map.of(), null, true); // body could not be read

			Path target = dir.resolve("TC001").resolve("verifyItems_ACC001_01");
			ApiCaptureActions.writeCaptureFiles(target, List.of(first, second, third), false,
					"TC001 / verifyItems [data: ACC001, run 1]");

			Assert.assertTrue(Files.isRegularFile(target.resolve("001_GET_items.txt")));
			Assert.assertTrue(Files.isRegularFile(target.resolve("002_POST_items.txt")));
			Assert.assertTrue(Files.isRegularFile(target.resolve("003_POST_upload.txt")));
			Assert.assertEquals(Files.readString(target.resolve("002_POST_items.txt")), second.toCurl(false),
					"per-request file is exactly the cURL, nothing else (Postman raw import)");

			String collected = Files.readString(target.resolve(ApiCaptureActions.COLLECTED_FILE));
			Assert.assertTrue(collected.startsWith("# API capture - TC001 / verifyItems [data: ACC001, run 1]"), collected);
			Assert.assertTrue(collected.contains("Requests: 3"), collected);
			Assert.assertTrue(collected.contains("### 001 | GET | 200 | XHR | https://x.io/api/items?page=1"), collected);
			Assert.assertTrue(collected.contains("### 002 | POST | 201 | Fetch | https://x.io/api/items"), collected);
			Assert.assertTrue(collected.contains("### 003 | POST | - | XHR | https://x.io/upload"), "missing status renders as -");
			Assert.assertTrue(collected.contains("request body could not be read"), "omitted body is flagged");
			Assert.assertTrue(collected.indexOf(first.toCurl(false)) < collected.indexOf(second.toCurl(false)),
					"collected file keeps send order");
		} finally {
			deleteRecursively(dir);
		}
	}

	@Test
	public void writeCaptureFilesWithNoRequestsStillLeavesTheFolderAndCollectedFile() throws IOException {
		Path dir = Files.createTempDirectory("apicapture-empty");
		try {
			Path target = dir.resolve("TC002").resolve("noCalls_targeted_01");
			ApiCaptureActions.writeCaptureFiles(target, List.of(), true, "TC002 / noCalls");

			try (Stream<Path> files = Files.list(target)) {
				Assert.assertEquals(files.count(), 1, "only the collected file");
			}
			String collected = Files.readString(target.resolve(ApiCaptureActions.COLLECTED_FILE));
			Assert.assertTrue(collected.contains("no API requests were captured"), collected);
			Assert.assertTrue(collected.contains("secrets masked"), collected);
		} finally {
			deleteRecursively(dir);
		}
	}

	// -------------------------------------------------------------------------
	// Lifecycle guards (no browser)
	// -------------------------------------------------------------------------

	@Test
	public void startWithoutADriverReturnsFalseAndStopIsANoOp() {
		Reporter reporter = new Reporter() {
			@Override
			public long takeSnap() {
				return 0L;
			}
		};
		ApiCaptureActions capture = new ApiCaptureActions(() -> null, reporter);

		Assert.assertFalse(capture.start("TC", "m", "targeted", false), "no driver -> nothing to attach to");
		Assert.assertFalse(capture.isActive());
		Assert.assertNull(capture.stop(), "stop() without a running capture must be a harmless no-op");

		ApiCaptureActions throwing = new ApiCaptureActions(() -> {
			throw new IllegalStateException("No WebDriver available");
		}, reporter);
		Assert.assertFalse(throwing.start("TC", "m", "targeted", false), "a throwing supplier is handled, not propagated");
	}

	@Test
	public void saveSnapshotWithoutARunningCaptureIsANoOp() {
		Reporter reporter = new Reporter() {
			@Override
			public long takeSnap() {
				return 0L;
			}
		};
		ApiCaptureActions capture = new ApiCaptureActions(() -> null, reporter);
		Assert.assertNull(capture.saveSnapshot("GPN_loaded"), "nothing is recording, so nothing to write");
		Assert.assertNull(capture.snapshotSince("x", 0, 10, 10), "same for the explicit-position variant");
	}

	// -------------------------------------------------------------------------
	// Response details
	// -------------------------------------------------------------------------

	@Test
	public void responseTextHasStatusLineHeadersAndBody() {
		CapturedApi api = new CapturedApi(1, "r", "POST", "https://x.io/api/save", "XHR", Map.of(), "{}", false);
		api.setStatus(201);
		api.setStatusText("Created");
		LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Set-Cookie", "sid=abc123");
		api.setResponseHeaders(headers);
		api.setResponseBody("{\"id\":7,\"token\":\"t0k3n\",\"password\":\"s3cret\"}");

		String plain = ApiCaptureActions.buildResponseText(api, false);
		Assert.assertTrue(plain.startsWith("HTTP 201 Created\nContent-Type: application/json\nSet-Cookie: sid=abc123\n\n{"), plain);
		Assert.assertTrue(plain.contains("s3cret"), "unmasked by default");

		String masked = ApiCaptureActions.buildResponseText(api, true);
		Assert.assertTrue(masked.contains("Set-Cookie: ***"), masked);
		Assert.assertTrue(masked.contains("Content-Type: application/json"), "non-secret headers untouched");
		Assert.assertFalse(masked.contains("s3cret"), "credential-looking body field masked: " + masked);
	}

	@Test
	public void responseTextIsNullWhenNothingIsKnownAndExplainsMissingBodies() {
		CapturedApi unknown = new CapturedApi(1, "r", "GET", "https://x.io/a", "XHR", Map.of(), null, false);
		Assert.assertNull(ApiCaptureActions.buildResponseText(unknown, false));

		CapturedApi binary = new CapturedApi(2, "r2", "GET", "https://x.io/pic", "Fetch", Map.of(), null, false);
		binary.setStatus(200);
		binary.setResponseNote("binary response body not stored (10 base64 characters)");
		String text = ApiCaptureActions.buildResponseText(binary, false);
		Assert.assertEquals(text, "HTTP 200\n# binary response body not stored (10 base64 characters)");
	}

	@Test
	public void onlyXhrAndFetchAnswersWithContentAreReadAsBodies() {
		Assert.assertTrue(ApiCaptureActions.wantsBody("XHR", 200, "POST"));
		Assert.assertTrue(ApiCaptureActions.wantsBody("Fetch", 500, "GET"), "error bodies are the interesting ones");
		Assert.assertFalse(ApiCaptureActions.wantsBody("Document", 200, "GET"));
		Assert.assertFalse(ApiCaptureActions.wantsBody("Image", 200, "GET"));
		Assert.assertFalse(ApiCaptureActions.wantsBody("XHR", 204, "POST"), "no content");
		Assert.assertFalse(ApiCaptureActions.wantsBody("XHR", 304, "GET"), "not modified");
		Assert.assertFalse(ApiCaptureActions.wantsBody("XHR", 200, "HEAD"));
		Assert.assertFalse(ApiCaptureActions.wantsBody("XHR", null, "GET"), "no response yet");
		Assert.assertFalse(ApiCaptureActions.wantsBody(null, 200, "GET"));
	}

	@Test
	public void responseFileIsWrittenNextToTheCurlAndRepeatedInTheCollectedFile() throws IOException {
		Path dir = Files.createTempDirectory("apicapture-response");
		try {
			CapturedApi save = new CapturedApi(1, "r1", "POST", "https://x.io/api/save", "XHR",
					Map.of("Content-Type", "application/json"), "{\"a\":1}", false);
			save.setStatus(200);
			save.setResponseHeaders(Map.of("Content-Type", "application/json"));
			save.setResponseBody("{\"saved\":true}");
			CapturedApi statusOnly = new CapturedApi(2, "r2", "GET", "https://x.io/api/ping", "XHR", Map.of(), null, false);
			statusOnly.setStatus(200);

			Path target = dir.resolve("t");
			ApiCaptureActions.writeCaptureFiles(target, List.of(save, statusOnly), false, "label");

			Assert.assertEquals(Files.readString(target.resolve("001_POST_save.txt")), save.toCurl(false),
					"the cURL file stays exactly the cURL, so Postman can import it");
			String response = Files.readString(target.resolve("001_POST_save_response.txt"));
			Assert.assertEquals(response, "HTTP 200\nContent-Type: application/json\n\n{\"saved\":true}");
			Assert.assertFalse(Files.exists(target.resolve("002_GET_ping_response.txt")),
					"a request with only a status code gets no separate response file");

			String collected = Files.readString(target.resolve(ApiCaptureActions.COLLECTED_FILE));
			Assert.assertTrue(collected.contains(">>> response\nHTTP 200\nContent-Type: application/json\n\n{\"saved\":true}\n<<<"), collected);
			Assert.assertTrue(collected.contains(">>> response\nHTTP 200\n<<<"), "status-only responses still appear inline: " + collected);
		} finally {
			deleteRecursively(dir);
		}
	}

	// -------------------------------------------------------------------------
	// Quiet detection and snapshot naming
	// -------------------------------------------------------------------------

	@Test
	public void pageIsQuietOnlyWhenNothingIsInFlightAndEnoughTimeHasPassed() {
		ApiCaptureActions.ActivityTracker tracker = new ApiCaptureActions.ActivityTracker(0L);
		long ms = 1_000_000L;
		long quiet = 500 * ms;
		long waitStarted = 10_000 * ms;

		Assert.assertFalse(tracker.isQuiet(waitStarted + 100 * ms, waitStarted, quiet),
				"even with nothing in flight, the quiet period is counted from when the wait began, so a call fired just after a click is not missed");
		Assert.assertTrue(tracker.isQuiet(waitStarted + 600 * ms, waitStarted, quiet));

		tracker.started("s|1", waitStarted + 650 * ms);
		Assert.assertFalse(tracker.isQuiet(waitStarted + 5_000 * ms, waitStarted, quiet), "a request is still in flight");
		Assert.assertEquals(tracker.inFlightCount(), 1);

		tracker.finished("s|1", waitStarted + 5_000 * ms);
		Assert.assertFalse(tracker.isQuiet(waitStarted + 5_200 * ms, waitStarted, quiet), "finished only 200 ms ago");
		Assert.assertTrue(tracker.isQuiet(waitStarted + 5_600 * ms, waitStarted, quiet));
	}

	@Test
	public void closingAWindowReleasesItsUnfinishedRequests() {
		ApiCaptureActions.ActivityTracker tracker = new ApiCaptureActions.ActivityTracker(0L);
		tracker.started("popup|1", 1L);
		tracker.started("popup|2", 2L);
		tracker.started("main|1", 3L);
		tracker.forgetSession("popup", 4L);
		Assert.assertEquals(tracker.inFlightCount(), 1, "only the main window's request is still expected to finish");
	}

	@Test
	public void webSocketsAndEventStreamsNeverHoldUpASnapshot() {
		Assert.assertTrue(ApiCaptureActions.isLongLivedType("WebSocket"));
		Assert.assertTrue(ApiCaptureActions.isLongLivedType("EventSource"));
		Assert.assertTrue(ApiCaptureActions.isLongLivedType("Ping"));
		Assert.assertFalse(ApiCaptureActions.isLongLivedType("XHR"));
		Assert.assertFalse(ApiCaptureActions.isLongLivedType(null));
	}

	@Test
	public void snapshotFoldersAreNumberedSoTheySortInTheOrderTheyWereTaken() {
		Assert.assertEquals(ApiCaptureActions.snapshotFolderName(1, "GPN_loaded"), "01_GPN_loaded");
		Assert.assertEquals(ApiCaptureActions.snapshotFolderName(12, "save clicked!"), "12_save_clicked_");
		Assert.assertEquals(ApiCaptureActions.snapshotFolderName(3, null), "03_phase");
	}

	// -------------------------------------------------------------------------
	// helpers
	// -------------------------------------------------------------------------

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// best effort temp cleanup
				}
			});
		}
	}
}
