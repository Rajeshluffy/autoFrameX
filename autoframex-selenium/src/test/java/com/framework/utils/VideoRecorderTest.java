package com.framework.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Coverage for {@link VideoRecorder}'s lifecycle and retention policy —
 * previously untested despite being on the critical path of every
 * {@code @AfterMethod} (via {@code ProjectSpecificMethods.postCondition}).
 * FFmpeg is genuinely invoked for the assembly tests (confirmed present on
 * this machine), not mocked, so this also validates the real command line
 * {@link VideoRecorder} builds.
 *
 * <p>Does <b>not</b> attempt to test the "FFmpeg binary missing" path: {@code
 * VideoRecorder.FFMPEG} is a {@code static final} field resolved once at class
 * load, so a {@code System.setProperty("ffmpeg.path", ...)} call from inside a
 * test method has no effect if anything already triggered the class's static
 * init this JVM. That path is already correctly handled in source
 * (assembleVideo()'s {@code catch (IOException)} sets {@code lastError} and
 * returns {@code null} rather than propagating) — this is a testability gap
 * in the fixture, not an untested behavior change.
 */
public class VideoRecorderTest {

	@Test
	public void startWithNullDriverNeverBecomesActive() {
		VideoRecorder recorder = new VideoRecorder();
		recorder.start("nullDriverTest", null);
		Assert.assertFalse(recorder.isActive(), "start(name, null) must not flip active — nothing to capture.");
		Assert.assertNull(recorder.stop(true));
		Assert.assertNull(recorder.stop(false));
	}

	@Test
	public void passedTestDiscardsFramesAndReturnsNull() throws InterruptedException {
		VideoRecorder recorder = new VideoRecorder();
		recorder.start("passedTest", new FakeScreenshotDriver());
		Assert.assertTrue(recorder.isActive());
		Thread.sleep(600); // let at least one capture tick (2fps → every 500ms) land
		File result = recorder.stop(true);
		Assert.assertNull(result, "stop(true) must discard frames and return null regardless of how many were captured.");
		Assert.assertFalse(recorder.isActive());
	}

	@Test
	public void failedTestWithZeroCapturedFramesSkipsAssemblyAndReturnsNull() throws InterruptedException {
		// AlwaysFailingScreenshotDriver keeps frameCounter at 0 regardless of how
		// many capture ticks the scheduler fires (captureFrame()'s catch swallows
		// every failure) — deterministic, unlike racing the zero-initial-delay
		// scheduler with a driver that *would* succeed.
		VideoRecorder recorder = new VideoRecorder();
		recorder.start("zeroFramesTest", new AlwaysFailingScreenshotDriver());
		Thread.sleep(600); // give the scheduler multiple chances to (fail to) capture
		File result = recorder.stop(false);
		Assert.assertNull(result,
				"stop(false) with zero captured frames must skip FFmpeg entirely and return null, "
				+ "not attempt to assemble an empty sequence.");
	}

	@Test
	public void failedTestWithCapturedFramesProducesARealMp4() throws InterruptedException {
		VideoRecorder recorder = new VideoRecorder();
		recorder.start("failedTestWithFrames", new FakeScreenshotDriver());
		Thread.sleep(1100); // ~2 capture ticks at 2fps
		File mp4 = recorder.stop(false);
		try {
			Assert.assertNotNull(mp4, "stop(false) with >=1 captured frame should produce a real MP4 via FFmpeg.");
			Assert.assertTrue(mp4.exists(), "Returned File must actually exist on disk.");
			Assert.assertTrue(mp4.length() > 0, "Assembled MP4 must be non-empty.");
			Assert.assertTrue(mp4.getName().endsWith(".mp4"));
		} finally {
			if (mp4 != null) {
				mp4.delete();
			}
		}
	}

	@Test
	public void isActiveReflectsLifecycleAccurately() throws InterruptedException {
		VideoRecorder recorder = new VideoRecorder();
		Assert.assertFalse(recorder.isActive(), "Never-started recorder must report inactive.");

		recorder.start("lifecycleTest", new FakeScreenshotDriver());
		Assert.assertTrue(recorder.isActive());

		Thread.sleep(600);
		recorder.stop(true);
		Assert.assertFalse(recorder.isActive(), "isActive() must flip false after stop(), on the passed path.");
	}

	@Test
	public void secondStopCallAfterAlreadyStoppedIsANoOp() throws InterruptedException {
		VideoRecorder recorder = new VideoRecorder();
		recorder.start("doubleStopTest", new FakeScreenshotDriver());
		Thread.sleep(600);
		recorder.stop(true);
		// Calling stop() again must not throw or double-clean an already-cleaned frames dir.
		Assert.assertNull(recorder.stop(true));
		Assert.assertNull(recorder.stop(false));
	}

	@Test
	public void concurrentRecordingsUseIsolatedFrameDirectories() throws InterruptedException {
		final int threadCount = 4;
		AtomicInteger failures = new AtomicInteger(0);
		Thread[] threads = new Thread[threadCount];

		for (int i = 0; i < threadCount; i++) {
			final int idx = i;
			threads[i] = new Thread(() -> {
				try {
					VideoRecorder recorder = new VideoRecorder();
					recorder.start("concurrentTest" + idx, new FakeScreenshotDriver());
					Thread.sleep(600);
					File mp4 = recorder.stop(false);
					if (mp4 == null || !mp4.exists() || mp4.length() == 0) {
						failures.incrementAndGet();
					} else {
						mp4.delete();
					}
				} catch (Exception e) {
					failures.incrementAndGet();
				}
			}, "video-concurrent-test-" + idx);
		}

		for (Thread t : threads) t.start();
		for (Thread t : threads) t.join();

		Assert.assertEquals(failures.get(), 0,
				"All " + threadCount + " concurrent recordings should independently produce a valid MP4 — "
				+ "a failure here suggests frame-directory collision between threads.");
	}

	/**
	 * Fake {@link RemoteWebDriver} that answers {@code getScreenshotAs(FILE)}
	 * with a real, tiny, valid PNG each call (VideoRecorder's {@code
	 * captureFrame()} copies the returned file with {@code FileUtils.copyFile},
	 * so it must be a real file with real bytes, not a stub).
	 */
	private static class FakeScreenshotDriver extends RemoteWebDriver {
		protected FakeScreenshotDriver() {
			super();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <X> X getScreenshotAs(OutputType<X> target) {
			try {
				File tmp = File.createTempFile("fake-screenshot", ".png");
				tmp.deleteOnExit();
				BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
				ImageIO.write(image, "png", tmp);
				return (X) tmp;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/** Fails every screenshot attempt — drives the zero-captured-frames test deterministically. */
	private static class AlwaysFailingScreenshotDriver extends RemoteWebDriver {
		protected AlwaysFailingScreenshotDriver() {
			super();
		}

		@Override
		public <X> X getScreenshotAs(OutputType<X> target) {
			throw new org.openqa.selenium.WebDriverException("simulated screenshot failure");
		}
	}
}
