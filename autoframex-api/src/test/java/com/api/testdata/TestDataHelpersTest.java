package com.api.testdata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/** Cleanup that always runs and unique names that never collide. */
public class TestDataHelpersTest {

	@AfterMethod(alwaysRun = true)
	public void reset() {
		ApiCleanup.discard();
	}

	@Test
	public void undoesRunNewestFirst() {
		List<String> order = new ArrayList<>();
		ApiCleanup.register("delete parent", () -> order.add("parent"));
		ApiCleanup.register("delete child", () -> order.add("child"));
		Assert.assertEquals(ApiCleanup.pending(), 2);

		Assert.assertTrue(ApiCleanup.runAll().isEmpty());
		Assert.assertEquals(order, List.of("child", "parent"), "children go before parents");
		Assert.assertEquals(ApiCleanup.pending(), 0);
	}

	@Test
	public void oneFailingUndoDoesNotStopTheOthers() {
		List<String> ran = new ArrayList<>();
		ApiCleanup.register("first", () -> ran.add("first"));
		ApiCleanup.register("broken", () -> {
			throw new IllegalStateException("404 Not Found for access_token=SECRET-VALUE-1234");
		});
		ApiCleanup.register("last", () -> ran.add("last"));

		List<String> failures = ApiCleanup.runAll();
		Assert.assertEquals(ran, List.of("last", "first"));
		Assert.assertEquals(failures.size(), 1);
		Assert.assertTrue(failures.get(0).contains("broken failed"), failures.get(0));
		Assert.assertFalse(failures.get(0).contains("SECRET-VALUE-1234"), failures.get(0));
	}

	@Test
	public void anAssertionErrorInAnUndoIsCollectedToo() {
		ApiCleanup.register("verify gone", () -> Assert.fail("still there"));
		Assert.assertEquals(ApiCleanup.runAll().size(), 1);
	}

	@Test
	public void runAllOrFailReportsLeftoverData() {
		ApiCleanup.register("delete incident 7", () -> {
			throw new RuntimeException("403");
		});
		try {
			ApiCleanup.runAllOrFail();
			Assert.fail("leftover data must fail");
		} catch (AssertionError e) {
			Assert.assertTrue(e.getMessage().contains("delete incident 7"), e.getMessage());
		}
		Assert.assertEquals(ApiCleanup.pending(), 0, "nothing is retried by the next test");
	}

	@Test
	public void discardForgetsWithoutRunning() {
		List<String> ran = new ArrayList<>();
		ApiCleanup.register("x", () -> ran.add("x"));
		ApiCleanup.discard();
		Assert.assertTrue(ApiCleanup.runAll().isEmpty());
		Assert.assertTrue(ran.isEmpty());
	}

	@Test
	public void eachThreadOnlyCleansItsOwnData() throws Exception {
		List<String> ran = new ArrayList<>();
		ApiCleanup.register("mine", () -> ran.add("mine"));

		CountDownLatch done = new CountDownLatch(1);
		int[] otherPending = new int[1];
		Thread other = new Thread(() -> {
			otherPending[0] = ApiCleanup.pending();
			ApiCleanup.runAll();
			done.countDown();
		});
		other.start();
		done.await();

		Assert.assertEquals(otherPending[0], 0, "another test's records are not visible here");
		Assert.assertTrue(ran.isEmpty(), "and running there did not delete mine");
		ApiCleanup.runAll();
		Assert.assertEquals(ran, List.of("mine"));
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void aNullActionIsRejectedWhenRegistered() {
		ApiCleanup.register("nothing", null);
	}

	@Test
	public void uniqueNamesNeverRepeat() throws Exception {
		Set<String> names = ConcurrentHashMap.newKeySet();
		List<Thread> threads = new ArrayList<>();
		for (int t = 0; t < 8; t++) {
			Thread thread = new Thread(() -> {
				for (int i = 0; i < 500; i++) {
					names.add(UniqueData.unique("incident"));
				}
			});
			threads.add(thread);
			thread.start();
		}
		for (Thread thread : threads) {
			thread.join();
		}
		Assert.assertEquals(names.size(), 4000);
		Assert.assertTrue(new HashSet<>(names).iterator().next().startsWith("incident-" + UniqueData.runId()));
	}

	@Test
	public void aMissingPrefixStillGivesAName() {
		Assert.assertTrue(UniqueData.unique(null).startsWith("data-"));
		Assert.assertTrue(UniqueData.unique(" ").startsWith("data-"));
	}
}
