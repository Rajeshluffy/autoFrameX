package com.framework.config.data;

import java.util.Map;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The environment name and "is this production" were decided in three places: the project configuration (which
 * understood both {@code environment} and its alias {@code env}), the browser safety policy (which understood only
 * {@code environment}) and, for the ZAP scans, nowhere. One implementation here, used by all of them.
 */
public class ConfigResolverEnvironmentTest {

	@BeforeMethod
	public void requireDefaults() {
		if (System.getenv("ENVIRONMENT") != null && !System.getenv("ENVIRONMENT").isBlank()) {
			throw new SkipException("ENVIRONMENT is set in this process");
		}
		System.clearProperty("environment");
		System.clearProperty("env");
	}

	@AfterMethod(alwaysRun = true)
	public void clearProperties() {
		System.clearProperty("environment");
		System.clearProperty("env");
	}

	@Test
	public void productionIsProdOrProductionInAnyCaseAndNothingElse() {
		Assert.assertTrue(ConfigResolver.isProduction("prod"));
		Assert.assertTrue(ConfigResolver.isProduction(" Production "));
		Assert.assertTrue(ConfigResolver.isProduction("PROD"));
		Assert.assertFalse(ConfigResolver.isProduction("qa"));
		Assert.assertFalse(ConfigResolver.isProduction("preprod"));
		Assert.assertFalse(ConfigResolver.isProduction("dev"));
		Assert.assertFalse(ConfigResolver.isProduction(""));
		Assert.assertFalse(ConfigResolver.isProduction(null));
	}

	@Test
	public void theEnvironmentIsDevWhenNothingSaysOtherwise() {
		Assert.assertEquals(ConfigResolver.resolveEnvironment(null), "dev");
	}

	@Test
	public void bothSpellingsAreUnderstoodInEveryTier() {
		Assert.assertEquals(ConfigResolver.resolveEnvironment(Map.of("env", "qa")), "qa");
		Assert.assertEquals(ConfigResolver.resolveEnvironment(Map.of("environment", "prod")), "prod");

		System.setProperty("env", "production");
		Assert.assertEquals(ConfigResolver.resolveEnvironment(null), "production");
	}

	@Test
	public void aParameterBeatsASystemPropertyAndAnUnexpandedPlaceholderIsIgnored() {
		System.setProperty("env", "prod");

		Assert.assertEquals(ConfigResolver.resolveEnvironment(Map.of("env", "qa")), "qa");
		Assert.assertEquals(ConfigResolver.resolveEnvironment(Map.of("env", "${env}")), "prod",
				"a suite-XML placeholder nobody filled in is not a value");
	}
}
