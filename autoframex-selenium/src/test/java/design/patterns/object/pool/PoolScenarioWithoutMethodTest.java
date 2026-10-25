package design.patterns.object.pool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * A Cucumber scenario has no {@code java.lang.reflect.Method}, so {@code CucumberProjectBase} called
 * {@code setupDriver(null, params, ...)} and {@code teardownDriver(null, ...)}. The pool read the test's name,
 * its {@code @BrowserConfig} and its URL from that method and threw {@code NullPointerException:
 * Cannot invoke "Method.getDeclaringClass()" because "method" is null} before it acquired anything, so no
 * scenario could get a browser through that path. A null method must mean "no annotations to read".
 */
public class PoolScenarioWithoutMethodTest {

	private static ConcurrentMap<String, String> params(String... keyValues) {
		ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	@Test
	public void aScenarioIsNamedAfterItsScenarioNameParameter() {
		Assert.assertEquals(DriverPoolManager.testNameOf(null, params("scenarioName", "Create a lead")), "Create a lead");
	}

	@Test
	public void aScenarioWithoutAnyNameIsNamedAfterItsThread() {
		String expected = "scenario@thread-" + Thread.currentThread().getId();
		Assert.assertEquals(DriverPoolManager.testNameOf(null, null), expected);
		Assert.assertEquals(DriverPoolManager.testNameOf(null, params("scenarioName", "  ")), expected);
	}

	@Test
	public void aTestNgMethodIsStillNamedClassDotMethod() throws NoSuchMethodException {
		Assert.assertEquals(DriverPoolManager.testNameOf(
				PoolScenarioWithoutMethodTest.class.getDeclaredMethod("aTestNgMethodIsStillNamedClassDotMethod"), null),
				"PoolScenarioWithoutMethodTest.aTestNgMethodIsStillNamedClassDotMethod");
	}

	@Test
	public void browserAndUrlResolveWithoutAMethod() {
		DriverPoolManager.bindContext("SCENARIO_WITHOUT_METHOD_TEST");
		try {
			DriverPoolManager manager = DriverPoolManager.getInstance();

			// from the scenario's own parameters: the first thing looked at, so it never reaches the annotations
			Assert.assertEquals(manager.determineUrl(null, params("url", "https://app.example/start")), "https://app.example/start");

			// with no parameters at all it must fall through to the environment and configuration, not throw
			Assert.assertNotNull(manager.determineBrowserId(null, params()), "a browser is chosen from the environment or config");
			manager.determineUrl(null, params());   // may be blank for a project with no URL, must not throw a NullPointerException
		} finally {
			DriverPoolManager.bindContext(null);
		}
	}

	@Test
	public void tearingDownWhenNothingWasSetUpIsHarmless() {
		DriverPoolManager.bindContext("SCENARIO_WITHOUT_METHOD_TEARDOWN");
		try {
			DriverPoolManager manager = DriverPoolManager.getInstance();
			Assert.assertFalse(manager.isPoolInitialized(), "a fresh context has no pool until initializePool() is called");
			manager.teardownDriver(null, true);   // no context, no driver: logs and returns
		} finally {
			DriverPoolManager.bindContext(null);
		}
	}
}
