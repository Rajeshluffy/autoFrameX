package com.framework.selenium.api.base;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.Assert;
import org.testng.annotations.Test;

import design.patterns.object.pool.DriverPoolManager;

/**
 * Regression coverage for finding F5 in the framework-3.1 architecture review:
 * {@link SeleniumBase#getDriverManager()} used to cache its result on a plain
 * instance field (removed — see that method's Javadoc). Under
 * {@code parallel="methods"}/{@code "classes"}, TestNG runs multiple threads
 * against one shared instance of the test class, so an unsynchronized
 * "cache on first call" field let whichever thread called it first pin every
 * other thread on that shared instance to its context — silently defeating
 * {@link DriverPoolManager}'s per-thread multi-context resolution the moment
 * two contexts ever shared one instance.
 *
 * <p>This test reproduces exactly that shape: one shared {@link SeleniumBase}
 * instance, two real threads bound to two different contexts via
 * {@link DriverPoolManager#bindContext}, sequenced so the second thread's call
 * happens strictly after the first's (the precondition that made the old
 * cached field return stale data).
 */
public class DriverManagerContextResolutionTest {

	@Test
	public void sharedInstanceResolvesEachThreadsOwnContextIndependently() throws InterruptedException {
		SeleniumBase shared = new SeleniumBase() {
			// SeleniumBase itself is concrete; no overrides needed for this test.
		};

		AtomicReference<DriverPoolManager> managerSeenByThreadA = new AtomicReference<>();
		AtomicReference<DriverPoolManager> managerSeenByThreadB = new AtomicReference<>();

		Thread threadA = new Thread(() -> {
			DriverPoolManager.bindContext("F5_CONTEXT_A");
			managerSeenByThreadA.set(shared.getDriverManager());
		}, "F5-test-thread-A");

		threadA.start();
		threadA.join(); // strictly sequenced: A's call (and, pre-fix, its cache write) completes first

		Thread threadB = new Thread(() -> {
			DriverPoolManager.bindContext("F5_CONTEXT_B");
			managerSeenByThreadB.set(shared.getDriverManager());
		}, "F5-test-thread-B");

		threadB.start();
		threadB.join();

		Assert.assertNotNull(managerSeenByThreadA.get());
		Assert.assertNotNull(managerSeenByThreadB.get());
		Assert.assertNotSame(managerSeenByThreadA.get(), managerSeenByThreadB.get(),
				"Thread B (bound to F5_CONTEXT_B) received the same DriverPoolManager instance as "
				+ "Thread A (bound to F5_CONTEXT_A) — the shared SeleniumBase instance is still "
				+ "pinning every thread to whichever context called getDriverManager() first, "
				+ "exactly the F5 regression this test guards against.");

		// Cross-check against DriverPoolManager's own source of truth for each context,
		// resolved fresh on the calling thread (this test's own thread, bound below).
		DriverPoolManager.bindContext("F5_CONTEXT_A");
		Assert.assertSame(managerSeenByThreadA.get(), DriverPoolManager.getInstance());

		DriverPoolManager.bindContext("F5_CONTEXT_B");
		Assert.assertSame(managerSeenByThreadB.get(), DriverPoolManager.getInstance());
	}
}
