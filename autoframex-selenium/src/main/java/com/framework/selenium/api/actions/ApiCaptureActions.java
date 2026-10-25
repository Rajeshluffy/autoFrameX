package com.framework.selenium.api.actions;

import com.framework.utils.StepReporter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.devtools.CdpEndpointFinder;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.TextMessage;
import org.openqa.selenium.remote.http.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.utils.EncryptionUtils;
import com.framework.utils.ExtentReportManager;
import com.framework.utils.Reporter;

/**
 * Records every API request the browser sends during one test invocation
 * (Chrome DevTools Protocol, {@code Network} domain) and writes each one as a
 * Postman-importable cURL command into the current run's report folder.
 *
 * <h3>Folder layout</h3>
 * <pre>
 * reports/&lt;run&gt;/apicapture/
 *   &lt;testcaseName&gt;/                          &lt;- {@code @TestMetadata.name} / class simple name
 *     &lt;method&gt;_&lt;accountId&gt;_&lt;NN&gt;/              &lt;- one folder per DataProvider row AND per
 *       001_GET_login.txt                     invocation (NN = how many times this
 *       002_POST_authenticate.txt             test method has run so far in this JVM,
 *       collected_apis.txt                    so retries never overwrite each other)
 *       &lt;NN&gt;_&lt;phase&gt;/                         &lt;- optional, one per snapshot, see {@link #saveSnapshot}
 * </pre>
 * Every {@code NNN_METHOD_endpoint.txt} holds exactly one cURL (Postman ->
 * Import -> Raw text). {@code collected_apis.txt} repeats all of them, in send
 * order, with the response status and resource type in a header line per request.
 *
 * <h3>Every window, every frame</h3>
 * The capture talks to the <em>browser</em> endpoint, not to one page. It turns on
 * {@code Target.setAutoAttach} with {@code waitForDebuggerOnStart}, so a window or
 * cross-origin iframe the application opens (e.g. GPN launched from the AlfaDOCK
 * home page) is attached to <em>before</em> its first request leaves: Chrome holds
 * the new target paused until {@code Network.enable} has been sent and only then
 * resumes it. Nothing the new window does while loading is missed, and no test code
 * has to know the window exists.
 *
 * <h3>What is captured</h3>
 * XHR / fetch / document navigations / WebSockets / EventSource / "Other". Static
 * assets are skipped twice over - by CDP resource type (Stylesheet, Image, Font,
 * Script, ...) and by file extension as a fallback for untyped requests - and so
 * are {@code data:}, {@code blob:} and browser-internal URLs and CORS preflight
 * {@code OPTIONS} calls. A request that never gets a response keeps status
 * {@code -} and, when Chrome says why (e.g. {@code net::ERR_ABORTED}), the reason
 * is written under its header line.
 *
 * <h3>Lifecycle</h3>
 * One instance per test invocation, owned by a {@code ThreadLocal} in
 * {@code ProjectSpecificMethods} (same pattern as {@code VideoRecorder}):
 * {@link #start} in {@code @BeforeMethod} once the driver is bound,
 * {@link #stop} in {@code @AfterMethod} <em>before</em> the driver is returned to
 * the pool. A test can also call {@link #saveSnapshot} at any point (typically right
 * after the page reports it has finished loading) to write what has been captured
 * since the previous snapshot into its own sub-folder.
 *
 * <h3>Why it does not use {@code driver.getDevTools()}</h3>
 * Selenium ships version-pinned CDP packages ({@code selenium-devtools-vNNN}) for
 * only the three Chrome releases current at that Selenium version, and
 * {@code getDevTools()} returns a no-op stub once the installed browser is more
 * than a few majors newer (Selenium 4.33 + Chrome 155). It also follows a single
 * page. This class opens its own WebSocket with Selenium's HTTP client and speaks
 * the long-stable {@code Target}/{@code Network} protocol directly, so it is
 * independent of both the Selenium and the Chrome version.
 *
 * <h3>Browser support</h3>
 * Chromium-based browsers only (Chrome, Edge, Chromium) - Firefox dropped CDP.
 * Anything that fails to attach is logged and the test carries on without
 * capture; this class never fails a test.
 */
public class ApiCaptureActions {

	private static final Logger logger = LoggerFactory.getLogger(ApiCaptureActions.class);

	/** Sub-folder of the run's report folder that holds every capture. */
	public static final String CAPTURE_DIR = "apicapture";

	/** Name of the per-invocation file that lists every captured request. */
	public static final String COLLECTED_FILE = "collected_apis.txt";

	private static final String MASK = "***";
	private static final int MAX_NAME_LENGTH = 60;
	private static final Duration CDP_TIMEOUT = Duration.ofSeconds(5);
	private static final long DRAIN_TIMEOUT_SECONDS = 3;

	/** The page counts as quiet when nothing is in flight and nothing new started for this long. */
	public static final long DEFAULT_QUIET_MILLIS = 500;
	/** A snapshot never waits longer than this for the page to go quiet. */
	public static final long DEFAULT_MAX_WAIT_MILLIS = 15_000;
	/** Safety cap on recorded requests per test, so a chatty page cannot exhaust memory. */
	private static final int MAX_CAPTURED_REQUESTS = 5_000;
	/** Response bodies longer than this are cut (the note in the file says so). */
	private static final int MAX_RESPONSE_CHARS = 500_000;
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
	private static final Json JSON = new Json();

	// Vendor capability keys under which a local chromedriver/msedgedriver reports debuggerAddress.
	private static final List<String> VENDOR_OPTION_KEYS = List.of("goog:chromeOptions", "ms:edgeOptions");

	// CDP ResourceType values (lower-cased) that are never API calls.
	private static final Set<String> SKIPPED_RESOURCE_TYPES = Set.of(
			"stylesheet", "image", "media", "font", "script", "texttrack", "prefetch",
			"manifest", "signedexchange", "ping", "cspviolationreport", "preflight", "fedcm");

	// Extension fallback for requests whose resource type is missing or "Other".
	private static final Set<String> STATIC_EXTENSIONS = Set.of(
			".css", ".js", ".mjs", ".map", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
			".webp", ".bmp", ".woff", ".woff2", ".ttf", ".otf", ".eot", ".mp4", ".webm", ".mp3", ".wav");

	private static final Set<String> SKIPPED_SCHEMES = Set.of(
			"data", "blob", "about", "chrome", "chrome-extension", "devtools", "javascript", "file");

