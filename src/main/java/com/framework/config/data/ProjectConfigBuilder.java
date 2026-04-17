package com.framework.config.data;

public class ProjectConfigBuilder {

	private String browserName;
	private boolean remote;
	private short implicit;
	private short explicit;
	private short shortWait;
	private int pollingIntervalMs;
	private int defaultWaitTimeout;
	private short retryLimit;
	private int elementMaxRetryAttempts;
	private int poolMaxSize;
	private int poolMaxIdleMinutes;
	private String serverUrl;
	private String devUrl;
	private String qaUrl;
	private String appUrl;
	private String userName;
	private String password;
	private String dbUrl;
	private String dbUserName;
	private String dbPassword;
	private String dbQuery;
	private int scriptTimeout;
	private int pageLoadTimeout;
	private boolean closeAfterEach = true;
	private int poolMinSize = 2;
	private int poolBorrowTimeoutSeconds = 30;
	private int poolMaxReuseCount = 75;

	public ProjectConfigBuilder setBrowserName(String browserName) {
		this.browserName = browserName;
		return this;
	}

	public ProjectConfigBuilder setRemote(boolean remote) {
		this.remote = remote;
		return this;
	}

	public ProjectConfigBuilder setImplicit(short implicit) {
		this.implicit = implicit;
		return this;
	}

	public ProjectConfigBuilder setExplicit(short explicit) {
		this.explicit = explicit;
		return this;
	}

	public ProjectConfigBuilder setRetryLimit(short retryLimit) {
		this.retryLimit = retryLimit;
		return this;
	}

	public ProjectConfigBuilder setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
		return this;
	}

	public ProjectConfigBuilder setAppUrl(String appUrl) {
		this.appUrl = appUrl;
		return this;
	}

	public ProjectConfigBuilder setDevUrl(String devUrl) {
		this.devUrl = devUrl;
		return this;
	}

	public ProjectConfigBuilder setQaUrl(String qaUrl) {
		this.qaUrl = qaUrl;
		return this;
	}

	public ProjectConfigBuilder setCredentials(String userName, String password) {
		this.userName = userName;
		this.password = password;
		return this;
	}

	public ProjectConfigBuilder setDbConfig(String url, String userName, String password, String query) {
		this.dbUrl = url;
		this.dbUserName = userName;
		this.dbPassword = password;
		this.dbQuery = query;
		return this;
	}

	public ProjectConfigBuilder setShortWait(short shortWait) {
		this.shortWait = shortWait;
		return this;
	}

	public ProjectConfigBuilder setPollingIntervalMs(int pollingIntervalMs) {
		this.pollingIntervalMs = pollingIntervalMs;
		return this;
	}

	public ProjectConfigBuilder setElementMaxRetryAttempts(int attempts) {
		this.elementMaxRetryAttempts = attempts;
		return this;
	}

	public ProjectConfigBuilder setDefaultWaitTimeout(int defaultWaitTimeout) {
		this.defaultWaitTimeout = defaultWaitTimeout;
		return this;
	}

	public ProjectConfigBuilder setPoolMaxSize(int poolMaxSize) {
		this.poolMaxSize = poolMaxSize;
		return this;
	}

	public ProjectConfigBuilder setPoolMaxIdleMinutes(int poolMaxIdleMinutes) {
		this.poolMaxIdleMinutes = poolMaxIdleMinutes;
		return this;
	}
	
	public ProjectConfigBuilder setScriptTimeout(int scriptTimeout) {
		this.scriptTimeout = scriptTimeout;
		return this;
	}
	
	public ProjectConfigBuilder setPageLoadTimeout(int pageLoadTimeout) {
		this.pageLoadTimeout = pageLoadTimeout;
		return this;
	}

	public ProjectConfigBuilder setCloseAfterEach(boolean closeAfterEach) {
		this.closeAfterEach = closeAfterEach;
		return this;
	}

	public ProjectConfigBuilder setPoolMinSize(int poolMinSize) {
		this.poolMinSize = poolMinSize;
		return this;
	}

	public ProjectConfigBuilder setPoolBorrowTimeoutSeconds(int poolBorrowTimeoutSeconds) {
		this.poolBorrowTimeoutSeconds = poolBorrowTimeoutSeconds;
		return this;
	}

	public ProjectConfigBuilder setPoolMaxReuseCount(int poolMaxReuseCount) {
		this.poolMaxReuseCount = poolMaxReuseCount;
		return this;
	}

	public ProjectConfig build() {
		return new ProjectConfig(
			browserName, remote, implicit, explicit, shortWait, pollingIntervalMs,
			defaultWaitTimeout, retryLimit, elementMaxRetryAttempts, poolMaxSize,
			poolMaxIdleMinutes, serverUrl, devUrl, qaUrl, appUrl, userName,
			password, dbUrl, dbUserName, dbPassword, dbQuery, scriptTimeout,
			pageLoadTimeout, closeAfterEach, poolMinSize, poolBorrowTimeoutSeconds,
			poolMaxReuseCount
		);
	}
}
