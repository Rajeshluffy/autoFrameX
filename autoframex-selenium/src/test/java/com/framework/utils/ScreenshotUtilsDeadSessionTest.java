package com.framework.utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Regression coverage for a gap found during the framework-3.1 architecture
 * review's screenshot/video workstream: {@link ScreenshotUtils#captureViewport}
 * and {@link ScreenshotUtils#captureDomSnapshot} previously caught only
 * {@link java.io.IOException}, letting a {@link WebDriverException} (exactly
 * what a dead/crashed session throws) propagate uncaught. That mattered most
 * for {@link ScreenshotUtils#captureFailureEvidence}, which calls straight
 * into both methods — and is meant to run right after a test has already
 * failed, when the driver is most likely to be in a broken state.
 *
 * <p>Uses a session-less {@link RemoteWebDriver} subclass (via the protected
 * no-arg constructor — same technique as
 * {@code design.patterns.object.pool.PoolCounterAccountingTest}) rather than a
 * real browser, so this runs in milliseconds.
 */
public class ScreenshotUtilsDeadSessionTest {

	@Test
	public void captureViewportOnDeadSessionReturnsNullInsteadOfThrowing() {
		String result = ScreenshotUtils.captureViewport(new DeadSessionDriver(), tempDir());
		Assert.assertNull(result, "captureViewport() should degrade to null on a dead session, not throw");
	}

	@Test
	public void captureDomSnapshotOnDeadSessionReturnsNullInsteadOfThrowing() {
		String result = ScreenshotUtils.captureDomSnapshot(new DeadSessionDriver(), tempDir());
		Assert.assertNull(result, "captureDomSnapshot() should degrade to null on a dead session, not throw");
	}

	@Test
	public void captureFailureEvidenceOnDeadSessionNeverThrows() {
		// The whole point of this bundle method: one broken capture must not
		// prevent the caller (an @AfterMethod-style failure handler) from
		// completing. Asserting it returns *at all* is the regression here —
		// pre-fix, this threw a WebDriverException straight out of captureViewport().
		ScreenshotUtils.FailureEvidence evidence =
				ScreenshotUtils.captureFailureEvidence(new DeadSessionDriver(), tempDir());
		Assert.assertNotNull(evidence, "captureFailureEvidence() must always return a bundle, even if every field inside it is null");
		Assert.assertNull(evidence.screenshotPath);
		Assert.assertNull(evidence.domSnapshotPath);
	}

	private static String tempDir() {
		return System.getProperty("java.io.tmpdir") + "/screenshot-utils-dead-session-test";
	}

	/** Session-less fake driver whose viewport/DOM calls always fail like a dead session would. */
	private static class DeadSessionDriver extends RemoteWebDriver {
		protected DeadSessionDriver() {
			super();
		}

		@Override
		public String getPageSource() {
			throw new WebDriverException("simulated dead session");
		}

		@Override
		public <X> X getScreenshotAs(OutputType<X> target) {
			throw new WebDriverException("simulated dead session");
		}
	}
}