	// Header names (lower-cased) replaced with *** when secret masking is on.
	private static final Set<String> SENSITIVE_HEADERS = Set.of(
			"authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key",
			"x-auth-token", "x-access-token", "x-csrf-token", "x-xsrf-token", "api-key", "apikey", "token");

	// "<testcase>#<method>" -> how many times that test has started in this JVM.
	private static final ConcurrentMap<String, AtomicInteger> INVOCATIONS = new ConcurrentHashMap<>();

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final StepReporter reporter;

	// ---- per-capture state (one instance = one test invocation) ----
	private CdpSocket cdp;
	private ExecutorService worker;
	private Path targetDir;
	private String label;
	private boolean maskSecrets;
	private volatile boolean active;
	private final AtomicInteger sequence = new AtomicInteger();
	private final AtomicInteger lastSnapshotSeq = new AtomicInteger();
	private final AtomicInteger snapshotCount = new AtomicInteger();
	private final ActivityTracker activity = new ActivityTracker();
	private volatile boolean capLogged;
	private final ConcurrentLinkedQueue<CapturedApi> captured = new ConcurrentLinkedQueue<>();
	private final ConcurrentMap<String, CapturedApi> latestByRequestKey = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Boolean> followedSessions = new ConcurrentHashMap<>();
	private final Set<String> followedTargets = ConcurrentHashMap.newKeySet();

