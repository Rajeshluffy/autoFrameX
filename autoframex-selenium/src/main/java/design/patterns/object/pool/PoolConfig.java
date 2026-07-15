package design.patterns.object.pool;

// ============================================================================

//POOL CONFIGURATION - BUILDER PATTERN
//============================================================================

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
	private final Set<String> supportedBrowsers;
	private final int pageLoadTimeoutSeconds;
	private final int scriptTimeoutSeconds;
	private final int implicitWaitSeconds;
	private final String gridHubUrl;

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
		this.pageLoadTimeoutSeconds = builder.pageLoadTimeoutSeconds;
		this.scriptTimeoutSeconds = builder.scriptTimeoutSeconds;
		this.implicitWaitSeconds = builder.implicitWaitSeconds;
		this.gridHubUrl = builder.gridHubUrl;
	}

	public int getMaxPoolSize() {
		return maxPoolSize;
	}

	public int getMinPoolSize() {
		return minPoolSize;
	}

	public int getMaxIdleMinutes() {
		return maxIdleMinutes;
	}

	public int getBorrowTimeoutSeconds() {
		return borrowTimeoutSeconds;
	}

	public int getMaxReuseCount() {
		return maxReuseCount;
	}

	public boolean isHealthCheckEnabled() {
		return healthCheckEnabled;
	}

	public boolean isStateResetEnabled() {
		return stateResetEnabled;
	}

	/**
	 * When {@code true} browser windows are closed after every {@code @Test}
	 * (driver session kept alive for reuse). When {@code false} healthy drivers
	 * are returned to the pool without closing the window.
	 */
	public boolean isCloseAfterEach() {
		return closeAfterEach;
	}

	public Set<String> getSupportedBrowsers() {
		return supportedBrowsers;
	}

	/** Selenium Grid hub URL, used by {@code GRID_*} browser providers. Empty string if not configured. */
	public String getGridHubUrl() {
		return gridHubUrl;
	}

	/** Seconds before a page load times out — applied to every newly-created driver. */
	public int getPageLoadTimeoutSeconds() {
		return pageLoadTimeoutSeconds;
	}

	/** Seconds before an async script execution times out. */
	public int getScriptTimeoutSeconds() {
		return scriptTimeoutSeconds;
	}

	/** Seconds of implicit wait applied to every newly-created driver. */
	public int getImplicitWaitSeconds() {
		return implicitWaitSeconds;
	}

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
	 *
	 * <p>Field defaults below are plain constants, not read from {@code ConfigManager} —
	 * {@code DriverPoolManager.loadConfiguration()} (the only caller in this codebase)
	 * always overrides every one of them explicitly, so reaching into config here was
	 * dead coupling: {@code design.patterns.*} (meant to be generic, framework-agnostic
	 * infrastructure) importing {@code com.framework.config.data} for values nothing
	 * ever actually used. These constants mirror {@code frameworkConfig.properties}'
	 * own defaults purely so a caller who builds a {@code PoolConfig} directly (bypassing
	 * {@code DriverPoolManager}) still gets sane values.
	 */
	public static class Builder {
		private static final int DEFAULT_MAX_POOL_SIZE = 5;
		private static final int DEFAULT_MIN_POOL_SIZE = 2;
		private static final int DEFAULT_MAX_IDLE_MINUTES = 10;
		private static final int DEFAULT_BORROW_TIMEOUT_SECONDS = 30;
		private static final int DEFAULT_MAX_REUSE_COUNT = 75;
		private static final int DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS = 30;
		private static final int DEFAULT_SCRIPT_TIMEOUT_SECONDS = 30;
		private static final int DEFAULT_IMPLICIT_WAIT_SECONDS = 10;

		private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;
		private int minPoolSize = DEFAULT_MIN_POOL_SIZE;
		private int maxIdleMinutes = DEFAULT_MAX_IDLE_MINUTES;
		private int borrowTimeoutSeconds = DEFAULT_BORROW_TIMEOUT_SECONDS;
		private int maxReuseCount = DEFAULT_MAX_REUSE_COUNT;
		private boolean healthCheckEnabled = true;
		private boolean stateResetEnabled = true;
		private boolean closeAfterEach = true;
		private Set<String> supportedBrowsers = new HashSet<>();
		private int pageLoadTimeoutSeconds = DEFAULT_PAGE_LOAD_TIMEOUT_SECONDS;
		private int scriptTimeoutSeconds = DEFAULT_SCRIPT_TIMEOUT_SECONDS;
		private int implicitWaitSeconds = DEFAULT_IMPLICIT_WAIT_SECONDS;
		private String gridHubUrl = "";

		public Builder() {
			supportedBrowsers.add("CHROME");
		}

		/** Selenium Grid hub URL, threaded into {@code GRID_*} browser providers. */
		public Builder gridHubUrl(String gridHubUrl) {
			this.gridHubUrl = gridHubUrl != null ? gridHubUrl : "";
			return this;
		}

		/** Seconds before a page load times out — applied to every newly-created driver. */
		public Builder pageLoadTimeoutSeconds(int pageLoadTimeoutSeconds) {
			if (pageLoadTimeoutSeconds <= 0)
				throw new IllegalArgumentException("pageLoadTimeoutSeconds must be positive");
			this.pageLoadTimeoutSeconds = pageLoadTimeoutSeconds;
			return this;
		}

		/** Seconds before an async script execution times out. */
		public Builder scriptTimeoutSeconds(int scriptTimeoutSeconds) {
			if (scriptTimeoutSeconds <= 0)
				throw new IllegalArgumentException("scriptTimeoutSeconds must be positive");
			this.scriptTimeoutSeconds = scriptTimeoutSeconds;
			return this;
		}

		/** Seconds of implicit wait applied to every newly-created driver. */
		public Builder implicitWaitSeconds(int implicitWaitSeconds) {
			if (implicitWaitSeconds < 0)
				throw new IllegalArgumentException("implicitWaitSeconds must be >= 0");
			this.implicitWaitSeconds = implicitWaitSeconds;
			return this;
		}

		/**
		 * Sets maximum number of drivers per browser type.
		 * 
		 * @param maxPoolSize maximum pool size (must be > 0)
		 * @return this builder
		 * @throws IllegalArgumentException if size <= 0
		 */
		public Builder maxPoolSize(int maxPoolSize) {
			if (maxPoolSize <= 0)
				throw new IllegalArgumentException("maxPoolSize must be positive");
			this.maxPoolSize = maxPoolSize;
			return this;
		}

		/** Minimum drivers pre-warmed at startup per browser type. */
		public Builder minPoolSize(int minPoolSize) {
			if (minPoolSize < 0)
				throw new IllegalArgumentException("minPoolSize must be >= 0");
			this.minPoolSize = minPoolSize;
			return this;
		}

		/**
		 * Seconds a borrow blocks before throwing {@code DriverAcquisitionException}.
		 */
		public Builder borrowTimeoutSeconds(int borrowTimeoutSeconds) {
			if (borrowTimeoutSeconds <= 0)
				throw new IllegalArgumentException("borrowTimeoutSeconds must be positive");
			this.borrowTimeoutSeconds = borrowTimeoutSeconds;
			return this;
		}

		/**
		 * Hard cap on uses per driver before it is retired (prevents memory/fd leaks).
		 */
		public Builder maxReuseCount(int maxReuseCount) {
			if (maxReuseCount <= 0)
				throw new IllegalArgumentException("maxReuseCount must be positive");
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
		 * Adds a supported browser id (a {@link design.patterns.factory.browser.BrowserRegistry}
		 * key — built-in ids match {@code BrowserType.name()}, e.g. {@code "CHROME"}).
		 *
		 * @param browserId browser id to support
		 * @return this builder
		 */
		public Builder addSupportedBrowser(String browserId) {
			this.supportedBrowsers.add(browserId);
			return this;
		}

		/** Compat overload for a caller holding a {@link BrowserType} constant. */
		public Builder addSupportedBrowser(BrowserType browserType) {
			return addSupportedBrowser(browserType.name());
		}

		/**
		 * Clears all supported browsers.
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
			// Ensure minPoolSize does not exceed maxPoolSize
			if (this.minPoolSize > this.maxPoolSize) {
				this.minPoolSize = this.maxPoolSize;
			}
			return new PoolConfig(this);
		}

		
	}
}