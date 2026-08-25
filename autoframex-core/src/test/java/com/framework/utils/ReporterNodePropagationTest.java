package com.framework.utils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

/**
 * Regression coverage for {@link Reporter#captureCurrentNode()}/
 * {@link Reporter#runWithNode(ExtentTest, Runnable)} — the fix for a gap found
 * live while verifying the framework-3.1 automatic API-call timing feature:
 * {@code ApiPerformanceUtils.runLoadTest()}'s concurrent worker threads never
 * inherit the calling thread's {@code ThreadLocal<ExtentTest>} node (plain
 * {@code ThreadLocal}s don't propagate to new threads), so every one of a load
 * test's API calls silently dropped its timing step. Confirmed against a real
 * ServiceNow instance: 176 calls with no report line before this fix, all of
 * them present after.
 */
public class ReporterNodePropagationTest {

	@Test
	public void runWithNodeMakesTheCapturedNodeVisibleOnAnotherThread() throws Exception {
		ExtentTest node = createRealExtentTestNode("propagation-test");

		AtomicReference<ExtentTest> seenOnWorkerThread = new AtomicReference<>();
		Thread worker = new Thread(() ->
				Reporter.runWithNode(node, () -> seenOnWorkerThread.set(Reporter.captureCurrentNode())),
				"propagation-test-worker");
		worker.start();
		worker.join();

		Assert.assertSame(seenOnWorkerThread.get(), node,
				"runWithNode() should make the captured node visible via captureCurrentNode() on the worker thread.");
	}

	@Test
	public void runWithNodeClearsTheBindingAfterwardsSoAPooledThreadDoesNotLeakIt() throws Exception {
		ExtentTest node = createRealExtentTestNode("propagation-cleanup-test");

		AtomicReference<ExtentTest> duringTask = new AtomicReference<>();
		AtomicReference<ExtentTest> afterTask = new AtomicReference<>();
		Thread worker = new Thread(() -> {
			Reporter.runWithNode(node, () -> duringTask.set(Reporter.captureCurrentNode()));
			// Simulates a thread-pool thread being reused for unrelated work next —
			// must not still see the previous task's node.
			afterTask.set(Reporter.captureCurrentNode());
		}, "propagation-cleanup-worker");
		worker.start();
		worker.join();

		Assert.assertSame(duringTask.get(), node);
		Assert.assertNull(afterTask.get(),
				"The node binding must be cleared after runWithNode() returns — otherwise a reused "
				+ "pool thread would leak one task's report node into the next, unrelated task's steps.");
	}

	@Test
	public void runWithNodeIsAPlainPassthroughWhenNodeIsNull() {
		// captureCurrentNode() returns null when the calling thread never had a
		// node bound (e.g. a thread pool created before any test started) —
		// runWithNode() must not NPE on that, just run the task normally.
		AtomicReference<Boolean> ran = new AtomicReference<>(false);
		Reporter.runWithNode(null, () -> ran.set(true));
		Assert.assertTrue(ran.get());
	}

	@Test
	public void multipleWorkerThreadsShareTheSameCapturedNodeWithoutCrossTalk() throws Exception {
		ExtentTest node = createRealExtentTestNode("propagation-concurrency-test");
		int threadCount = 8;
		CountDownLatch done = new CountDownLatch(threadCount);
		AtomicReference<Boolean> anyMismatch = new AtomicReference<>(false);

		for (int i = 0; i < threadCount; i++) {
			new Thread(() -> {
				Reporter.runWithNode(node, () -> {
					if (Reporter.captureCurrentNode() != node) {
						anyMismatch.set(true);
					}
				});
				done.countDown();
			}, "propagation-concurrency-worker-" + i).start();
		}

		done.await();
		Assert.assertFalse(anyMismatch.get(),
				"Every worker thread should see the exact same captured node — ThreadLocal "
				+ "binding must be independent per-thread, not accidentally shared/overwritten.");
	}

	/** Builds a real, minimal ExtentTest node — a temp-file-backed ExtentReports instance. */
	private static ExtentTest createRealExtentTestNode(String testName) throws IOException {
		File tempReport = File.createTempFile("reporter-node-propagation-test", ".html");
		tempReport.deleteOnExit();
		ExtentReports extent = new ExtentReports();
		extent.attachReporter(new ExtentSparkReporter(tempReport.getAbsolutePath()));
		return extent.createTest(testName);
	}
}
