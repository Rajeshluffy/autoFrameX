package design.patterns.factory.browser;

import java.util.List;
import java.util.Map;

import org.openqa.selenium.MutableCapabilities;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import design.patterns.object.pool.PoolConfig;

/**
 * The browser providers decided "headless" only from {@code -Dheadless} or the {@code HEADLESS} variable. A suite
 * with {@code <parameter name="headless" value="true"/>} (or {@code autoFrameX.browser.isheadless=true} in the
 * project file) resolved to headless in the framework's own configuration, yet Chrome still started with a window,
 * and failed to start at all in a container without a display. The pool now carries the resolved setting and each
 * provider follows it; only a pool built without one falls back to the property and the variable.
 */
public class BrowserHeadlessConfigTest {

	@AfterMethod(alwaysRun = true)
	public void clearProperty() {
		System.clearProperty("headless");
	}

	private static PoolConfig pool(Boolean headless) {
		PoolConfig.Builder builder = new PoolConfig.Builder().minPoolSize(0);
		if (headless != null) {
			builder.headless(headless);
		}
		return builder.build();
	}

	@SuppressWarnings("unchecked")
	private static List<String> args(MutableCapabilities options, String vendorKey) {
		Map<String, Object> vendor = (Map<String, Object>) options.asMap().get(vendorKey);
		Object args = vendor == null ? null : vendor.get("args");
		return args == null ? List.of() : (List<String>) args;   // no arguments at all is the windowed case
	}

	private static boolean chromeHeadless(PoolConfig config) {
		return args(ChromeBrowser.forConfig(config).getBrowserOptions(), "goog:chromeOptions").contains("--headless=new");
	}

	private static boolean edgeHeadless(PoolConfig config) {
		return args(EdgeBrowser.forConfig(config).getBrowserOptions(), "ms:edgeOptions").contains("--headless=new");
	}

	private static boolean firefoxHeadless(PoolConfig config) {
		return args(FireFoxBrowser.forConfig(config).getBrowserOptions(), "moz:firefoxOptions").contains("-headless");
	}

	// ---------------------------------------------------------------- the configured value is used

	@Test
	public void aHeadlessPoolConfigStartsEveryBrowserHeadlessWithoutAnyJvmProperty() {
		PoolConfig headless = pool(true);
		Assert.assertTrue(chromeHeadless(headless), "chrome");
		Assert.assertTrue(edgeHeadless(headless), "edge");
		Assert.assertTrue(firefoxHeadless(headless), "firefox");
	}

	@Test
	public void aNonHeadlessPoolConfigWinsOverTheJvmProperty() {
		// The suite said headless=false; a stray -Dheadless=true must not override a value set closer to the test.
		System.setProperty("headless", "true");
		PoolConfig windowed = pool(false);
		Assert.assertFalse(chromeHeadless(windowed), "chrome");
		Assert.assertFalse(edgeHeadless(windowed), "edge");
		Assert.assertFalse(firefoxHeadless(windowed), "firefox");
	}

	// ----------------------------------------------------------- nothing configured: the old behaviour

	@Test
	public void aPoolWithoutAHeadlessSettingStillHonoursTheJvmProperty() {
		System.setProperty("headless", "true");
		PoolConfig unset = pool(null);
		Assert.assertNull(unset.getHeadless(), "not configured");
		Assert.assertTrue(chromeHeadless(unset), "chrome");
		Assert.assertTrue(edgeHeadless(unset), "edge");
		Assert.assertTrue(firefoxHeadless(unset), "firefox");
	}

	@Test
	public void theSingletonAccessorKeepsTheOldBehaviour() {
		System.setProperty("headless", "true");
		Assert.assertTrue(args(ChromeBrowser.getInstance().getBrowserOptions(), "goog:chromeOptions").contains("--headless=new"));
		System.setProperty("headless", "false");
		Assert.assertFalse(args(ChromeBrowser.getInstance().getBrowserOptions(), "goog:chromeOptions").contains("--headless=new"));
	}

	@Test
	public void theRegistryBuildsProvidersFromThePoolConfig() {
		PoolConfig headless = pool(true);
		Browser chrome = BrowserRegistry.resolve("CHROME", headless);
		Assert.assertTrue(args(((ChromeBrowser) chrome).getBrowserOptions(), "goog:chromeOptions").contains("--headless=new"),
				"a provider resolved through the registry follows the pool's headless setting");
	}
}