	public ApiCaptureActions(Supplier<RemoteWebDriver> driverSupplier, StepReporter reporter) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
	}

	// =========================================================================
	// LIFECYCLE
	// =========================================================================

	/**
	 * Attaches to the current thread's browser and starts recording every window.
	 *
	 * @param testCaseName report-level test case name ({@code @TestMetadata.name})
	 * @param methodName   the {@code @Test} method name
	 * @param dataKey      the DataProvider row key ({@code AccountData.getAccountId()};
	 *                     {@code "targeted"} when the test is not data-driven)
	 * @param maskSecrets  replace Authorization/Cookie-style header values and
	 *                     credential-looking body fields with {@code ***}
	 * @return {@code true} if recording started; {@code false} (never an exception)
	 *         when there is no driver, the browser is not Chromium-based, or
	 *         DevTools could not be reached
	 */
	public boolean start(String testCaseName, String methodName, String dataKey, boolean maskSecrets) {
		if (active) {
			logger.warn("API capture already running for {} - ignoring second start()", label);
			return true;
		}

		RemoteWebDriver driver = currentDriver();
		if (driver == null) {
			logger.warn("API capture skipped - no WebDriver is bound to this thread.");
			return false;
		}
		Capabilities caps = driver.getCapabilities();
		if (!isChromiumBased(caps)) {
			logger.info("API capture skipped - needs a Chromium-based browser, got '{}'.",
					caps == null ? "?" : caps.getBrowserName());
			return false;
		}

		this.maskSecrets = maskSecrets;
		int invocation = nextInvocation(testCaseName, methodName);
		this.targetDir = captureRoot()
				.resolve(sanitise(testCaseName, "UnnamedTest"))
				.resolve(folderNameFor(methodName, dataKey, invocation));
		this.label = testCaseName + " / " + methodName + " [data: " + dataKey + ", run " + invocation + "]";

		try {
			HttpClient.Factory factory = HttpClient.Factory.createDefault();
			URI[] endpoint = resolveBrowserSocket(caps, factory).orElseThrow(() -> new IllegalStateException(
					"browser reports no CDP endpoint (neither se:cdp nor a vendor debuggerAddress)"));

			logger.debug("API capture endpoint: httpBase={} webSocket={}", endpoint[0], endpoint[1]);
			worker = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "apicapture-events");
				t.setDaemon(true);
				return t;
			});
			cdp = new CdpSocket(factory, endpoint[0], endpoint[1], this::onCdpEvent);

			// 1. Every window/iframe opened from now on is attached paused and resumed by onAttached().
			cdp.call(null, "Target.setAutoAttach", Map.of(
					"autoAttach", true, "waitForDebuggerOnStart", true, "flatten", true), CDP_TIMEOUT);

			// 2. Windows that already exist (the one the driver is on, any popup open already).
			Map<String, Object> targets = cdp.call(null, "Target.getTargets", Map.of(), CDP_TIMEOUT);
			Object infos = targets.get("targetInfos");
			if (infos instanceof List) {
				for (Object o : (List<?>) infos) {
					Map<String, Object> info = asMap(o);
					if (info == null || !"page".equals(str(info.get("type")))) {
						continue;
					}
					String targetId = str(info.get("targetId"));
					if (targetId == null || followedTargets.contains(targetId)) {
						continue;
					}
					Map<String, Object> attached = cdp.call(null, "Target.attachToTarget",
							Map.of("targetId", targetId, "flatten", true), CDP_TIMEOUT);
					onAttached(str(attached.get("sessionId")), info, false);
				}
			}

			active = true;
			logger.info("API capture started for {} -> {} ({} {}, following {} window(s))",
					label, targetDir, caps.getBrowserName(), caps.getBrowserVersion(), followedTargets.size());
			return true;
		} catch (Exception e) {
			logger.warn("API capture could not attach to DevTools for {}: {}", label, describe(e));
			releaseTransport();
			return false;
		}
	}

	/**
	 * Stops recording and writes the capture folder. Safe to call when
	 * {@link #start} was never called or returned {@code false} - returns
	 * {@code null} then. Never throws.
	 *
	 * @return the folder written, or {@code null} when nothing was recorded
	 */
	public Path stop() {
		if (!active) {
			return null;
		}
		active = false;

		drainEvents();
		releaseTransport();

		List<CapturedApi> apis = sortedCopy(0);
		try {
			writeCaptureFiles(targetDir, apis, maskSecrets, label);
			reporter.reportStep("API capture: " + apis.size() + " request(s) saved to "
					+ targetDir.normalize(), "INFO", false);
			logger.info("API capture finished for {}: {} request(s) -> {}", label, apis.size(), targetDir);
		} catch (IOException e) {
			logger.warn("API capture for {} could not be written to {}: {}", label, targetDir, e.getMessage());
			reporter.reportStep("API capture could not be written: " + e.getMessage(), "WARNING", false);
		} catch (RuntimeException e) {
			logger.warn("API capture for {} failed while writing files: {}", label, e.toString());
		}
		return targetDir;
	}

	/**
	 * Cuts a labelled bookmark in the recording: waits until the page is quiet
	 * (nothing in flight and nothing new started for {@link #DEFAULT_QUIET_MILLIS}
	 * ms, at most {@link #DEFAULT_MAX_WAIT_MILLIS} ms), then writes every request
	 * captured <em>since the previous snapshot</em> into
	 * {@code <invocation folder>/<NN>_<phase>/} and keeps recording. The invocation
	 * folder written by {@link #stop} still holds the whole timeline.
	 *
	 * @param phase label for the sub-folder, e.g. {@code "GPN_loaded"}
	 * @return the sub-folder written, or {@code null} when no capture is running
	 */
	public Path saveSnapshot(String phase) {
		Snapshot snapshot = snapshotSince(phase, lastSnapshotSeq.get(), DEFAULT_QUIET_MILLIS, DEFAULT_MAX_WAIT_MILLIS);
		return snapshot == null ? null : snapshot.getDir();
	}

	/** Position in the recording right now. Pass it to {@link #snapshotSince} to cut "everything after this point". */
	public int currentSequence() {
		return sequence.get();
	}

	/**
	 * Like {@link #saveSnapshot} but cuts from an explicit position instead of the
	 * previous snapshot, and returns the calls as well as the folder. This is what
	 * {@code SeleniumBase.captureApis} uses to keep only the calls one action caused.
	 *
	 * @param fromSeq       only requests recorded after this position are kept
	 *                      ({@link #currentSequence()} taken before the action)
	 * @param quietMillis   how long the page must stay quiet; also the minimum wait
	 *                      after the action, so a call fired shortly after a click is not missed
	 * @param maxWaitMillis upper bound on the wait
	 * @return the calls and the folder (folder is {@code null} if writing failed),
	 *         or {@code null} when no capture is running
	 */
	public Snapshot snapshotSince(String phase, int fromSeq, long quietMillis, long maxWaitMillis) {
		if (!active) {
			return null;
		}
		awaitQuiet(quietMillis, maxWaitMillis);

		List<CapturedApi> segment = sortedCopy(fromSeq);
		if (!segment.isEmpty()) {
			int to = segment.get(segment.size() - 1).seq;
			lastSnapshotSeq.accumulateAndGet(to, Math::max);
		}

		Path dir = targetDir.resolve(snapshotFolderName(snapshotCount.incrementAndGet(), phase));
		try {
			writeCaptureFiles(dir, segment, maskSecrets, label + " | phase: " + phase);
			reporter.reportStep("API capture '" + phase + "': " + segment.size() + " request(s) saved to "
					+ dir.normalize(), "INFO", false);
			logger.info("API capture snapshot '{}' for {}: {} request(s) -> {}", phase, label, segment.size(), dir);
			return new Snapshot(dir, segment);
		} catch (IOException | RuntimeException e) {
			logger.warn("API capture snapshot '{}' for {} failed: {}", phase, label, e.toString());
			return new Snapshot(null, segment);
		}
	}

	/**
	 * Waits until nothing is in flight and nothing new has started for
	 * {@code quietMillis}, counted from now at the earliest. Gives up after
	 * {@code maxWaitMillis}. WebSockets and event streams never finish, so they do
	 * not count as in flight.
	 *
	 * @return {@code true} if the page went quiet, {@code false} if the wait timed out
	 */
	boolean awaitQuiet(long quietMillis, long maxWaitMillis) {
		long startNanos = System.nanoTime();
		long deadline = startNanos + TimeUnit.MILLISECONDS.toNanos(maxWaitMillis);
		long quietNanos = TimeUnit.MILLISECONDS.toNanos(quietMillis);
		while (true) {
			drainEvents();
			long now = System.nanoTime();
			if (activity.isQuiet(now, startNanos, quietNanos)) {
				return true;
			}
			if (now - deadline >= 0) {
				logger.warn("API capture: page not quiet after {} ms ({} request(s) still in flight) - cutting anyway",
						maxWaitMillis, activity.inFlightCount());
				return false;
			}
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
	}

	/** {@code true} between a successful {@link #start} and {@link #stop}. */
	public boolean isActive() {
		return active;
	}

	/** Folder this capture writes to (known after {@link #start}, even if it failed to attach). */
	public Path getTargetDir() {
		return targetDir;
	}

	// =========================================================================
	// CDP ATTACH
	// =========================================================================

	/**
	 * Browser-level endpoint as {@code {httpClientBase, webSocketUrl}}: the Grid's
	 * {@code se:cdp} endpoint if present (used for both), otherwise the
	 * {@code debuggerAddress} a local chromedriver/msedgedriver reports (the HTTP
	 * client must be built from <em>that</em> address - the JDK WebSocket refuses a
	 * client whose base URI is the {@code ws://} URL itself) plus the
	 * {@code webSocketDebuggerUrl} it resolves to.
	 */
	private static Optional<URI[]> resolveBrowserSocket(Capabilities caps, HttpClient.Factory factory) {
		// se:cdp holds a ready-made ws:// URL (chromedriver sets it for a local browser
		// too, a Grid sets its own proxy URL). Selenium's CdpEndpointFinder.getReportedUri
		// mangles it into "http://ws://...", so read the capability directly.
		Object reported = caps.getCapability("se:cdp");
		if (reported != null && !String.valueOf(reported).isBlank()) {
			try {
				URI webSocket = URI.create(String.valueOf(reported).trim());
				if (webSocket.getAuthority() != null) {
					String scheme = "wss".equalsIgnoreCase(webSocket.getScheme()) || "https".equalsIgnoreCase(webSocket.getScheme())
							? "https" : "http";
					return Optional.of(new URI[] {URI.create(scheme + "://" + webSocket.getAuthority()), webSocket});
				}
			} catch (IllegalArgumentException e) {
				logger.debug("Ignoring malformed se:cdp capability '{}': {}", reported, e.toString());
			}
		}
		for (String vendorKey : VENDOR_OPTION_KEYS) {
			Optional<URI> debuggerAddress = CdpEndpointFinder.getReportedUri(vendorKey, caps);
			if (debuggerAddress.isEmpty()) {
				continue;
			}
			HttpClient client = CdpEndpointFinder.getHttpClient(factory, debuggerAddress.get());
			try {
				Optional<URI> webSocket = CdpEndpointFinder.getCdpEndPoint(client);
				if (webSocket.isPresent()) {
					return Optional.of(new URI[] {debuggerAddress.get(), webSocket.get()});
				}
			} finally {
				client.close();
			}
		}
		return Optional.empty();
	}

	/**
	 * Runs on the socket's reader thread - must never block. Starts recording a new
	 * window/iframe and, if Chrome is holding it paused, lets it run <em>after</em>
	 * the Network domain is on. CDP executes commands of one session in order, so
	 * "enable, then resume" needs no waiting for replies.
	 */
	private void onAttached(String sessionId, Map<String, Object> info, boolean waitingForDebugger) {
		if (sessionId == null) {
			return;
		}
		String type = info == null ? "" : String.valueOf(info.get("type"));
		String targetId = info == null ? null : str(info.get("targetId"));
		boolean tracked = "page".equals(type) || "iframe".equals(type);
		try {
			if (!tracked) {
				return; // workers, service workers ...: resume only (finally)
			}
			if (followedSessions.putIfAbsent(sessionId, Boolean.TRUE) != null) {
				return; // already handled: attachToTarget's reply and its event both land here
			}
			if (targetId != null && !followedTargets.add(targetId)) {
				// A second session for a window we already follow would record every request twice.
				followedSessions.remove(sessionId);
				cdp.post(null, "Target.detachFromTarget", Map.of("sessionId", sessionId));
				return;
			}
			cdp.post(sessionId, "Network.enable", Map.of());
			// Cross-origin iframes live in their own target; pick them up too.
			cdp.post(sessionId, "Target.setAutoAttach", Map.of(
					"autoAttach", true, "waitForDebuggerOnStart", true, "flatten", true));
		} finally {
			if (waitingForDebugger) {
				cdp.post(sessionId, "Runtime.runIfWaitingForDebugger", Map.of());
			}
		}
	}

	// =========================================================================
	// CDP EVENTS
	// =========================================================================

	/** Entry point for every protocol event, on the socket's reader thread. */
	private void onCdpEvent(Map<String, Object> message) {
		String method = str(message.get("method"));
		if (method == null) {
			return;
		}
		Map<String, Object> params = asMap(message.get("params"));
		if (params == null) {
			params = Collections.emptyMap();
		}
		switch (method) {
			case "Target.attachedToTarget":
				onAttached(str(params.get("sessionId")), asMap(params.get("targetInfo")),
						Boolean.TRUE.equals(params.get("waitingForDebugger")));
				return;
			case "Target.detachedFromTarget":
				String detached = str(params.get("sessionId"));
				if (detached != null) {
					followedSessions.remove(detached);
					activity.forgetSession(detached, System.nanoTime());
				}
				return;
			case "Network.requestWillBeSent":
			case "Network.responseReceived":
			case "Network.loadingFailed":
			case "Network.loadingFinished":
				String sessionId = str(message.get("sessionId"));
				if (sessionId == null) {
					return;
				}
				final Map<String, Object> eventParams = params;
				try {
					worker.execute(() -> handleNetworkEvent(method, sessionId, eventParams));
				} catch (RuntimeException e) {
					logger.debug("API capture worker already stopped, dropping {}", method);
				}
				return;
			default:
				// not interested
		}
	}

	/** Runs on the single "apicapture-events" thread, so events of one capture are handled in order. */
	private void handleNetworkEvent(String method, String sessionId, Map<String, Object> params) {
		try {
			switch (method) {
				case "Network.requestWillBeSent":
					onRequestWillBeSent(sessionId, params);
					break;
				case "Network.responseReceived":
					onResponseReceived(sessionId, params);
					break;
				case "Network.loadingFailed":
					onLoadingFailed(sessionId, params);
					break;
				case "Network.loadingFinished":
					onLoadingFinished(sessionId, params);
					break;
				default:
					break;
			}
		} catch (RuntimeException e) {
			logger.debug("Ignoring malformed {} event: {}", method, e.toString());
		}
	}

	private void onRequestWillBeSent(String sessionId, Map<String, Object> params) {
		Map<String, Object> request = asMap(params.get("request"));
		if (request == null) {
			return;
		}
		String requestId = str(params.get("requestId"));
		String key = sessionId + "|" + requestId;
		if (!isLongLivedType(str(params.get("type")))) {
			activity.started(key, System.nanoTime());
		}

		// A redirect hop re-uses the requestId; its status only ever shows up here.
		Map<String, Object> redirect = asMap(params.get("redirectResponse"));
		if (redirect != null && requestId != null) {
			CapturedApi previousHop = latestByRequestKey.get(key);
			if (previousHop != null) {
				previousHop.status = toInt(redirect.get("status"));
				previousHop.statusText = str(redirect.get("statusText"));
				previousHop.responseHeaders = toStringMap(asMap(redirect.get("headers")));
			}
		}

		String url = str(request.get("url"));
		String resourceType = str(params.get("type"));
		if (!isApiRequest(url, resourceType)) {
			return;
		}

		String method = str(request.get("method"));
		method = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
		Map<String, String> headers = toStringMap(asMap(request.get("headers")));
		if (isPreflight(method, headers)) {
			return;
		}

		String postData = str(request.get("postData"));
		boolean bodyOmitted = false;
		if (postData == null && Boolean.TRUE.equals(request.get("hasPostData"))) {
			// Chrome leaves large bodies out of the event; ask for them while the
			// request is still in flight. Blocking is safe here: replies are
			// completed on the socket thread, this is the worker thread.
			postData = fetchPostData(sessionId, requestId);
			bodyOmitted = postData == null;
		}

		if (sequence.get() >= MAX_CAPTURED_REQUESTS) {
			if (!capLogged) {
				capLogged = true;
				logger.warn("API capture for {} reached {} requests - further requests are not recorded",
						label, MAX_CAPTURED_REQUESTS);
			}
			return;
		}

		CapturedApi api = new CapturedApi(sequence.incrementAndGet(), requestId, method, url,
				resourceType, headers, postData, bodyOmitted);
		captured.add(api);
		if (requestId != null) {
			latestByRequestKey.put(key, api);
		}
	}

	private void onResponseReceived(String sessionId, Map<String, Object> params) {
		String requestId = str(params.get("requestId"));
		Map<String, Object> response = asMap(params.get("response"));
		if (requestId == null || response == null) {
			return;
		}
		CapturedApi api = latestByRequestKey.get(sessionId + "|" + requestId);
		if (api != null) {
			api.status = toInt(response.get("status"));
			api.statusText = str(response.get("statusText"));
			api.mimeType = str(response.get("mimeType"));
			api.responseHeaders = toStringMap(asMap(response.get("headers")));
		}
	}

	private void onLoadingFailed(String sessionId, Map<String, Object> params) {
		String requestId = str(params.get("requestId"));
		if (requestId == null) {
			return;
		}
		activity.finished(sessionId + "|" + requestId, System.nanoTime());
		CapturedApi api = latestByRequestKey.get(sessionId + "|" + requestId);
		if (api != null && api.status == null) {
			String reason = str(params.get("errorText"));
			api.failure = reason == null ? "failed" : reason
					+ (Boolean.TRUE.equals(params.get("canceled")) ? " (canceled)" : "");
		}
	}

	/**
	 * The response is complete: read its body (XHR/fetch only) before the browser
	 * discards it, then mark the request as no longer in flight. Doing both here, in
	 * order, is what lets a snapshot's quiet-wait also cover the body fetch.
	 */
	private void onLoadingFinished(String sessionId, Map<String, Object> params) {
		String requestId = str(params.get("requestId"));
		if (requestId == null) {
			return;
		}
		String key = sessionId + "|" + requestId;
		try {
			CapturedApi api = latestByRequestKey.get(key);
			if (api != null && wantsBody(api.resourceType, api.status, api.method)) {
				fetchResponseBody(sessionId, requestId, api);
			}
		} finally {
			activity.finished(key, System.nanoTime());
		}
	}

	private void fetchResponseBody(String sessionId, String requestId, CapturedApi api) {
		if (cdp == null) {
			return;
		}
		try {
			Map<String, Object> reply = cdp.call(sessionId, "Network.getResponseBody",
					Map.of("requestId", requestId), CDP_TIMEOUT);
			String body = str(reply.get("body"));
			if (Boolean.TRUE.equals(reply.get("base64Encoded"))) {
				api.responseNote = "binary response body not stored ("
						+ (body == null ? 0 : body.length()) + " base64 characters)";
			} else if (body != null && body.length() > MAX_RESPONSE_CHARS) {
				api.responseBody = body.substring(0, MAX_RESPONSE_CHARS);
				api.responseNote = "response body truncated: first " + MAX_RESPONSE_CHARS
						+ " of " + body.length() + " characters stored";
			} else {
				api.responseBody = body;
			}
		} catch (RuntimeException e) {
			// Redirects, cache hits and responses dropped by a navigation have no body to read.
			api.responseNote = "response body unavailable";
			logger.debug("Response body unavailable for request {}: {}", requestId, e.toString());
		}
	}

	private String fetchPostData(String sessionId, String requestId) {
		if (requestId == null || cdp == null) {
			return null;
		}
		try {
			Map<String, Object> reply = cdp.call(sessionId, "Network.getRequestPostData",
					Map.of("requestId", requestId), CDP_TIMEOUT);
			return str(reply.get("postData"));
		} catch (RuntimeException e) {
			// Binary/multipart bodies and already-released requests end up here.
			logger.debug("Post data unavailable for request {}: {}", requestId, e.toString());
			return null;
		}
	}

	/** Waits until every event already received has been processed. */
	private void drainEvents() {
		ExecutorService w = worker;
		if (w == null) {
			return;
		}
		try {
			w.submit(() -> { }).get(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			logger.debug("Draining API capture events failed: {}", e.toString());
		}
	}

	private void releaseTransport() {
		if (cdp != null) {
			cdp.close(); // closing the socket detaches every session, which also ends auto-attach
			cdp = null;
		}
		if (worker != null) {
			worker.shutdown();
			worker = null;
		}
	}

	private List<CapturedApi> sortedCopy(int afterSeq) {
		List<CapturedApi> apis = new ArrayList<>();
		for (CapturedApi api : captured) {
			if (api.seq > afterSeq) {
				apis.add(api);
			}
		}
		apis.sort((a, b) -> Integer.compare(a.seq, b.seq));
		return apis;
	}

	// =========================================================================
	// FILE OUTPUT - static and browser-free so it is unit-testable
	// =========================================================================

	/**
	 * Writes one {@code NNN_METHOD_endpoint.txt} per request plus
	 * {@link #COLLECTED_FILE} into {@code dir} (created if absent). An empty
	 * list still produces the folder and a collected file saying so, so every
	 * test invocation leaves a trace.
	 */
	static Path writeCaptureFiles(Path dir, List<CapturedApi> apis, boolean maskSecrets, String label)
			throws IOException {
		Files.createDirectories(dir);

		StringBuilder collected = new StringBuilder();
		collected.append("# API capture - ").append(label == null ? "" : label).append('\n')
				.append("# Captured: ").append(TIMESTAMP.format(LocalDateTime.now()))
				.append(" | Requests: ").append(apis.size())
				.append(maskSecrets ? " | secrets masked" : "").append('\n')
				.append("# One block per request, in the order the browser sent them. Each block is a\n")
				.append("# Postman-importable cURL (Postman -> Import -> Raw text). The NNN_*.txt files\n")
				.append("# beside this one hold the same commands, one per file; a >>> response block\n")
				.append("# under a request holds what the server answered (status, headers, body).\n");

		if (apis.isEmpty()) {
			collected.append("\n(no API requests were captured during this test)\n");
		}

		for (CapturedApi api : apis) {
			String curl = api.toCurl(maskSecrets);
			String fileName = String.format("%03d_%s_%s.txt", api.seq, api.method, endpointName(api.url));
			Files.writeString(dir.resolve(fileName), curl, StandardCharsets.UTF_8);
			String responseText = buildResponseText(api, maskSecrets);
			if (responseText != null && api.hasResponseDetail()) {
				Files.writeString(dir.resolve(fileName.replaceFirst("\\.txt$", "_response.txt")),
						responseText, StandardCharsets.UTF_8);
			}

			collected.append("\n### ").append(String.format("%03d", api.seq))
					.append(" | ").append(api.method)
					.append(" | ").append(api.status == null ? "-" : api.status)
					.append(" | ").append(api.resourceType == null ? "Other" : api.resourceType)
					.append(" | ").append(api.url).append('\n');
			if (api.failure != null) {
				collected.append("# no response: ").append(api.failure).append('\n');
			}
			if (api.bodyOmitted) {
				collected.append("# request body could not be read from the browser (binary/multipart, or already released)\n");
			}
			collected.append(curl).append('\n');
			if (responseText != null) {
				collected.append(">>> response\n").append(responseText).append("\n<<<\n");
			}
		}

		Files.writeString(dir.resolve(COLLECTED_FILE), collected.toString(), StandardCharsets.UTF_8);
		return dir;
	}

	/**
	 * What the server answered, as plain text: status line, headers, blank line, body.
	 * {@code null} when nothing about a response is known. With masking on, sensitive
	 * header values and credential-looking body fields are replaced with {@code ***}.
	 */
	static String buildResponseText(CapturedApi api, boolean maskSecrets) {
		if (api.status == null && api.responseHeaders.isEmpty()
				&& api.responseBody == null && api.responseNote == null) {
			return null;
		}
		StringBuilder text = new StringBuilder("HTTP ").append(api.status == null ? "-" : api.status);
		if (api.statusText != null && !api.statusText.isBlank()) {
			text.append(' ').append(api.statusText);
		}
		text.append('\n');
		for (Map.Entry<String, String> header : api.responseHeaders.entrySet()) {
			String value = header.getValue() == null ? "" : header.getValue();
			if (maskSecrets && SENSITIVE_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
				value = MASK;
			}
			text.append(header.getKey()).append(": ").append(value).append('\n');
		}
		if (api.responseBody != null) {
			text.append('\n').append(maskSecrets
					? EncryptionUtils.maskSensitiveValues(api.responseBody) : api.responseBody).append('\n');
		}
		if (api.responseNote != null) {
			text.append("# ").append(api.responseNote).append('\n');
		}
		return text.toString().stripTrailing();
	}

	/** Only XHR/fetch answers are read as bodies; no-content statuses and HEAD have none. */
	static boolean wantsBody(String resourceType, Integer status, String method) {
		if (resourceType == null || status == null) {
			return false;
		}
		String type = resourceType.toLowerCase(Locale.ROOT);
		if (!"xhr".equals(type) && !"fetch".equals(type)) {
			return false;
		}
		return status != 204 && status != 205 && status != 304 && !"HEAD".equalsIgnoreCase(method);
	}

	/** WebSockets, event streams and beacons never "finish", so they must not hold up a snapshot. */
	static boolean isLongLivedType(String resourceType) {
		if (resourceType == null) {
			return false;
		}
		String type = resourceType.toLowerCase(Locale.ROOT);
		return "websocket".equals(type) || "eventsource".equals(type) || "ping".equals(type);
	}

	/** {@code NN_<phase>}: numbered so snapshot folders sort in the order they were taken. */
	static String snapshotFolderName(int number, String phase) {
		return String.format("%02d_%s", number, sanitise(phase, "phase"));
	}

	/**
	 * Builds a Postman-importable cURL. HTTP/2 pseudo-headers ({@code :authority}
	 * etc.) are dropped; single quotes in the URL, headers and body are shell-escaped.
	 */
	static String buildCurl(String method, String url, Map<String, String> headers, String postData,
			boolean maskSecrets) {
		StringBuilder curl = new StringBuilder("curl --location --request ")
				.append(method == null ? "GET" : method)
				.append(" '").append(shellQuote(url)).append('\'');

		if (headers != null) {
			for (Map.Entry<String, String> header : headers.entrySet()) {
				String name = header.getKey();
				if (name == null || name.isEmpty() || name.startsWith(":")) {
					continue;
				}
				String value = header.getValue() == null ? "" : header.getValue();
				if (maskSecrets && SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
					value = MASK;
				}
				curl.append(" \\\n--header '").append(shellQuote(name + ": " + value)).append('\'');
			}
		}

		if (postData != null && !postData.isEmpty()) {
			String body = maskSecrets ? EncryptionUtils.maskSensitiveValues(postData) : postData;
			curl.append(" \\\n--data-raw '").append(shellQuote(body)).append('\'');
		}
		return curl.toString();
	}

	/** {@code true} for anything worth replaying as an API call; static assets and browser-internal URLs are out. */
	static boolean isApiRequest(String url, String resourceType) {
		if (url == null || url.isBlank()) {
			return false;
		}
		String lower = url.toLowerCase(Locale.ROOT);
		int colon = lower.indexOf(':');
		if (colon > 0 && SKIPPED_SCHEMES.contains(lower.substring(0, colon))) {
			return false;
		}
		if (resourceType != null && SKIPPED_RESOURCE_TYPES.contains(resourceType.toLowerCase(Locale.ROOT))) {
			return false;
		}
		String path = lower;
		int cut = path.indexOf('?');
		if (cut >= 0) {
			path = path.substring(0, cut);
		}
		cut = path.indexOf('#');
		if (cut >= 0) {
			path = path.substring(0, cut);
		}
		for (String ext : STATIC_EXTENSIONS) {
			if (path.endsWith(ext)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A CORS preflight: an {@code OPTIONS} the browser sends by itself, carrying
	 * {@code Access-Control-Request-Method}. Chrome reports it with resource type
	 * "Other", so the type filter alone does not catch it. It cannot be replayed
	 * meaningfully, and the real request that follows is captured anyway.
	 */
	static boolean isPreflight(String method, Map<String, String> headers) {
		if (!"OPTIONS".equalsIgnoreCase(method) || headers == null) {
			return false;
		}
		for (String name : headers.keySet()) {
			if ("access-control-request-method".equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Last non-empty path segment of the URL, sanitised for a file name; the host for "/" requests. */
	static String endpointName(String url) {
		try {
			URI uri = URI.create(url.trim());
			String path = uri.getPath();
			if (path == null || path.isBlank() || "/".equals(path)) {
				return sanitise(uri.getHost(), "root");
			}
			String[] segments = path.split("/");
			int last = lastNonEmptySegment(segments, segments.length - 1);
			if (last < 0) {
				return "endpoint";
			}
			String segment = sanitise(segments[last], "");
			// ".../delay/1" or ".../orders/<uuid>": the last segment alone says nothing, keep its parent too.
			if (isIdentifierSegment(segment)) {
				int parent = lastNonEmptySegment(segments, last - 1);
				if (parent >= 0) {
					return sanitise(segments[parent], "") + "_" + segment;
				}
			}
			return segment;
		} catch (RuntimeException e) {
			return "unknown_endpoint";
		}
	}

	/** Index of the nearest segment at or before {@code from} that is not empty once sanitised; -1 if none. */
	private static int lastNonEmptySegment(String[] segments, int from) {
		int i = from;
		while (i >= 0 && sanitise(segments[i], "").isEmpty()) {
			i--;
		}
		return i;
	}

	/** A bare number or UUID: an id, not a name. */
	private static boolean isIdentifierSegment(String segment) {
		return segment.matches("\\d+")
				|| segment.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	}

	/** {@code <method>_<dataKey>_<NN>} - the per-DataProvider-row, per-invocation folder name. */
	static String folderNameFor(String methodName, String dataKey, int invocation) {
		return sanitise(methodName, "test") + "_" + sanitise(dataKey, "nodata")
				+ "_" + String.format("%02d", invocation);
	}

	/** Increments and returns the JVM-wide run counter for {@code testCase#method}. */
	static int nextInvocation(String testCaseName, String methodName) {
		return INVOCATIONS.computeIfAbsent(testCaseName + "#" + methodName, k -> new AtomicInteger())
				.incrementAndGet();
	}

	/** {@code ./reports/<run>/apicapture} - same report-folder resolution as screenshots and videos. */
	static Path captureRoot() {
		String base = (ExtentReportManager.getFolderName() != null && !ExtentReportManager.getFolderName().isEmpty())
				? ExtentReportManager.getFolderName()
				: "reports/default";
		return Paths.get(".", base, CAPTURE_DIR);
	}

	static boolean isChromiumBased(Capabilities caps) {
		String name = caps == null || caps.getBrowserName() == null
				? "" : caps.getBrowserName().toLowerCase(Locale.ROOT);
		return name.contains("chrome") || name.contains("edge") || name.contains("chromium");
	}

	/** Replaces anything outside {@code [A-Za-z0-9_-]} with '_' and caps the length; {@code fallback} for null/blank. */
	static String sanitise(String value, String fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String safe = value.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
		if (safe.length() > MAX_NAME_LENGTH) {
			safe = safe.substring(0, MAX_NAME_LENGTH);
		}
		return safe.isEmpty() ? fallback : safe;
	}

	// =========================================================================
	// PRIVATE HELPERS
	// =========================================================================

	private RemoteWebDriver currentDriver() {
		if (driverSupplier == null) {
			return null;
		}
		try {
			return driverSupplier.get();
		} catch (RuntimeException e) {
			logger.debug("Driver supplier threw: {}", e.toString());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : null;
	}

	private static Map<String, String> toStringMap(Map<String, Object> raw) {
		if (raw == null) {
			return Collections.emptyMap();
		}
		Map<String, String> out = new LinkedHashMap<>();
		raw.forEach((k, v) -> out.put(k, v == null ? "" : String.valueOf(v)));
		return out;
	}

	private static String str(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static Integer toInt(Object value) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		try {
			return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Exception chain as one line, so a wrapped transport error shows its real cause. */
	static String describe(Throwable error) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = error; t != null && sb.length() < 600; t = t.getCause() == t ? null : t.getCause()) {
			String first = String.valueOf(t.getMessage()).split("\n")[0];
			sb.append(sb.length() == 0 ? "" : " <- ").append(t.getClass().getSimpleName()).append(": ").append(first);
		}
		return sb.toString();
	}

	private static String shellQuote(String value) {
		return value == null ? "" : value.replace("'", "'\\''");
	}

	// =========================================================================
	// MINIMAL CDP CLIENT
	// =========================================================================

	/**
	 * Just enough of a Chrome DevTools Protocol client for this class: JSON
	 * request/reply correlation by id, fire-and-forget commands, and a callback for
	 * events. Replies are completed on the reader thread; events are handed to
	 * {@code eventHandler} on the reader thread as well, so it must not block.
	 * Flattened sessions are used throughout, so every message simply carries an
	 * optional {@code sessionId}.
	 */
	private static final class CdpSocket implements WebSocket.Listener {
		private final HttpClient client;
		private final WebSocket socket;
		private final AtomicLong ids = new AtomicLong();
		private final ConcurrentMap<Long, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
		private final Consumer<Map<String, Object>> eventHandler;
		private final Object writeLock = new Object();
		private volatile boolean closed;

		CdpSocket(HttpClient.Factory factory, URI clientBase, URI webSocketUrl,
				Consumer<Map<String, Object>> eventHandler) {
			this.eventHandler = eventHandler;
			this.client = CdpEndpointFinder.getHttpClient(factory, clientBase);
			this.socket = client.openSocket(new HttpRequest(HttpMethod.GET, webSocketUrl.toString()), this);
		}

		/** Sends a command and waits for its reply; returns the reply's {@code result} object. */
		Map<String, Object> call(String sessionId, String method, Map<String, Object> params, Duration timeout) {
			long id = ids.incrementAndGet();
			CompletableFuture<Map<String, Object>> reply = new CompletableFuture<>();
			pending.put(id, reply);
			try {
				write(id, sessionId, method, params);
				Map<String, Object> message = reply.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
				if (message.get("error") != null) {
					throw new IllegalStateException("CDP " + method + " failed: " + message.get("error"));
				}
				Object result = message.get("result");
				return result instanceof Map ? castMap(result) : Collections.emptyMap();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted during CDP " + method, e);
			} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
				throw new IllegalStateException("CDP " + method + " got no reply: " + e, e);
			} finally {
				pending.remove(id);
			}
		}

		/** Sends a command without waiting; the reply (if any) is ignored. Never blocks on the browser. */
		void post(String sessionId, String method, Map<String, Object> params) {
			try {
				write(ids.incrementAndGet(), sessionId, method, params);
			} catch (RuntimeException e) {
				logger.debug("CDP {} could not be sent: {}", method, e.toString());
			}
		}

		private void write(long id, String sessionId, String method, Map<String, Object> params) {
			if (closed) {
				throw new IllegalStateException("CDP socket is closed");
			}
			Map<String, Object> command = new LinkedHashMap<>();
			command.put("id", id);
			command.put("method", method);
			command.put("params", params == null ? Collections.emptyMap() : params);
			if (sessionId != null) {
				command.put("sessionId", sessionId);
			}
			synchronized (writeLock) {
				socket.send(new TextMessage(JSON.toJson(command)));
			}
		}

		@Override
		public void onText(CharSequence data) {
			Map<String, Object> message;
			try {
				message = JSON.toType(data.toString(), Json.MAP_TYPE);
			} catch (RuntimeException e) {
				logger.debug("Unparseable CDP message dropped: {}", e.toString());
				return;
			}
			Object id = message.get("id");
			if (id instanceof Number) {
				CompletableFuture<Map<String, Object>> waiting = pending.get(((Number) id).longValue());
				if (waiting != null) {
					waiting.complete(message);
				}
				return;
			}
			try {
				eventHandler.accept(message);
			} catch (RuntimeException e) {
				logger.debug("CDP event handler failed: {}", e.toString());
			}
		}

		@Override
		public void onClose(int code, String reason) {
			closed = true;
			pending.values().forEach(f -> f.completeExceptionally(new IllegalStateException("CDP socket closed")));
		}

		@Override
		public void onError(Throwable cause) {
			logger.debug("CDP socket error: {}", cause.toString());
		}

		void close() {
			closed = true;
			try {
				socket.close();
			} catch (RuntimeException e) {
				logger.debug("CDP socket close failed: {}", e.toString());
			}
			try {
				client.close();
			} catch (RuntimeException e) {
				logger.debug("CDP client close failed: {}", e.toString());
			}
		}

		@SuppressWarnings("unchecked")
		private static Map<String, Object> castMap(Object value) {
			return (Map<String, Object>) value;
		}
	}

	// =========================================================================
	// VALUE OBJECT
	// =========================================================================

	/** One captured browser request. Immutable apart from status/failure, which arrive on later events. */
	public static final class CapturedApi {
		private final int seq;
		private final String requestId;
		private final String method;
		private final String url;
		private final String resourceType;
		private final Map<String, String> headers;
		private final String postData;
		private final boolean bodyOmitted;
		private volatile Integer status;
		private volatile String failure;
		private volatile String statusText;
		private volatile String mimeType;
		private volatile Map<String, String> responseHeaders = Collections.emptyMap();
		private volatile String responseBody;
		private volatile String responseNote;

		public CapturedApi(int seq, String requestId, String method, String url, String resourceType,
				Map<String, String> headers, String postData, boolean bodyOmitted) {
			this.seq = seq;
			this.requestId = requestId;
			this.method = method == null ? "GET" : method;
			this.url = url;
			this.resourceType = resourceType;
			this.headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
			this.postData = postData;
			this.bodyOmitted = bodyOmitted;
		}

		public int getSeq() { return seq; }
		public String getRequestId() { return requestId; }
		public String getMethod() { return method; }
		public String getUrl() { return url; }
		public String getResourceType() { return resourceType; }
		public Map<String, String> getHeaders() { return Collections.unmodifiableMap(new LinkedHashMap<>(headers)); }
		public Optional<String> getPostData() { return Optional.ofNullable(postData); }
		public boolean isBodyOmitted() { return bodyOmitted; }
		public Integer getStatus() { return status; }
		public void setStatus(Integer status) { this.status = status; }
		public String getFailure() { return failure; }
		public void setFailure(String failure) { this.failure = failure; }
		public String getStatusText() { return statusText; }
		public void setStatusText(String statusText) { this.statusText = statusText; }
		public String getMimeType() { return mimeType; }
		public void setMimeType(String mimeType) { this.mimeType = mimeType; }
		public Map<String, String> getResponseHeaders() {
			return Collections.unmodifiableMap(new LinkedHashMap<>(responseHeaders));
		}
		public void setResponseHeaders(Map<String, String> headers) {
			this.responseHeaders = headers == null ? Collections.emptyMap()
					: Collections.unmodifiableMap(new LinkedHashMap<>(headers));
		}
		public String getResponseBody() { return responseBody; }
		public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
		public String getResponseNote() { return responseNote; }
		public void setResponseNote(String responseNote) { this.responseNote = responseNote; }

		/** {@code true} when there is more to say about the response than its status code. */
		boolean hasResponseDetail() {
			return !responseHeaders.isEmpty() || responseBody != null || responseNote != null;
		}

		public String toCurl(boolean maskSecrets) {
			return buildCurl(method, url, headers, postData, maskSecrets);
		}

		@Override
		public String toString() {
			return String.format("#%03d %s %s (%s, status=%s)", seq, method, url, resourceType, status);
		}
	}

	/** Result of one snapshot: the folder written ({@code null} if writing failed) and the calls it holds. */
	public static final class Snapshot {
		private final Path dir;
		private final List<CapturedApi> calls;

		Snapshot(Path dir, List<CapturedApi> calls) {
			this.dir = dir;
			this.calls = Collections.unmodifiableList(new ArrayList<>(calls));
		}

		public Path getDir() { return dir; }
		public List<CapturedApi> getCalls() { return calls; }
	}

	/**
	 * Tracks which requests are still in flight and when the last one started or
	 * finished. Timestamps are passed in, so the logic is unit-testable without waiting.
	 */
	static final class ActivityTracker {
		private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
		private volatile long lastActivityNanos;

		ActivityTracker() {
			this(System.nanoTime());
		}

		/** @param initialNanos clock reading to treat as the last activity so far (tests pass a fake clock) */
		ActivityTracker(long initialNanos) {
			this.lastActivityNanos = initialNanos;
		}

		void started(String key, long nowNanos) {
			inFlight.add(key);
			lastActivityNanos = nowNanos;
		}

		void finished(String key, long nowNanos) {
			inFlight.remove(key);
			lastActivityNanos = nowNanos;
		}

		/** A window or frame went away: its unfinished requests will never report back. */
		void forgetSession(String sessionId, long nowNanos) {
			inFlight.removeIf(key -> key.startsWith(sessionId + "|"));
			lastActivityNanos = nowNanos;
		}

		int inFlightCount() {
			return inFlight.size();
		}

		/**
		 * Quiet = nothing in flight, and at least {@code quietNanos} since the later
		 * of the last activity and {@code floorNanos} (the moment the wait began).
		 */
		boolean isQuiet(long nowNanos, long floorNanos, long quietNanos) {
			if (!inFlight.isEmpty()) {
				return false;
			}
			long reference = lastActivityNanos - floorNanos > 0 ? lastActivityNanos : floorNanos;
			return nowNanos - reference >= quietNanos;
		}
	}
}
