package com.framework.utils;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import com.framework.exception.TestStepFailedException;

/**
 * A "fail" step is the only thing that fails a test, so it must throw even when no
 * Extent node is bound to the thread. It used to log a warning and return, which
 * turned the failure into a log line and let the test pass.
 */
public class ReporterFailStepTest {

	private static Reporter unboundReporter() {
		if (Reporter.captureCurrentNode() != null) {
			throw new SkipException("A report node is bound to this thread; cannot test the no-node path here");
		}
		return new Reporter() {
			@Override
			public long takeSnap() {
				return 0;
			}
		};
	}

	@Test(expectedExceptions = TestStepFailedException.class)
	public void aFailStepThrowsEvenWhenNoNodeIsBound() {
		unboundReporter().reportStep("button is missing", "FAIL", false);
	}

	@Test(expectedExceptions = TestStepFailedException.class)
	public void theStatusIsNotCaseSensitive() {
		unboundReporter().reportStep("button is missing", "fail", false);
	}

	@Test
	public void theThrownMessageCarriesTheStepDescription() {
		try {
			unboundReporter().reportStep("login button is missing", "FAIL", false);
			Assert.fail("a FAIL step must throw");
		} catch (TestStepFailedException e) {
			Assert.assertTrue(e.getMessage().contains("login button is missing"), e.getMessage());
		}
	}

	@Test
	public void nonFailStatusesWithoutANodeAreOnlyLogged() {
		Reporter reporter = unboundReporter();
		reporter.reportStep("fine", "PASS", false);
		reporter.reportStep("note", "INFO", false);
		reporter.reportStep("careful", "WARNING", false);
	}
}
