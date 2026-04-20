package design.patterns.object.pool;

// ============================================================================

//POOL CONFIGURATION - BUILDER PATTERN
//============================================================================

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.framework.config.data.ConfigManager;

import design.patterns.factory.browser.BrowserType;

/**
 * Configuration for WebDriver pool behavior.
 * Implements Builder pattern for flexible construction.
 * 
 * @author Framework Team
 * @version 2.0
 */
public class PoolConfig {

	private final int maxPoolSize;
	private final int minPoolSize;
	private final int maxIdleMinutes;
	private final int borrowTimeoutSeconds;
	private final int maxReuseCount;
	private final boolean healthCheckEnabled;
	private final boolean stateResetEnabled;
	private final boolean closeAfterEach;
	private final Set<BrowserType> supportedBrowsers;

	private PoolConfig(Builder builder) {
		this.maxPoolSize = builder.maxPoolSize;
		this.minPoolSize = builder.minPoolSize;
		this.maxIdleMinutes = builder.maxIdleMinutes;
		this.borrowTimeoutSeconds = builder.borrowTimeoutSeconds;
		this.maxReuseCount = builder.maxReuseCount;
		this.healthCheckEnabled = builder.healthCheckEnabled;
		this.stateResetEnabled = builder.stateResetEnabled;
		this.closeAfterEach = builder.closeAfterEach;
		this.supportedBrowsers = Collections.unmodifiableSet(builder.supportedBrowsers);
	}

	public int getMaxPoolSize() { return maxPoolSize; }
	public int getMinPoolSize() { return minPoolSize; }
	public int getMaxIdleMinutes() { return maxIdleMinutes; }
	public int getBorrowTimeoutSeconds() { return borrowTimeoutSeconds; }
	public int getMaxReuseCount() { return maxReuseCount; }
	public boolean isHealthCheckEnabled() { return healthCheckEnabled; }
	public boolean isStateResetEnabled() { return stateResetEnabled; }

	/**
	 * When {@code true} browser windows are closed after every {@code @Test}
	 * (driver session kept alive for reuse). When {@code false} healthy drivers
	 * are returned to the pool without closing the window.
	 */
	public boolean isCloseAfterEach() { return closeAfterEach; }

	public Set<BrowserType> getSupportedBrowsers() { return supportedBrowsers; }

	@Override
	public String toString() {
		return String.format(
				"PoolConfig{min=%d, max=%d, maxIdle=%dmin, borrowTimeout=%ds, " +
				"maxReuse=%d, healthCheck=%s, stateReset=%s, closeAfterEach=%s}",
				minPoolSize, maxPoolSize, maxIdleMinutes, borrowTimeoutSeconds,
				maxReuseCount, healthCheckEnabled, stateResetEnabled, closeAfterEach);
	}

	/**
	 * Builder for PoolConfig following Builder pattern.
	 */
	public static class Builder {
		private int maxPoolSize = ConfigManager.getInstance().getConfig().getPoolMaxSize();
		private int minPoolSize = ConfigManager.getInstance().getConfig().getPoolMinSize();
		private int maxIdleMinutes = ConfigManager.getInstance().getConfig().getPoolMaxIdleMinutes();
		private int borrowTimeoutSeconds = ConfigManager.getInstance().getConfig().getPoolBorrowTimeoutSeconds();
		private int maxReuseCount = ConfigManager.getInstance().getConfig().getPoolMaxReuseCount();
		private boolean healthCheckEnabled = true;
		private boolean stateResetEnabled = true;
		private boolean closeAfterEach = true;
		private Set<BrowserType> supportedBrowsers = new HashSet<>();

		public Builder() {
			supportedBrowsers.add(BrowserType.CHROME);
			// Removed automatic FIREFOX addition to stop eager invalid pre-warming
		}

		/**
		 * Sets maximum number of drivers per browser type.
		 * 
		 * @param maxPoolSize maximum pool size (must be > 0)
		 * @return this builder
		 * @throws IllegalArgumentException if size <= 0
		 */
		public Builder maxPoolSize(int maxPoolSize) {
			if (maxPoolSize <= 0) throw new IllegalArgumentException("maxPoolSize must be positive");
			this.maxPoolSize = maxPoolSize;
			return this;
		}

		/** Minimum drivers pre-warmed at startup per browser type. */
		public Builder minPoolSize(int minPoolSize) {
			if (minPoolSize < 0) throw new IllegalArgumentException("minPoolSize must be >= 0");
			this.minPoolSize = minPoolSize;
			return this;
		}

		/** Seconds a borrow blocks before throwing {@code DriverAcquisitionException}. */
		public Builder borrowTimeoutSeconds(int borrowTimeoutSeconds) {
			if (borrowTimeoutSeconds <= 0) throw new IllegalArgumentException("borrowTimeoutSeconds must be positive");
			this.borrowTimeoutSeconds = borrowTimeoutSeconds;
			return this;
		}

		/** Hard cap on uses per driver before it is retired (prevents memory/fd leaks). */
		public Builder maxReuseCount(int maxReuseCount) {
			if (maxReuseCount <= 0) throw new IllegalArgumentException("maxReuseCount must be positive");
			this.maxReuseCount = maxReuseCount;
			return this;
		}

		/**
		 * Sets maximum idle time before driver eviction.
		 * 
		 * @param maxIdleMinutes maximum idle minutes (must be > 0)
		 * @return this builder
		 * @throws IllegalArgumentException if minutes <= 0
		 */
		public Builder maxIdleMinutes(int maxIdleMinutes) {
			if (maxIdleMinutes <= 0) {
				throw new IllegalArgumentException("maxIdleMinutes must be positive");
			}
			this.maxIdleMinutes = maxIdleMinutes;
			return this;
		}

		/**
		 * Enables/disables health checks before driver reuse.
		 * 
		 * @param enabled true to enable health checks
		 * @return this builder
		 */
		public Builder healthCheckEnabled(boolean enabled) {
			this.healthCheckEnabled = enabled;
			return this;
		}

		/**
		 * Enables/disables state reset when returning drivers to pool.
		 *
		 * @param enabled true to enable state reset
		 * @return this builder
		 */
		public Builder stateResetEnabled(boolean enabled) {
			this.stateResetEnabled = enabled;
			return this;
		}

		/**
		 * Controls whether the browser is fully quit after every {@code @Test}.
		 * Defaults to {@code true} (always close — maximum isolation).
		 * Set to {@code false} to return healthy drivers to the pool for reuse.
		 *
		 * @param closeAfterEach true to quit browser after each test
		 * @return this builder
		 */
		public Builder closeAfterEach(boolean closeAfterEach) {
			this.closeAfterEach = closeAfterEach;
			return this;
		}

		/**
		 * Adds a supported browser type.
		 *
		 * @param browserType browser type to support
		 * @return this builder
		 */
		public Builder addSupportedBrowser(BrowserType browserType) {
			this.supportedBrowsers.add(browserType);
			return this;
		}

		/**
		 * Removes all browser types from the supported set.
		 *
		 * <p>Use together with {@link #addSupportedBrowser} to replace the
		 * constructor defaults (CHROME + FIREFOX) with only the browser
		 * actually configured for this suite run — avoids launching browser
		 * processes that no test will ever use.
		 *
		 * @return this builder
		 */
		public Builder clearSupportedBrowsers() {
			this.supportedBrowsers.clear();
			return this;
		}

		/**
		 * Builds the immutable PoolConfig.
		 * 
		 * @return configured PoolConfig instance
		 */
		public PoolConfig build() {
			return new PoolConfig(this);
		}
	}
}