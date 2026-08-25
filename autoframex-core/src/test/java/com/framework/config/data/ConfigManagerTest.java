package com.framework.config.data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Coverage for {@link ConfigManager}'s multi-context registry and
 * {@code resolveContextId}'s priority chain — mirrors
 * {@code design.patterns.object.pool.DriverPoolManagerMultiContextTest}'s
 * approach for the same pattern one module over.
 */
public class ConfigManagerTest {

	@Test
	public void sameContextIdAlwaysResolvesToTheSameInstance() {
		ConfigManager.bindContext("CM_TEST_A");
		Assert.assertSame(ConfigManager.getInstance(), ConfigManager.getInstance());
	}

	@Test
	public void differentContextIdsResolveToDifferentInstances() {
		ConfigManager.bindContext("CM_TEST_B1");
		ConfigManager forB1 = ConfigManager.getInstance();

		ConfigManager.bindContext("CM_TEST_B2");
		ConfigManager forB2 = ConfigManager.getInstance();

		Assert.assertNotSame(forB1, forB2);
	}

	@Test
	public void nullContextIdFallsBackToDefaultContext() {
		ConfigManager.bindContext("CM_TEST_C");
		ConfigManager explicit = ConfigManager.getInstance();

		ConfigManager.bindContext(null);
		ConfigManager afterNullBind = ConfigManager.getInstance();

		Assert.assertNotSame(explicit, afterNullBind);
	}

	@Test
	public void freshContextIsNotInitializedUntilTouched() {
		ConfigManager.bindContext("CM_TEST_D");
		Assert.assertFalse(ConfigManager.getInstance().isInitialized());
	}

	@Test
	public void getConfigAutoInitializesWithDefaultsWhenNeverExplicitlyInitialized() {
		ConfigManager.bindContext("CM_TEST_E");
		ConfigManager manager = ConfigManager.getInstance();
		Assert.assertNotNull(manager.getConfig(),
				"getConfig() should auto-initialize with defaults rather than returning null / throwing.");
		Assert.assertTrue(manager.isInitialized());
	}

	@Test
	public void initializeConfigIsIdempotent() {
		ConfigManager.bindContext("CM_TEST_F");
		ConfigManager manager = ConfigManager.getInstance();
		manager.initializeConfig();
		ProjectConfig first = manager.getConfig();
		manager.initializeConfig(); // second call — should skip, not rebuild
		Assert.assertSame(manager.getConfig(), first,
				"A second initializeConfig() call should be a no-op (guarded by the initialized flag), "
				+ "not silently rebuild and replace the config object.");
	}

	@Test
	public void resetClearsInitializedStateAndOverrides() {
		ConfigManager.bindContext("CM_TEST_G");
		ConfigManager manager = ConfigManager.getInstance();
		ConcurrentMap<String, String> params = new ConcurrentHashMap<>();
		params.put("someKey", "someValue");
		manager.initializeConfig(params);
		Assert.assertTrue(manager.isInitialized());
		Assert.assertEquals(manager.getRuntimeOverride("someKey"), "someValue");

		manager.reset();

		Assert.assertFalse(manager.isInitialized());
		Assert.assertNull(manager.getRuntimeOverride("someKey"),
				"reset() should clear runtimeOverrides, not just the initialized flag.");
	}

	@Test
	public void updateConfigRejectsNull() {
		ConfigManager.bindContext("CM_TEST_H");
		Assert.assertThrows(IllegalArgumentException.class,
				() -> ConfigManager.getInstance().updateConfig(null));
	}

	// =========================================================================
	// resolveContextId priority chain
	// =========================================================================

	@Test
	public void resolveContextIdPrefersExplicitContextIdParam() {
		Map<String, String> params = new HashMap<>();
		params.put("contextId", "explicitCtx");
		params.put("configClass", "some.other.Config");
		Assert.assertEquals(ConfigManager.resolveContextId(params), "explicitCtx");
	}

	@Test
	public void resolveContextIdFallsBackToConfigClassParam() {
		Map<String, String> params = new HashMap<>();
		params.put("configClass", "com.example.MyConfig");
		Assert.assertEquals(ConfigManager.resolveContextId(params), "com.example.MyConfig");
	}

	@Test
	public void resolveContextIdFallsBackToDefaultWhenNothingResolves() {
		Assert.assertEquals(ConfigManager.resolveContextId(null), "default");
		Assert.assertEquals(ConfigManager.resolveContextId(new HashMap<>()), "default");
	}

	@Test
	public void resolveContextIdIgnoresBlankExplicitContextId() {
		Map<String, String> params = new HashMap<>();
		params.put("contextId", "   ");
		params.put("configClass", "com.example.MyConfig");
		Assert.assertEquals(ConfigManager.resolveContextId(params), "com.example.MyConfig",
				"A blank contextId param should fall through to configClass, not win as an empty string.");
	}
}
