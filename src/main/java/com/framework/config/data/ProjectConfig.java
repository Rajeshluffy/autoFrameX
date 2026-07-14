package com.framework.config.data;

public class ProjectConfig {

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------
	private final String browserName;
	private final boolean remote;
	private final short implicit;
	private final short explicit;
	private final short shortWait;
	private final int pollingIntervalMs;
	private final int defaultWaitTimeout;
	private final short retryLimit;
	private final int elementMaxRetryAttempts;
	private final int poolMaxSize;
	private final int poolMaxIdleMinutes;
	private final String serverUrl;
	private final String devUrl;
	private final String qaUrl;
	private final String appUrl;
	private final String userName;
	private final String password;
	private final String dbUrl;
	private final String dbUserName;
	private final String dbPassword;
	private final String dbQuery;
	private final int scriptTimeout;
	private final int pageLoadTimeout;
	private final boolean closeAfterEach;
	private final int poolMinSize;
	private final int poolBorrowTimeoutSeconds;
	private final int poolMaxReuseCount;
	private final boolean gridEnabled;
	private final String gridHubUrl;
	private final ExecutionMode executionMode;
	private final String excelDataFile;

	// -------------------------------------------------------------------------
	// Constructor (package-private, intended for ProjectConfigBuilder only)
	// -------------------------------------------------------------------------
	ProjectConfig(
			String browserName, boolean remote, short implicit, short explicit,
			short shortWait, int pollingIntervalMs, int defaultWaitTimeout,
			short retryLimit, int elementMaxRetryAttempts, int poolMaxSize,
			int poolMaxIdleMinutes, String serverUrl, String devUrl, String qaUrl,
			String appUrl, String userName, String password, String dbUrl,
			String dbUserName, String dbPassword, String dbQuery,
			int scriptTimeout, int pageLoadTimeout, boolean closeAfterEach,
			int poolMinSize, int poolBorrowTimeoutSeconds, int poolMaxReuseCount,
			boolean gridEnabled, String gridHubUrl,
			ExecutionMode executionMode, String excelDataFile) {

		this.browserName = browserName;
		this.remote = remote;
		this.implicit = implicit;
		this.explicit = explicit;
		this.shortWait = shortWait;
		this.pollingIntervalMs = pollingIntervalMs;
		this.defaultWaitTimeout = defaultWaitTimeout;
		this.retryLimit = retryLimit;
		this.elementMaxRetryAttempts = elementMaxRetryAttempts;
		this.poolMaxSize = poolMaxSize;
		this.poolMaxIdleMinutes = poolMaxIdleMinutes;
		this.serverUrl = serverUrl;
		this.devUrl = devUrl;
		this.qaUrl = qaUrl;
		this.appUrl = appUrl;
		this.userName = userName;
		this.password = password;
		this.dbUrl = dbUrl;
		this.dbUserName = dbUserName;
		this.dbPassword = dbPassword;
		this.dbQuery = dbQuery;
		this.scriptTimeout = scriptTimeout;
		this.pageLoadTimeout = pageLoadTimeout;
		this.closeAfterEach = closeAfterEach;
		this.poolMinSize = poolMinSize;
		this.poolBorrowTimeoutSeconds = poolBorrowTimeoutSeconds;
		this.poolMaxReuseCount = poolMaxReuseCount;
		this.gridEnabled = gridEnabled;
		this.gridHubUrl = gridHubUrl;
		this.executionMode = executionMode != null ? executionMode : ExecutionMode.TARGETED;
		this.excelDataFile = excelDataFile != null ? excelDataFile : "";
	}

	// -------------------------------------------------------------------------
	// Getters
	// -------------------------------------------------------------------------
	public String getBrowserName() { return browserName; }
	public boolean isRemote() { return remote; }
	public short getImplicit() { return implicit; }
	public short getExplicit() { return explicit; }
	public short getShortWait() { return shortWait; }
	public int getPollingIntervalMs() { return pollingIntervalMs; }
	public int getDefaultWaitTimeout() { return defaultWaitTimeout; }
	public short getRetryLimit() { return retryLimit; }
	public int getElementMaxRetryAttempts() { return elementMaxRetryAttempts; }
	public int getPoolMaxSize() { return poolMaxSize; }
	public int getPoolMaxIdleMinutes() { return poolMaxIdleMinutes; }
	public String getServerUrl() { return serverUrl; }
	public String getDevUrl() { return devUrl; }
	public String getQaUrl() { return qaUrl; }
	public String getAppUrl() { return appUrl; }
	public String getUserName() { return userName; }
	public String getPassword() { return password; }
	public String getDbUrl() { return dbUrl; }
	public String getDbUserName() { return dbUserName; }
	public String getDbPassword() { return dbPassword; }
	public String getDbQuery() { return dbQuery; }
	public int getScriptTimeout() { return scriptTimeout; }
	public int getPageLoadTimeout() { return pageLoadTimeout; }
	public boolean isCloseAfterEach() { return closeAfterEach; }
	public int getPoolMinSize() { return poolMinSize; }
	public int getPoolBorrowTimeoutSeconds() { return poolBorrowTimeoutSeconds; }
	public int getPoolMaxReuseCount() { return poolMaxReuseCount; }
	public boolean isGridEnabled() { return gridEnabled; }
	public String getGridHubUrl() { return gridHubUrl; }
	public ExecutionMode getExecutionMode() { return executionMode; }
	public String getExcelDataFile() { return excelDataFile; }

}
