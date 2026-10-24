package com.api.testdata;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Names that do not collide, so parallel tests and repeated runs never find each other's records.
 *
 * <p>Fixed test data such as {@code "APISessionJAN"} breaks as soon as two scenarios run at once or a
 * previous run left its record behind. {@code UniqueData.unique("incident")} gives
 * {@code incident-3f9a1c2e-t12-7}: this run's id, the thread, and a counter.
 */
public final class UniqueData {

    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private UniqueData() {}

    /** {@code prefix-<run>-t<thread>-<n>}: unique per call, per thread and per JVM run. */
    public static String unique(String prefix) {
        return (prefix == null || prefix.isBlank() ? "data" : prefix)
                + "-" + RUN_ID + "-t" + Thread.currentThread().getId() + "-" + SEQUENCE.incrementAndGet();
    }

    /** The id shared by everything this JVM run created; handy for finding a run's leftovers. */
    public static String runId() {
        return RUN_ID;
    }
}
