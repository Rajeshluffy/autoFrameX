package com.framework.testng.api.base;

import java.util.Map;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Two things {@code ProjectSpecificMethods} did for every UI test with no way to see or change them:
 * <ul>
 *   <li>it recorded video for every test (a screenshot twice a second, kept only if the test failed), with no
 *       switch to turn that off for a fast CI run;</li>
 *   <li>its {@code fetchData} DataProvider is {@code parallel = true}, and TestNG runs it on 10 threads unless the
 *       suite says otherwise, while the browser pool holds 5 browsers and makes a thread wait only 30 seconds for
 *       one: with one-minute UI tests, rows 6 to 10 failed with "Cannot acquire driver", and nothing named the
 *       cause.</li>
 * </ul>
 */
public class ProjectSpecificMethodsSettingsTest {

	@BeforeMethod
	public void requireDefaults() {
		if (System.getenv("VIDEO_ENABLED") != null && !System.getenv("VIDEO_ENABLED").isBlank()) {
			throw new SkipException("VIDEO_ENABLED is set in this process");
		}
		System.clearProperty("video.enabled");
	}

	@AfterMethod(alwaysRun = true)
	public void clearProperty() {
		System.clearProperty("video.enabled");
	}

	@Test
	public void videoIsOnByDefaultSoExistingSuitesKeepTheirFailureVideos() {
		Assert.assertTrue(ProjectSpecificMethods.videoEnabled(null));
	}

	@Test
	public void videoCanBeSwitchedOffWithASystemProperty() {
		System.setProperty("video.enabled", "false");

		Assert.assertFalse(ProjectSpecificMethods.videoEnabled(null));
	}

	@Test
	public void aSuiteParameterBeatsTheSystemProperty() {
		System.setProperty("video.enabled", "true");

		Assert.assertFalse(ProjectSpecificMethods.videoEnabled(Map.of("video.enabled", "false")));
	}

	@Test
	public void moreProviderThreadsThanBrowsersIsCalledOutWithBothNumbers() {
		String warning = ProjectSpecificMethods.dataProviderOversubscription(10, 5);

		Assert.assertNotNull(warning);
		Assert.assertTrue(warning.contains("10") && warning.contains("5"), warning);
		Assert.assertTrue(warning.contains("data-provider-thread-count"), "it says which setting to change: " + warning);
	}

	@Test
	public void noWarningWhenTheThreadsFitThePool() {
		Assert.assertNull(ProjectSpecificMethods.dataProviderOversubscription(5, 5));
		Assert.assertNull(ProjectSpecificMethods.dataProviderOversubscription(3, 5));
	}
}
