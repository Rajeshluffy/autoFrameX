package design.patterns.factory.browser;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Proves the `RemoteGridBrowser` config-decoupling fix: the 2-arg constructor
 * (used by {@link BrowserRegistry}'s {@code GRID_*} providers) targets the
 * URL passed directly to it, never reaching into {@code ConfigManager} —
 * unlike the legacy 1-arg constructor, kept only for a caller holding a raw
 * {@code BrowserType} constant.
 *
 * <p>No real Selenium Grid hub is available in this environment, so instead
 * of asserting against a live grid, this compares the two constructors'
 * *failure signatures*: the 2-arg constructor is given a hostname under the
 * {@code .invalid} TLD (reserved by RFC 2606 to never resolve), so it must
 * fail with a DNS-resolution error ({@code UnresolvedAddressException}); the
 * 1-arg legacy constructor falls back to {@code frameworkConfig.properties}'
 * default ({@code http://localhost:4444/wd/hub} — see that file), where DNS
 * resolves fine but nothing is listening, so it must fail with a
 * connection-refused error instead. Different failure shapes for the two
 * constructors is exactly what "the 2-arg one doesn't touch ConfigManager"
 * predicts.
 *
 * <p>Throwaway verification for the BrowserType registry rework — not part of
 * the framework's public onboarding surface.
 */
public class RemoteGridBrowserDecouplingTest {

	@Test
	public void twoArgConstructorFailsOnDnsNotConnectionRefused() {
		RemoteGridBrowser browser = new RemoteGridBrowser("chrome", "http://fake-hub.invalid:4444/wd/hub");

		Exception ex = Assert.expectThrows(Exception.class, () -> browser.launchBrowser(null));

		String chain = exceptionChainClassNames(ex);
		Assert.assertTrue(chain.contains("UnresolvedAddressException"),
				"Expected a DNS-resolution failure (UnresolvedAddressException) for the "
				+ ".invalid hostname supplied directly to the 2-arg constructor — got: " + chain);
	}

	@Test
	public void oneArgLegacyConstructorFailsOnConnectionRefusedNotDns() {
		RemoteGridBrowser browser = new RemoteGridBrowser("chrome"); // legacy path — reads ConfigManager

		Exception ex = Assert.expectThrows(Exception.class, () -> browser.launchBrowser(null));

		String chain = exceptionChainClassNames(ex);
		Assert.assertFalse(chain.contains("UnresolvedAddressException"),
				"Legacy 1-arg constructor should resolve 'localhost' (from ConfigManager's "
				+ "default frameworkConfig.properties value) fine and fail on connection-refused "
				+ "instead — got a DNS failure, meaning it did NOT reach ConfigManager as expected: " + chain);
	}

	private static String exceptionChainClassNames(Throwable t) {
		StringBuilder sb = new StringBuilder();
		for (Throwable cur = t; cur != null; cur = cur.getCause()) {
			sb.append(cur.getClass().getName()).append(" -> ");
		}
		return sb.toString();
	}
}
