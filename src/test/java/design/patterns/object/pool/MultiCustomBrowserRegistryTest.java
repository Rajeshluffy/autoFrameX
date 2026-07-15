package design.patterns.object.pool;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import design.patterns.factory.browser.Browser;
import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserRegistry;

/**
 * Proves the BrowserType-registry-rework claim: {@link BrowserRegistry} supports
 * any number of independently-pooled custom browsers registered under distinct
 * ids, resolved by a single {@link WebDriverPoolFactory} instance without
 * collision — unlike the old design's single fixed {@code BrowserType.CUSTOM} slot.
 *
 * <p>Each custom provider throws a distinct marker exception instead of
 * launching a real browser, so this runs fast and needs no real WebDriver
 * session; {@link WebDriverPoolFactory#acquire} wraps it in a
 * {@code DriverAcquisitionException}, whose cause message we assert on to
 * confirm resolution routed to the correct provider each time.
 *
 * <p>Throwaway verification for the BrowserType registry rework — not part of
 * the framework's public onboarding surface.
 */
public class MultiCustomBrowserRegistryTest {

	@Test
	public void poolSupportsTwoIndependentCustomBrowsersSimultaneously() {
		BrowserRegistry.register("TEST_CUSTOM_A", config -> markerBrowser("MARKER_A_LAUNCHED"));
		BrowserRegistry.register("TEST_CUSTOM_B", config -> markerBrowser("MARKER_B_LAUNCHED"));

		PoolConfig config = new PoolConfig.Builder()
				.minPoolSize(0) // skip pre-warm — these providers don't create real drivers
				.clearSupportedBrowsers()
				.addSupportedBrowser("TEST_CUSTOM_A")
				.addSupportedBrowser("TEST_CUSTOM_B")
				.build();

		WebDriverPoolFactory pool = new WebDriverPoolFactory(new BrowserFactory(), config);
		try {
			WebDriverPoolFactory.DriverAcquisitionException exA = Assert.expectThrows(
					WebDriverPoolFactory.DriverAcquisitionException.class,
					() -> pool.acquire("TEST_CUSTOM_A", null));
			Assert.assertTrue(rootCauseMessage(exA).contains("MARKER_A_LAUNCHED"),
					"Expected TEST_CUSTOM_A to resolve to its own provider, got: " + rootCauseMessage(exA));

			WebDriverPoolFactory.DriverAcquisitionException exB = Assert.expectThrows(
					WebDriverPoolFactory.DriverAcquisitionException.class,
					() -> pool.acquire("TEST_CUSTOM_B", null));
			Assert.assertTrue(rootCauseMessage(exB).contains("MARKER_B_LAUNCHED"),
					"Expected TEST_CUSTOM_B to resolve to its own provider, got: " + rootCauseMessage(exB));
		} finally {
			pool.close();
		}
	}

	private static Browser markerBrowser(String marker) {
		return new Browser() {
			@Override
			public RemoteWebDriver launchBrowser() {
				throw new RuntimeException(marker);
			}

			@Override
			public RemoteWebDriver launchBrowser(Capabilities capabilities) {
				throw new RuntimeException(marker);
			}
		};
	}

	private static String rootCauseMessage(Throwable t) {
		Throwable cause = t;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause.getMessage() != null ? cause.getMessage() : "";
	}
}
