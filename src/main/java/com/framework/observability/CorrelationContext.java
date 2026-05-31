package com.framework.observability;

import java.util.UUID;

/**
 * ThreadLocal-backed holder for per-test correlation IDs.
 *
 * Suite-level IDs are resolved once at class load from environment variables.
 * Per-test IDs (traceId, sessionId) are generated fresh on each {@link #init()} call.
 */
public final class CorrelationContext {

    private static final String EXECUTION_ID   = UUID.randomUUID().toString();
    private static final String BUILD_ID       = resolveEnv("BUILD_ID",    "local");
    private static final String ENVIRONMENT_ID = resolveEnv("ENVIRONMENT", "local");
    private static final String COMMIT_ID      = resolveEnv("GIT_COMMIT",  "unknown");
    private static final String CONTAINER_ID   = resolveEnv("HOSTNAME",    "local");

    private static final ThreadLocal<Context> holder = new ThreadLocal<>();

    private CorrelationContext() {}

    /** Generates a new traceId + sessionId for the current thread. Call from @BeforeMethod. */
    public static void init() {
        holder.set(new Context(
            UUID.randomUUID().toString(),
            BUILD_ID,
            EXECUTION_ID,
            UUID.randomUUID().toString(),
            String.valueOf(Thread.currentThread().getId()),
            ENVIRONMENT_ID,
            COMMIT_ID,
            CONTAINER_ID
        ));
    }

    /**
     * Returns the Context for the current thread.
     * Creates a safe fallback if init() was never called (e.g. skipped test).
     */
    public static Context get() {
        Context ctx = holder.get();
        if (ctx == null) {
            init();
            ctx = holder.get();
        }
        return ctx;
    }

    /** Removes the ThreadLocal entry. Call from @AfterMethod. */
    public static void clear() {
        holder.remove();
    }

    /** Suite-level execution UUID — stable for the entire suite run. */
    public static String getExecutionId() {
        return EXECUTION_ID;
    }

    private static String resolveEnv(String name, String fallback) {
        String val = System.getenv(name);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    // -------------------------------------------------------------------------
    // Immutable value object
    // -------------------------------------------------------------------------
    public static final class Context {
        private final String traceId;
        private final String buildId;
        private final String executionId;
        private final String sessionId;
        private final String threadId;
        private final String environmentId;
        private final String commitId;
        private final String containerId;

        Context(String traceId, String buildId, String executionId, String sessionId,
                String threadId, String environmentId, String commitId, String containerId) {
            this.traceId       = traceId;
            this.buildId       = buildId;
            this.executionId   = executionId;
            this.sessionId     = sessionId;
            this.threadId      = threadId;
            this.environmentId = environmentId;
            this.commitId      = commitId;
            this.containerId   = containerId;
        }

        public String getTraceId()       { return traceId; }
        public String getBuildId()       { return buildId; }
        public String getExecutionId()   { return executionId; }
        public String getSessionId()     { return sessionId; }
        public String getThreadId()      { return threadId; }
        public String getEnvironmentId() { return environmentId; }
        public String getCommitId()      { return commitId; }
        public String getContainerId()   { return containerId; }
    }
}
