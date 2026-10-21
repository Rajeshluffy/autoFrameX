package design.patterns.factory.browser;

import java.util.List;
import java.util.Map;

import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * The browser providers used to switch off the sandbox, certificate validation and Chrome's
 * download protection on every run, production included. These tests pin the new rules:
 * the sandbox flag only in CI or containers, the other two never for production, and an
 * explicit setting always wins.
 */
public class BrowserSecurityPolicyTest {

	private static final String[] PROPERTIES = {"noSandbox", "acceptInsecureCerts", "relaxedDownloads", "environment"};

	@AfterMethod(alwaysRun = true)
	public void clearOverrides() {
		for (String key : PROPERTIES) {
			System.clearProperty(key);
		}
	}

	// -------------------------------------------------------------------------
	// the pure decisions
	// -------------------------------------------------------------------------

	@Test
	public void anExplicitSettingAlwaysBeatsTheAutomaticDefault() {
		Assert.assertTrue(BrowserSecurityPolicy.decide("true", false));
		Assert.assertFalse(BrowserSecurityPolicy.decide("false", true));
		Assert.assertTrue(BrowserSecurityPolicy.decide(" TRUE ", false), "whitespace and case are ignored");
		Assert.assertTrue(BrowserSecurityPolicy.decide(null, true), "no setting: the automatic default applies");
		Assert.assertFalse(BrowserSecurityPolicy.decide("  ", false));
	}

	@Test
	public void onlyProductionCountsAsProduction() {
		Assert.assertTrue(BrowserSecurityPolicy.isProduction("prod"));
		Assert.assertTrue(BrowserSecurityPolicy.isProduction(" Production "));
		Assert.assertFalse(BrowserSecurityPolicy.isProduction("qa"));
		Assert.assertFalse(BrowserSecurityPolicy.isProduction("dev"));
		Assert.assertFalse(BrowserSecurityPolicy.isProduction(null));
	}

	@Test
	public void ciAndContainersAreRecognisedFromTheirMarkers() {
		Assert.assertFalse(BrowserSecurityPolicy.inCiOrContainer(name -> null, () -> false), "a plain developer machine");
		Assert.assertTrue(BrowserSecurityPolicy.inCiOrContainer(name -> "CI".equals(name) ? "true" : null, () -> false));
		Assert.assertTrue(BrowserSecurityPolicy.inCiOrContainer(name -> "JENKINS_URL".equals(name) ? "http://j" : null, () -> false));
		Assert.assertTrue(BrowserSecurityPolicy.inCiOrContainer(name -> "GITHUB_ACTIONS".equals(name) ? "true" : null, () -> false));
		Assert.assertTrue(BrowserSecurityPolicy.inCiOrContainer(name -> "KUBERNETES_SERVICE_HOST".equals(name) ? "10.0.0.1" : null, () -> false));
		Assert.assertTrue(BrowserSecurityPolicy.inCiOrContainer(name -> null, () -> true), "Docker's /.dockerenv marker file");
		Assert.assertFalse(BrowserSecurityPolicy.inCiOrContainer(name -> "CI".equals(name) ? "false" : null, () -> false),
				"CI=false means not CI");
	}

	// -------------------------------------------------------------------------
	// what the providers actually build
	// -------------------------------------------------------------------------

	@Test
	public void productionRunsGetStrictCertificatesAndDownloadProtection() {
		System.setProperty("environment", "prod");

		ChromeOptions chrome = ChromeBrowser.getInstance().getBrowserOptions();
		Assert.assertEquals(chrome.getCapability("acceptInsecureCerts"), Boolean.FALSE);
		Assert.assertEquals(chromePrefs(chrome).get("safebrowsing.disable_download_protection"), Boolean.FALSE,
				"download protection stays on for production");
		Assert.assertEquals(EdgeBrowser.getInstance().getBrowserOptions().getCapability("acceptInsecureCerts"), Boolean.FALSE);
		Assert.assertEquals(FireFoxBrowser.getInstance().getBrowserOptions().getCapability("acceptInsecureCerts"), Boolean.FALSE);
	}

	@Test
	public void devAndQaRunsKeepTheRelaxedBehaviourTheyAlwaysHad() {
		System.setProperty("environment", "qa");

		ChromeOptions chrome = ChromeBrowser.getInstance().getBrowserOptions();
		Assert.assertEquals(chrome.getCapability("acceptInsecureCerts"), Boolean.TRUE);
		Assert.assertEquals(chromePrefs(chrome).get("safebrowsing.disable_download_protection"), Boolean.TRUE);
	}

	@Test
	public void anExplicitSettingOverridesTheEnvironmentRule() {
		System.setProperty("environment", "prod");
		System.setProperty("acceptInsecureCerts", "true");
		System.setProperty("relaxedDownloads", "true");

		ChromeOptions chrome = ChromeBrowser.getInstance().getBrowserOptions();
		Assert.assertEquals(chrome.getCapability("acceptInsecureCerts"), Boolean.TRUE, "explicit true wins even for prod");
		Assert.assertEquals(chromePrefs(chrome).get("safebrowsing.disable_download_protection"), Boolean.TRUE);

		System.setProperty("environment", "dev");
		System.setProperty("acceptInsecureCerts", "false");
		Assert.assertEquals(ChromeBrowser.getInstance().getBrowserOptions().getCapability("acceptInsecureCerts"), Boolean.FALSE,
				"explicit false wins even for dev");
	}

	@Test
	public void theSandboxFlagFollowsTheExplicitSettingInBothChromiumBrowsers() {
		System.setProperty("noSandbox", "true");
		Assert.assertTrue(chromeArgs(ChromeBrowser.getInstance().getBrowserOptions()).contains("--no-sandbox"));
		Assert.assertTrue(edgeArgs(EdgeBrowser.getInstance().getBrowserOptions()).contains("--no-sandbox"));

		System.setProperty("noSandbox", "false");
		Assert.assertFalse(chromeArgs(ChromeBrowser.getInstance().getBrowserOptions()).contains("--no-sandbox"),
				"no flag when explicitly switched off, whatever machine this runs on");
		Assert.assertFalse(edgeArgs(EdgeBrowser.getInstance().getBrowserOptions()).contains("--no-sandbox"));
	}

	@Test
	public void firefoxOptionsStillBuild() {
		FirefoxOptions options = FireFoxBrowser.getInstance().getBrowserOptions();
		Assert.assertNotNull(options);
	}

	// -------------------------------------------------------------------------
	// helpers
	// -------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private static Map<String, Object> chromePrefs(ChromeOptions options) {
		Map<String, Object> chromeOptions = (Map<String, Object>) options.asMap().get("goog:chromeOptions");
		return (Map<String, Object>) chromeOptions.get("prefs");
	}

	@SuppressWarnings("unchecked")
	private static List<String> chromeArgs(ChromeOptions options) {
		Map<String, Object> chromeOptions = (Map<String, Object>) options.asMap().get("goog:chromeOptions");
		return (List<String>) chromeOptions.get("args");
	}

	@SuppressWarnings("unchecked")
	private static List<String> edgeArgs(EdgeOptions options) {
		Map<String, Object> edgeOptions = (Map<String, Object>) options.asMap().get("ms:edgeOptions");
		return (List<String>) edgeOptions.get("args");
	}
}
