package com.framework.config.data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The retry policy for API-style tests is plain configuration: off by default, switched
 * on per run or per suite through the same priority chain every other key uses.
 */
public class RetryApiConfigTest {

	@Test
	public void apiTestsAreNotRetriedByDefaultButGeneralTestsStillAre() {
		ProjectConfig config = ProjectDirector.construct(new ConcurrentHashMap<>());

		Assert.assertEquals(config.getRetryApiLimit(), 0, "API tests must not be retried unless they ask for it");
		Assert.assertEquals(config.getRetryApiGroups(), "api,performance");
		Assert.assertTrue(config.getRetryLimit() > 0, "the general retry limit is untouched by this setting");
	}

	@Test
	public void suiteParametersOverrideTheApiRetryPolicy() {
		ConcurrentMap<String, String> params = new ConcurrentHashMap<>();
		params.put("retryApiLimit", "2");
		params.put("retryApiGroups", "api,contract");

		ProjectConfig config = ProjectDirector.construct(params);

		Assert.assertEquals(config.getRetryApiLimit(), 2);
		Assert.assertEquals(config.getRetryApiGroups(), "api,contract");
	}
}
