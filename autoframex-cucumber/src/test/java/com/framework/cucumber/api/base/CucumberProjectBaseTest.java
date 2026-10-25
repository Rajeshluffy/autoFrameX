package com.framework.cucumber.api.base;

import java.net.URI;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The capture folder for a Cucumber scenario is named after its feature file, so the name has to be
 * derived reliably from the URI Cucumber hands to the hooks.
 */
public class CucumberProjectBaseTest {

	@Test
	public void featureNameIsTheFileNameWithoutItsExtension() {
		Assert.assertEquals(CucumberProjectBase.featureNameOf(URI.create("file:///C:/proj/features/login.feature")), "login");
		Assert.assertEquals(CucumberProjectBase.featureNameOf(URI.create("classpath:features/Create%20Lead.feature")), "Create%20Lead");
		Assert.assertEquals(CucumberProjectBase.featureNameOf(URI.create("classpath:login.feature")), "login");
		Assert.assertEquals(CucumberProjectBase.featureNameOf(URI.create("file:///x/y/orders.feature?line=12")), "orders");
	}

	/** Session-less fake that records what it was asked to open, and can refuse. */
	private static final class RecordingDriver extends org.openqa.selenium.remote.RemoteWebDriver {
		final java.util.List<String> opened = new java.util.ArrayList<>();
		final boolean refuse;

		RecordingDriver(boolean refuse) {
			super();
			this.refuse = refuse;
		}

		@Override
		public void get(String url) {
			if (refuse) {
				throw new org.openqa.selenium.WebDriverException("net::ERR_CONNECTION_REFUSED");
			}
			opened.add(url);
		}
	}

	@Test
	public void theApplicationUrlIsOpenedWhenThereIsOne() {
		RecordingDriver driver = new RecordingDriver(false);
		CucumberProjectBase.openAppUrl(driver, "https://app.example/login", "Login works");
		Assert.assertEquals(driver.opened, java.util.List.of("https://app.example/login"));
	}

	@Test
	public void aBlankUrlMeansThereIsNothingToOpen() {
		RecordingDriver driver = new RecordingDriver(true);   // would fail if it were touched
		CucumberProjectBase.openAppUrl(driver, null, "s");
		CucumberProjectBase.openAppUrl(driver, "  ", "s");
		Assert.assertTrue(driver.opened.isEmpty());
	}

	@Test
	public void anUnreachableApplicationFailsTheScenarioInsteadOfContinuingOnABlankPage() {
		com.framework.exception.FrameworkException e = Assert.expectThrows(com.framework.exception.FrameworkException.class,
				() -> CucumberProjectBase.openAppUrl(new RecordingDriver(true), "https://down.example", "Create a lead"));
		Assert.assertTrue(e.getMessage().contains("Create a lead") && e.getMessage().contains("https://down.example"), e.getMessage());
		Assert.assertTrue(e.getMessage().contains("ERR_CONNECTION_REFUSED"), "the browser's reason is kept: " + e.getMessage());
	}

	@Test
	public void unusualOrMissingUrisFallBackToAUsableName() {
		Assert.assertEquals(CucumberProjectBase.featureNameOf(null), "UnnamedFeature");
		Assert.assertEquals(CucumberProjectBase.featureNameOf(URI.create("file:///x/y/")), "UnnamedFeature");
		Assert.assertEquals(CucumberProjectBase.featureNameOf(URI.create("file:///x/y/notes.txt")), "notes.txt",
				"anything that is not a .feature file keeps its name");
	}
}
