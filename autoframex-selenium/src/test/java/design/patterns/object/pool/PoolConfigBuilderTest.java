package design.patterns.object.pool;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Coverage for {@link PoolConfig.Builder}'s validation guards and defaults —
 * pure object construction, no driver/browser dependency, previously untested
 * despite backing every {@code maxPoolSize}/{@code borrowTimeoutSeconds}/etc.
 * value the pool trusts at runtime.
 */
public class PoolConfigBuilderTest {

	@Test
	public void defaultsMatchDocumentedValues() {
		PoolConfig config = new PoolConfig.Builder().build();
		Assert.assertEquals(config.getMaxPoolSize(), 5);
		Assert.assertEquals(config.getMinPoolSize(), 2);
		Assert.assertEquals(config.getMaxIdleMinutes(), 10);
		Assert.assertEquals(config.getBorrowTimeoutSeconds(), 30);
		Assert.assertEquals(config.getMaxReuseCount(), 75);
		Assert.assertTrue(config.isHealthCheckEnabled());
		Assert.assertTrue(config.isStateResetEnabled());
		Assert.assertTrue(config.isCloseAfterEach());
		Assert.assertTrue(config.getSupportedBrowsers().contains("CHROME"),
				"Builder() should default to supporting CHROME");
	}

	@Test
	public void minPoolSizeIsClampedToMaxPoolSizeOnBuild() {
		PoolConfig config = new PoolConfig.Builder()
				.maxPoolSize(2)
				.minPoolSize(10) // deliberately larger than max
				.build();
		Assert.assertEquals(config.getMinPoolSize(), 2,
				"minPoolSize must never exceed maxPoolSize after build() — a caller "
				+ "requesting more pre-warmed drivers than the hard cap allows should be "
				+ "silently clamped, not create an inconsistent config.");
	}

	@Test
	public void minPoolSizeBelowMaxIsUnaffectedByClamping() {
		PoolConfig config = new PoolConfig.Builder()
				.maxPoolSize(10)
				.minPoolSize(2)
				.build();
		Assert.assertEquals(config.getMinPoolSize(), 2);
		Assert.assertEquals(config.getMaxPoolSize(), 10);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void maxPoolSizeZeroIsRejected() {
		new PoolConfig.Builder().maxPoolSize(0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void maxPoolSizeNegativeIsRejected() {
		new PoolConfig.Builder().maxPoolSize(-1);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void minPoolSizeNegativeIsRejected() {
		new PoolConfig.Builder().minPoolSize(-1);
	}

	@Test
	public void minPoolSizeZeroIsAllowed() {
		// Zero pre-warmed drivers is a legitimate "lazy start" configuration —
		// used throughout this test suite's own marker-Browser tests.
		PoolConfig config = new PoolConfig.Builder().minPoolSize(0).build();
		Assert.assertEquals(config.getMinPoolSize(), 0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void borrowTimeoutSecondsZeroIsRejected() {
		new PoolConfig.Builder().borrowTimeoutSeconds(0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void maxReuseCountZeroIsRejected() {
		new PoolConfig.Builder().maxReuseCount(0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void maxIdleMinutesZeroIsRejected() {
		new PoolConfig.Builder().maxIdleMinutes(0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void pageLoadTimeoutSecondsZeroIsRejected() {
		new PoolConfig.Builder().pageLoadTimeoutSeconds(0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void scriptTimeoutSecondsZeroIsRejected() {
		new PoolConfig.Builder().scriptTimeoutSeconds(0);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void implicitWaitSecondsNegativeIsRejected() {
		new PoolConfig.Builder().implicitWaitSeconds(-1);
	}

	@Test
	public void implicitWaitSecondsZeroIsAllowed() {
		// Unlike the other timeouts, zero implicit wait is a legitimate
		// "explicit-waits-only" configuration, not a misconfiguration.
		PoolConfig config = new PoolConfig.Builder().implicitWaitSeconds(0).build();
		Assert.assertEquals(config.getImplicitWaitSeconds(), 0);
	}

	@Test
	public void clearSupportedBrowsersRemovesTheDefaultChromeEntry() {
		PoolConfig config = new PoolConfig.Builder()
				.clearSupportedBrowsers()
				.addSupportedBrowser("CUSTOM_BROWSER_ID")
				.build();
		Assert.assertEquals(config.getSupportedBrowsers().size(), 1);
		Assert.assertTrue(config.getSupportedBrowsers().contains("CUSTOM_BROWSER_ID"));
		Assert.assertFalse(config.getSupportedBrowsers().contains("CHROME"));
	}

	@Test
	public void supportedBrowsersSetIsUnmodifiableAfterBuild() {
		PoolConfig config = new PoolConfig.Builder().build();
		Assert.assertThrows(UnsupportedOperationException.class,
				() -> config.getSupportedBrowsers().add("SHOULD_NOT_BE_ADDABLE"));
	}

	@Test
	public void gridHubUrlDefaultsToEmptyStringNotNull() {
		PoolConfig config = new PoolConfig.Builder().build();
		Assert.assertEquals(config.getGridHubUrl(), "");
	}

	@Test
	public void gridHubUrlNullIsCoercedToEmptyString() {
		PoolConfig config = new PoolConfig.Builder().gridHubUrl(null).build();
		Assert.assertEquals(config.getGridHubUrl(), "",
				"A null gridHubUrl should be coerced to empty, not stored as literal null "
				+ "(callers format it directly into log/error strings).");
	}

	@Test
	public void toStringContainsKeyTuningValues() {
		PoolConfig config = new PoolConfig.Builder().maxPoolSize(7).minPoolSize(1).build();
		String s = config.toString();
		Assert.assertTrue(s.contains("max=7"), "toString() should surface maxPoolSize; got: " + s);
		Assert.assertTrue(s.contains("min=1"), "toString() should surface minPoolSize; got: " + s);
	}
}
