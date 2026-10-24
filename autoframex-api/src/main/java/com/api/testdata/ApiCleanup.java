package com.api.testdata;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.api.rest.assured.base.ApiRedaction;

/**
 * Removes what a test created, whatever the test's outcome.
 *
 * <p>A test registers an undo the moment it creates something, and an {@code @After} hook runs them all:
 * <pre>
 * ResponseAPI created = service.createIncident(payload);
 * String sysId = created.path("result.sys_id", String.class);
 * ApiCleanup.register("delete incident " + sysId, () -&gt; service.deleteIncident(sysId));
 * ...
 * {@literal @}After public void cleanUp() { ApiCleanup.runAll(); }
 * </pre>
 *
 * <p>This replaces keeping the created id in one field of the scenario context, which loses the first record
 * when a scenario creates two, and is never set at all when the test fails before reading the id. Here every
 * registered undo runs, newest first (children before parents), and one failing undo does not stop the others.
 *
 * <p>The list belongs to the thread that registered, like a test's own state, so parallel tests do not delete
 * each other's records. Register and run on the thread that runs the test.
 */
public final class ApiCleanup {

    private static final Logger logger = LoggerFactory.getLogger(ApiCleanup.class);

    private static final ThreadLocal<Deque<Item>> PENDING = ThreadLocal.withInitial(ArrayDeque::new);

    private static final class Item {
        final String description;
        final CleanupAction action;

        Item(String description, CleanupAction action) {
            this.description = description;
            this.action = action;
        }
    }

    private ApiCleanup() {}

    /** Remembers an undo for this thread's test. {@code description} is what the log and any failure will say. */
    public static void register(String description, CleanupAction action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        PENDING.get().push(new Item(description == null ? "(unnamed cleanup)" : description, action));
    }

    /** How many undos are waiting on this thread. */
    public static int pending() {
        return PENDING.get().size();
    }

    /**
     * Runs this thread's undos, newest first, and empties the list. A failing undo is logged and collected;
     * the rest still run.
     *
     * @return one line per undo that failed (credentials masked); empty when everything was cleaned up
     */
    public static List<String> runAll() {
        Deque<Item> items = PENDING.get();
        List<String> failures = new ArrayList<>();
        try {
            while (!items.isEmpty()) {
                Item item = items.pop();
                try {
                    item.action.run();
                    logger.info("Cleanup done: {}", item.description);
                } catch (Exception | AssertionError e) {
                    String failure = ApiRedaction.mask(item.description + " failed: "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                    logger.warn("Cleanup {}", failure);
                    failures.add(failure);
                }
            }
        } finally {
            PENDING.remove();
        }
        return failures;
    }

    /**
     * Like {@link #runAll()}, but fails if anything could not be cleaned up. Use it where leaked data should
     * turn the run red; do not use it from a hook that must not hide the test's own failure.
     */
    public static void runAllOrFail() {
        List<String> failures = runAll();
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " cleanup action(s) failed, test data was left behind:\n  "
                    + String.join("\n  ", failures));
        }
    }

    /** Forgets this thread's undos without running them. */
    public static void discard() {
        PENDING.remove();
    }
}
