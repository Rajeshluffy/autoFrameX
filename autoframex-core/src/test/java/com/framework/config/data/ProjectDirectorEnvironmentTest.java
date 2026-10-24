package com.framework.config.data;

import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.framework.exception.FrameworkException;

/**
 * Environment routing. The workflows, the suite XMLs and the Dockerfile all say {@code env}, but only
 * {@code environment} used to be read, so those runs silently went to dev. An unknown name or a prod run
 * with no prodUrl used to warn and fall back to dev / QA.
 */
public class ProjectDirectorEnvironmentTest {

	private String savedEnvironment;
	private String savedEnv;

	@BeforeMethod
	public void clearSystemProperties() {
		savedEnvironment = System.clearProperty("environment");
		savedEnv = System.clearProperty("env");
	}

	@AfterMethod(alwaysRun = true)
	public void restoreSystemProperties() {
		restore("environment", savedEnvironment);
		restore("env", savedEnv);
	}

	private static void restore(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}

	private static void requireNoEnvironmentVariable() {
		String variable = System.getenv("ENVIRONMENT");
		if (variable != null && !variable.isBlank()) {
			throw new SkipException("ENVIRONMENT is set in this process; the default-name cases cannot run here");
		}
	}

	private static ConcurrentMap<String, String> params(String... keyValues) {
		ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	private static ProjectAppConfiguration config(String prodUrl) {
		return (ProjectAppConfiguration) Proxy.newProxyInstance(
				ProjectAppConfiguration.class.getClassLoader(), new Class<?>[] { ProjectAppConfiguration.class },
				(proxy, method, args) -> {
					switch (method.getName()) {
					case "devUrl": return "https://dev.example";
					case "qaUrl": return "https://qa.example";
					case "prodUrl": return prodUrl;
					default: return null;
					}
				});
	}

	// ----------------------------------------------------------------- the name

	@Test
	public void envIsAnAliasForEnvironmentAsASuiteParameter() {
		Assert.assertEquals(ProjectDirector.resolveEnvironmentName(params("env", "qa")), "qa");
	}

	@Test
	public void envIsAnAliasForEnvironmentAsASystemProperty() {
		requireNoEnvironmentVariable();
		System.setProperty("env", "prod");
		Assert.assertEquals(ProjectDirector.resolveEnvironmentName(params()), "prod");
	}

	@Test
	public void environmentWinsOverTheEnvAlias() {
		Assert.assertEquals(ProjectDirector.resolveEnvironmentName(params("environment", "prod", "env", "qa")), "prod");
	}

	@Test
	public void aSuiteParameterBeatsASystemProperty() {
		System.setProperty("env", "prod");
		Assert.assertEquals(ProjectDirector.resolveEnvironmentName(params("env", "qa")), "qa");
	}

	@Test
	public void anUnexpandedPlaceholderCountsAsNotSet() {
		requireNoEnvironmentVariable();
		// testng.xml has <parameter name="env" value="${env}"/>; with no -Denv TestNG leaves the text as is.
		Assert.assertEquals(ProjectDirector.resolveEnvironmentName(params("env", "${env}")), "dev");
		Assert.assertEquals(ProjectDirector.resolveEnvironmentName(params("env", "  ")), "dev");
	}

	@Test
	public void withNothingSetTheEnvironmentIsDev() {
		requireNoEnvironmentVariable();
		Assert.assertEquals(ProjectDirector.resolveEnvironmentName(params()), "dev");
	}

	// ---------------------------------------------------------------- the route

	@Test
	public void eachKnownNameRoutesToItsUrl() {
		ProjectAppConfiguration cfg = config("https://prod.example");
		Assert.assertEquals(ProjectDirector.routeUrl("dev", cfg), "https://dev.example");
		Assert.assertEquals(ProjectDirector.routeUrl("Development", cfg), "https://dev.example");
		Assert.assertEquals(ProjectDirector.routeUrl("qa", cfg), "https://qa.example");
		Assert.assertEquals(ProjectDirector.routeUrl("TEST", cfg), "https://qa.example");
		Assert.assertEquals(ProjectDirector.routeUrl("prod", cfg), "https://prod.example");
		Assert.assertEquals(ProjectDirector.routeUrl("Production", cfg), "https://prod.example");
	}

	@Test
	public void anUnknownEnvironmentThrowsInsteadOfFallingBackToDev() {
		FrameworkException e = Assert.expectThrows(FrameworkException.class,
				() -> ProjectDirector.routeUrl("staging", config("https://prod.example")));
		Assert.assertTrue(e.getMessage().contains("staging"), e.getMessage());
		Assert.assertTrue(e.getMessage().contains("qa") && e.getMessage().contains("prod"),
				"the message should list the supported names: " + e.getMessage());
	}

	@Test
	public void prodWithoutAProdUrlThrowsInsteadOfTestingQa() {
		Assert.assertThrows(FrameworkException.class, () -> ProjectDirector.routeUrl("prod", config(null)));
		Assert.assertThrows(FrameworkException.class, () -> ProjectDirector.routeUrl("production", config("")));
	}
}
