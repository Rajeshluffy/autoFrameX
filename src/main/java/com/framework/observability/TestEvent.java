package com.framework.observability;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable value object representing one test execution event.
 * Serialized to NDJSON by {@link TestEventCollector}.
 * Construct via {@link #builder()}.
 */
public final class TestEvent {

    private final String testId;
    private final String suite;
    private final String feature;
    private final String severity;
    private final String owner;
    private final String environment;
    private final String browser;
    private final long   startTimeMs;
    private final long   endTimeMs;
    private final long   durationMs;
    private final String status;
    private final String failureCategory;
    private final String failureMessage;
    private final int    retryCount;
    private final double flakyScore;
    private final String traceId;
    private final String buildId;
    private final String gitCommit;
    private final String containerId;
    private final String executionId;
    private final String sessionId;
    private final String threadId;
    private final Map<String, String> artifacts;
    private final Map<String, Object> resourceUsage;

    private TestEvent(Builder b) {
        this.testId          = b.testId;
        this.suite           = b.suite;
        this.feature         = b.feature;
        this.severity        = b.severity;
        this.owner           = b.owner;
        this.environment     = b.environment;
        this.browser         = b.browser;
        this.startTimeMs     = b.startTimeMs;
        this.endTimeMs       = b.endTimeMs;
        this.durationMs      = b.durationMs;
        this.status          = b.status;
        this.failureCategory = b.failureCategory;
        this.failureMessage  = b.failureMessage;
        this.retryCount      = b.retryCount;
        this.flakyScore      = b.flakyScore;
        this.traceId         = b.traceId;
        this.buildId         = b.buildId;
        this.gitCommit       = b.gitCommit;
        this.containerId     = b.containerId;
        this.executionId     = b.executionId;
        this.sessionId       = b.sessionId;
        this.threadId        = b.threadId;
        this.artifacts       = Collections.unmodifiableMap(
                b.artifacts != null ? b.artifacts : new HashMap<>());
        this.resourceUsage   = Collections.unmodifiableMap(
                b.resourceUsage != null ? b.resourceUsage : new HashMap<>());
    }

    public static Builder builder() { return new Builder(); }

    public String getTestId()          { return testId; }
    public String getSuite()           { return suite; }
    public String getFeature()         { return feature; }
    public String getSeverity()        { return severity; }
    public String getOwner()           { return owner; }
    public String getEnvironment()     { return environment; }
    public String getBrowser()         { return browser; }
    public long   getStartTimeMs()     { return startTimeMs; }
    public long   getEndTimeMs()       { return endTimeMs; }
    public long   getDurationMs()      { return durationMs; }
    public String getStatus()          { return status; }
    public String getFailureCategory() { return failureCategory; }
    public String getFailureMessage()  { return failureMessage; }
    public int    getRetryCount()      { return retryCount; }
    public double getFlakyScore()      { return flakyScore; }
    public String getTraceId()         { return traceId; }
    public String getBuildId()         { return buildId; }
    public String getGitCommit()       { return gitCommit; }
    public String getContainerId()     { return containerId; }
    public String getExecutionId()     { return executionId; }
    public String getSessionId()       { return sessionId; }
    public String getThreadId()        { return threadId; }
    public Map<String, String> getArtifacts()     { return artifacts; }
    public Map<String, Object> getResourceUsage() { return resourceUsage; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------
    public static final class Builder {
        private String testId;
        private String suite           = "";
        private String feature         = "";
        private String severity        = "";
        private String owner           = "";
        private String environment     = "";
        private String browser         = "";
        private long   startTimeMs;
        private long   endTimeMs;
        private long   durationMs;
        private String status          = "UNKNOWN";
        private String failureCategory = "UNKNOWN";
        private String failureMessage;
        private int    retryCount;
        private double flakyScore;
        private String traceId         = "";
        private String buildId         = "";
        private String gitCommit       = "";
        private String containerId     = "";
        private String executionId     = "";
        private String sessionId       = "";
        private String threadId        = "";
        private Map<String, String> artifacts     = new HashMap<>();
        private Map<String, Object> resourceUsage = new HashMap<>();

        private Builder() {}

        public Builder testId(String v)          { this.testId = v;          return this; }
        public Builder suite(String v)           { this.suite = v;           return this; }
        public Builder feature(String v)         { this.feature = v;         return this; }
        public Builder severity(String v)        { this.severity = v;        return this; }
        public Builder owner(String v)           { this.owner = v;           return this; }
        public Builder environment(String v)     { this.environment = v;     return this; }
        public Builder browser(String v)         { this.browser = v;         return this; }
        public Builder startTimeMs(long v)       { this.startTimeMs = v;     return this; }
        public Builder endTimeMs(long v)         { this.endTimeMs = v;       return this; }
        public Builder durationMs(long v)        { this.durationMs = v;      return this; }
        public Builder status(String v)          { this.status = v;          return this; }
        public Builder failureCategory(String v) { this.failureCategory = v; return this; }
        public Builder failureMessage(String v)  { this.failureMessage = v;  return this; }
        public Builder retryCount(int v)         { this.retryCount = v;      return this; }
        public Builder flakyScore(double v)      { this.flakyScore = v;      return this; }
        public Builder traceId(String v)         { this.traceId = v;         return this; }
        public Builder buildId(String v)         { this.buildId = v;         return this; }
        public Builder gitCommit(String v)       { this.gitCommit = v;       return this; }
        public Builder containerId(String v)     { this.containerId = v;     return this; }
        public Builder executionId(String v)     { this.executionId = v;     return this; }
        public Builder sessionId(String v)       { this.sessionId = v;       return this; }
        public Builder threadId(String v)        { this.threadId = v;        return this; }
        public Builder artifacts(Map<String, String> v)     { this.artifacts = v;     return this; }
        public Builder resourceUsage(Map<String, Object> v) { this.resourceUsage = v; return this; }

        public TestEvent build() {
            if (testId == null || testId.isEmpty()) {
                throw new IllegalStateException("TestEvent.testId must not be null or empty");
            }
            return new TestEvent(this);
        }
    }
}
