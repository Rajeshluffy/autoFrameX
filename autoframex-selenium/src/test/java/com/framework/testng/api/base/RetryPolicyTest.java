package com.framework.testng.api.base;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * API-style tests must not be retried unless they ask for it: a retry can repeat a call that
 * is not safe to repeat (create, pay, delete). The rules live in two pure functions so they
 * can be checked without running a failing test.
 */
public class RetryPolicyTest {

	@Test
	public void testsInAnApiGroupAreNotRetriedByDefaultWhileOthersKeepTheGlobalLimit() {
		int globalLimit = 4;
		int apiLimit = 0;

		Assert.assertEquals(RetryEngine.limitFor(null, null, true, apiLimit, globalLimit), 0, "API test: no retry");
		Assert.assertEquals(RetryEngine.limitFor(null, null, false, apiLimit, globalLimit), 4, "UI test: unchanged");
	}

	@Test
	public void anExplicitRetryAnnotationAlwaysWinsMethodBeforeClass() {
		Assert.assertEquals(RetryEngine.limitFor(2, null, true, 0, 4), 2, "an API test opts in with @Retry(limit = 2)");
		Assert.assertEquals(RetryEngine.limitFor(null, 3, true, 0, 4), 3, "class-level @Retry also opts in");
		Assert.assertEquals(RetryEngine.limitFor(1, 3, true, 0, 4), 1, "method beats class");
		Assert.assertEquals(RetryEngine.limitFor(0, null, false, 0, 4), 0, "@Retry(limit = 0) switches retries off for a UI test");
	}

	@Test
	public void theApiLimitCanBeRaisedAndNegativeValuesMeanZero() {
		Assert.assertEquals(RetryEngine.limitFor(null, null, true, 1, 4), 1);
		Assert.assertEquals(RetryEngine.limitFor(-5, null, false, 0, 4), 0);
		Assert.assertEquals(RetryEngine.limitFor(null, null, false, 0, -1), 0);
	}

	@Test
	public void apiGroupsAreMatchedCaseInsensitivelyAgainstTheConfiguredList() {
		String configured = "api, performance";
		Assert.assertTrue(RetryEngine.isApiTest(new String[] {"api"}, configured));
		Assert.assertTrue(RetryEngine.isApiTest(new String[] {"smoke", "Performance"}, configured), "any one group is enough, case ignored");
		Assert.assertFalse(RetryEngine.isApiTest(new String[] {"smoke", "regression"}, configured));
		Assert.assertFalse(RetryEngine.isApiTest(new String[] {}, configured), "no groups: an ordinary test");
		Assert.assertFalse(RetryEngine.isApiTest(null, configured));
		Assert.assertFalse(RetryEngine.isApiTest(new String[] {"api"}, ""), "an empty list marks nothing as API");
		Assert.assertFalse(RetryEngine.isApiTest(new String[] {"api"}, null));
		Assert.assertFalse(RetryEngine.isApiTest(new String[] {"apiary"}, "api"), "whole-name match, not a prefix");
	}
}
