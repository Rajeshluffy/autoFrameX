package com.framework.observability;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe singleton that tracks pass/fail history per test name across the
 * entire suite run and computes a flakiness score.
 *
 * <p>Window size: last 10 results per test.
 * flakyScore = failures / windowSize  (0.0 = always passes, 1.0 = always fails)
 */
public final class FlakyTestTracker {

    public enum FlakyClassification { STABLE, FLAKY, QUARANTINED }

    private static final int WINDOW_SIZE = 10;
    private static final FlakyTestTracker INSTANCE = new FlakyTestTracker();

    private final ConcurrentHashMap<String, Deque<Boolean>> history = new ConcurrentHashMap<>();

    private FlakyTestTracker() {}

    public static FlakyTestTracker getInstance() { return INSTANCE; }

    /**
     * Records a pass or fail result for the given test name.
     * Maintains a sliding window of the last {@code WINDOW_SIZE} results.
     */
    public void record(String testName, boolean passed) {
        Deque<Boolean> window = history.computeIfAbsent(testName,
                k -> new ArrayDeque<>(WINDOW_SIZE));
        synchronized (window) {
            window.addLast(passed);
            while (window.size() > WINDOW_SIZE) {
                window.removeFirst();
            }
        }
    }

    /**
     * Returns the flakiness score: ratio of failures in the sliding window.
     * Returns 0.0 if the test has no recorded history.
     */
    public double getFlakyScore(String testName) {
        Deque<Boolean> window = history.get(testName);
        if (window == null) return 0.0;
        synchronized (window) {
            if (window.isEmpty()) return 0.0;
            long failures = window.stream().filter(b -> !b).count();
            return (double) failures / window.size();
        }
    }

    /**
     * Classifies a test based on its flaky score.
     * STABLE < 0.2, FLAKY 0.2–0.8, QUARANTINED > 0.8
     */
    public FlakyClassification getClassification(String testName) {
        double score = getFlakyScore(testName);
        if (score > 0.8) return FlakyClassification.QUARANTINED;
        if (score >= 0.2) return FlakyClassification.FLAKY;
        return FlakyClassification.STABLE;
    }

    /** Returns an unmodifiable snapshot of all tracked tests and their flaky scores. */
    public Map<String, Double> getSummary() {
        Map<String, Double> summary = new HashMap<>();
        history.keySet().forEach(name -> summary.put(name, getFlakyScore(name)));
        return Collections.unmodifiableMap(summary);
    }

    /** Clears all tracked history. Call from @AfterSuite. */
    public void reset() {
        history.clear();
    }
}
