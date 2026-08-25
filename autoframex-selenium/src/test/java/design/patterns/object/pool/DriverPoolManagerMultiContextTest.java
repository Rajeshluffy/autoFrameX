package design.patterns.object.pool;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Coverage for {@link DriverPoolManager}'s multi-context registry and its
 * pre-setup guard rails — the parts of its public API not already exercised
 * by {@link PoolCounterAccountingTest} (F1/F3) or
 * {@code WindowFrameActionsQuitBookkeepingTest} (F4).
 */
public class DriverPoolManagerMultiContextTest {

	@Test
	public void sameContextIdAlwaysResolvesToTheSameInstance() {
		DriverPoolManager.bindContext("MULTI_CTX_TEST_A");
		DriverPoolManager first = DriverPoolManager.getInstance();
		DriverPoolManager second = DriverPoolManager.getInstance();
		Assert.assertSame(first, second,
				"Two getInstance() calls on the same thread, same bound context, must return the same instance.");
	}

	@Test
	public void differentContextIdsResolveToDifferentInstances() {
		DriverPoolManager.bindContext("MULTI_CTX_TEST_B1");
		DriverPoolManager forB1 = DriverPoolManager.getInstance();

		DriverPoolManager.bindContext("MULTI_CTX_TEST_B2");
		DriverPoolManager forB2 = DriverPoolManager.getInstance();

		Assert.assertNotSame(forB1, forB2,
				"Two distinct contextIds must resolve to two distinct DriverPoolManager instances — "
				+ "this is the multi-application isolation the registry-of-singletons design exists for.");
	}

	@Test
	public void nullContextIdFallsBackToDefaultContext() {
		DriverPoolManager.bindContext("MULTI_CTX_TEST_C");
		DriverPoolManager explicit = DriverPoolManager.getInstance();

		DriverPoolManager.bindContext(null);
		DriverPoolManager afterNullBind = DriverPoolManager.getInstance();

		Assert.assertNotSame(explicit, afterNullBind,
				"bindContext(null) should fall back to the shared default context, not silently "
				+ "keep the previous explicit context bound.");
	}

	@Test
	public void blankContextIdFallsBackToDefaultContext() {
		DriverPoolManager.bindContext(""); // blank, not null
		DriverPoolManager fromBlank = DriverPoolManager.getInstance();

		DriverPoolManager.bindContext(null);
		DriverPoolManager fromNull = DriverPoolManager.getInstance();

		Assert.assertSame(fromBlank, fromNull,
				"An explicitly blank contextId should resolve the same default context as never binding at all.");
	}

	@Test
	public void getDriverBeforeSetupThrowsIllegalStateException() {
		DriverPoolManager.bindContext("MULTI_CTX_TEST_D");
		DriverPoolManager manager = DriverPoolManager.getInstance();
		Assert.assertThrows(IllegalStateException.class, manager::getDriver);
	}

	@Test
	public void getWaitBeforeSetupThrowsIllegalStateException() {
		DriverPoolManager.bindContext("MULTI_CTX_TEST_E");
		DriverPoolManager manager = DriverPoolManager.getInstance();
		Assert.assertThrows(IllegalStateException.class, manager::getWait);
	}

	@Test
	public void poolStatisticsBeforeInitializationSaysSo() {
		DriverPoolManager.bindContext("MULTI_CTX_TEST_F");
		DriverPoolManager manager = DriverPoolManager.getInstance();
		Assert.assertEquals(manager.getPoolStatistics(), "Pool not initialized");
	}

	@Test
	public void shutdownPoolBeforeInitializationIsANoOpNotAnException() {
		DriverPoolManager.bindContext("MULTI_CTX_TEST_G");
		DriverPoolManager manager = DriverPoolManager.getInstance();
		manager.shutdownPool(); // must not throw
		Assert.assertEquals(manager.getPoolStatistics(), "Pool not initialized");
	}
}
