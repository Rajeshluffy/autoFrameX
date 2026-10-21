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

	@Test
	public void unusualOrMissingUrisFallBackToAUsableName() {
		Assert.assertEquals(CucumberProjectBase.featureNameOf(null), "UnnamedFeature");
		Assert.assertEquals(CucumberProjectBase.featureNameOf(URI.create("file:///x/y/")), "UnnamedFeature");
		Assert.assertEquals(CucumberProjectBase.featureNameOf(URI.create("file:///x/y/notes.txt")), "notes.txt",
				"anything that is not a .feature file keeps its name");
	}
}
