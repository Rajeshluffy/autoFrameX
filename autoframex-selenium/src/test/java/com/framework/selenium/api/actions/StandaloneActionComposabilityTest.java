package com.framework.selenium.api.actions;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.framework.utils.Reporter;

/**
 * Proves the TD-07 composability claim: a consumer that only needs one
 * narrow capability can compose that single action class directly, without
 * inheriting the full ~85-method {@code SeleniumBase} surface (and without
 * TestNG lifecycle wiring — {@link Reporter}'s {@code @Before*}/{@code @After*}
 * annotations never fire here; {@link #reporter} is just a plain object).
 *
 * <p>Throwaway verification for the composition refactor — not part of the
 * framework's public onboarding surface.
 */
public class StandaloneActionComposabilityTest {

	private RemoteWebDriver driver;
	private ClickActions clickActions;

	@BeforeClass
	public void setUp() {
		ChromeOptions options = new ChromeOptions();
		if ("true".equalsIgnoreCase(System.getProperty("headless", System.getenv("HEADLESS")))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--no-sandbox", "--disable-dev-shm-usage");
		driver = new ChromeDriver(options);

		// Minimal Reporter — no ExtentReports/TestNG node exists, so reportStep()
		// just logs a warning and returns (see Reporter.reportStep's null guard).
		Reporter reporter = new Reporter() {
			@Override
			public long takeSnap() {
				return 0L;
			}
		};

		WaitActions waitActions = new WaitActions(() -> driver, reporter);
		LocatorActions locatorActions = new LocatorActions(() -> driver, reporter);
		clickActions = new ClickActions(() -> driver, reporter, waitActions, locatorActions);
	}

	@Test
	public void clickActionsWorksStandaloneWithoutExtendingSeleniumBase() {
		driver.get("https://example.com");
		WebElement moreInfoLink = driver.findElement(By.cssSelector("a"));
		clickActions.click(moreInfoLink);
		Assert.assertNotEquals(driver.getCurrentUrl(), "https://example.com/",
				"Click should have navigated away from example.com");
	}

	@AfterClass
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}
}
