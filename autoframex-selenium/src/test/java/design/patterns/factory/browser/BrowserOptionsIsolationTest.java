package design.patterns.factory.browser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Coverage for {@code ChromeBrowser}/{@code FireFoxBrowser}/{@code EdgeBrowser}'s
 * {@code getBrowserOptions()} — the part of {@code design.patterns.factory.browser}
 * that's actually testable without a real browser process (the package sat at
 * ~19% line coverage; {@code launchBrowser()} itself needs a live driver, but
 * options-building is pure object construction).
 *
 * <p>Also locks in the architecture review's observation that each provider is
 * a singleton wrapping a <i>stateless</i> options-builder: two calls, even from
 * different threads, must never share or leak state through the singleton.
 */
public class BrowserOptionsIsolationTest {

	@Test
	public void chromeOptionsAreFreshOnEveryCall() {
		ChromeBrowser browser = ChromeBrowser.getInstance();
		ChromeOptions first = browser.getBrowserOptions();
		ChromeOptions second = browser.getBrowserOptions();
		Assert.assertNotSame(first, second,
				"getBrowserOptions() must build a new ChromeOptions per call — a shared instance "
				+ "would let one thread's mutation (e.g. a merged capability) leak into another's.");
	}

	@Test
	public void firefoxOptionsAreFreshOnEveryCall() {
		FireFoxBrowser browser = FireFoxBrowser.getInstance();
		Assert.assertNotSame(browser.getBrowserOptions(), browser.getBrowserOptions());
	}

	@Test
	public void edgeOptionsAreFreshOnEveryCall() {
		EdgeBrowser browser = EdgeBrowser.getInstance();
		Assert.assertNotSame(browser.getBrowserOptions(), browser.getBrowserOptions());
	}

	@Test
	public void chromeDownloadDirectoryIsKeyedByCallingThreadId() throws InterruptedException {
		ChromeBrowser browser = ChromeBrowser.getInstance();
		AtomicReference<String> pathFromThreadA = new AtomicReference<>();
		AtomicReference<String> pathFromThreadB = new AtomicReference<>();

		Thread threadA = new Thread(() ->
				pathFromThreadA.set(downloadDirOf(browser.getBrowserOptions())), "options-test-thread-A");
		Thread threadB = new Thread(() ->
				pathFromThreadB.set(downloadDirOf(browser.getBrowserOptions())), "options-test-thread-B");

		threadA.start();
		threadA.join();
		threadB.start();
		threadB.join();

		Assert.assertNotNull(pathFromThreadA.get());
		Assert.assertNotNull(pathFromThreadB.get());
		Assert.assertNotEquals(pathFromThreadA.get(), pathFromThreadB.get(),
				"Two different threads must get two different download directories — a shared "
				+ "directory would let concurrent Chrome instances collide on identical filenames.");
		Assert.assertTrue(pathFromThreadA.get().endsWith(String.valueOf(threadA.getId())));
		Assert.assertTrue(pathFromThreadB.get().endsWith(String.valueOf(threadB.getId())));
	}

	@Test
	public void headlessSystemPropertyControlsChromeHeadlessArgument() {
		String previous = System.getProperty("headless");
		try {
			System.setProperty("headless", "true");
			List<String> argsHeadless = chromeArgs(ChromeBrowser.getInstance().getBrowserOptions());
			Assert.assertTrue(argsHeadless.contains("--headless=new"),
					"headless=true should add --headless=new; got: " + argsHeadless);

			System.setProperty("headless", "false");
			List<String> argsHeaded = chromeArgs(ChromeBrowser.getInstance().getBrowserOptions());
			Assert.assertFalse(argsHeaded.contains("--headless=new"),
					"headless=false must not add --headless=new; got: " + argsHeaded);
		} finally {
			// System properties are JVM-global — restore so this doesn't bleed into
			// whatever suite/test runs next in the same forked JVM.
			if (previous == null) {
				System.clearProperty("headless");
			} else {
				System.setProperty("headless", previous);
			}
		}
	}

	@Test
	public void chromeOptionsDoNotHardcodeARemoteDebuggingPort() {
		// Regression guard for the fixed-CDP-port finding from the framework-3.1
		// architecture review: a hardcoded --remote-debugging-port collided across
		// concurrently-launched local Chrome instances. Ensures nobody reintroduces it.
		List<String> args = chromeArgs(ChromeBrowser.getInstance().getBrowserOptions());
		boolean hasFixedPort = args.stream().anyMatch(a -> a.startsWith("--remote-debugging-port="));
		Assert.assertFalse(hasFixedPort,
				"Found a hardcoded --remote-debugging-port argument — this caused "
				+ "SessionNotCreatedException (\"unable to connect to renderer\") under concurrent "
				+ "local Chrome launches. Let ChromeDriver auto-assign the port per instance.");
	}

	@SuppressWarnings("unchecked")
	private static String downloadDirOf(ChromeOptions options) {
		Map<String, Object> chromeOptions = (Map<String, Object>) options.asMap().get("goog:chromeOptions");
		Map<String, Object> prefs = (Map<String, Object>) chromeOptions.get("prefs");
		return (String) prefs.get("download.default_directory");
	}

	@SuppressWarnings("unchecked")
	private static List<String> chromeArgs(ChromeOptions options) {
		Map<String, Object> chromeOptions = (Map<String, Object>) options.asMap().get("goog:chromeOptions");
		return (List<String>) chromeOptions.get("args");
	}
}
