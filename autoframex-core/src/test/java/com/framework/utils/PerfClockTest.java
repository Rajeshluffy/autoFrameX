package com.framework.utils;

import org.testng.Assert;
import org.testng.annotations.Test;

/** Coverage for the tiny stopwatch every automatic performance-capture site uses. */
public class PerfClockTest {

	@Test
	public void elapsedMsIsApproximatelyTheSleptDuration() throws InterruptedException {
		long start = PerfClock.start();
		Thread.sleep(50);
		long elapsed = PerfClock.elapsedMs(start);
		Assert.assertTrue(elapsed >= 45, "Expected at least ~45ms elapsed, got " + elapsed + "ms");
		Assert.assertTrue(elapsed < 2000, "Expected well under 2s elapsed, got " + elapsed + "ms — clock looks broken");
	}

	@Test
	public void elapsedMsIsNeverNegative() {
		long start = PerfClock.start();
		long elapsed = PerfClock.elapsedMs(start);
		Assert.assertTrue(elapsed >= 0, "Elapsed time must never be negative, got " + elapsed + "ms");
	}
}
