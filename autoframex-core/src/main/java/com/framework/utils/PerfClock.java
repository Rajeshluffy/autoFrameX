package com.framework.utils;

import java.util.concurrent.TimeUnit;

/**
 * Tiny stopwatch helper shared by every automatic performance-capture site
 * (UI element interactions, page-load readiness, API calls) across the
 * selenium and api modules.
 *
 * <p>Uses {@link System#nanoTime()} rather than {@code currentTimeMillis()}
 * for elapsed-time measurement — monotonic, immune to system clock
 * adjustments, standard practice for interval timing.
 */
public final class PerfClock {

	private PerfClock() {}

	/** Starting mark for a timed interval. Pair with {@link #elapsedMs(long)}. */
	public static long start() {
		return System.nanoTime();
	}

	/** Milliseconds elapsed since {@code startNanos} (from {@link #start()}). */
	public static long elapsedMs(long startNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
	}
}
