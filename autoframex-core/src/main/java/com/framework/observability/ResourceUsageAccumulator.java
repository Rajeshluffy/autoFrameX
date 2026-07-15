package com.framework.observability;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Thread-local accumulator for per-test resource usage metrics.
 *
 * Performance utilities write metrics here during test execution.
 * Reporter.tearDownTest() drains the map into TestEvent.resourceUsage
 * when building the observability event.
 */
public final class ResourceUsageAccumulator {

    private static final ThreadLocal<Map<String, Object>> holder =
            ThreadLocal.withInitial(HashMap::new);

    private ResourceUsageAccumulator() {}

    /** Adds or replaces a metric entry for the current thread. */
    public static void put(String key, Object value) {
        holder.get().put(key, value);
    }

    /**
     * Returns an unmodifiable snapshot of the current thread's metrics,
     * then clears the map so the next test starts fresh.
     */
    public static Map<String, Object> drain() {
        Map<String, Object> snapshot = Collections.unmodifiableMap(
                new HashMap<>(holder.get()));
        holder.get().clear();
        return snapshot;
    }

    /** Explicitly clears the current thread's map. Call from cleanupThreadLocals(). */
    public static void clear() {
        holder.remove();
    }
}
