package com.framework.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Selenium's {@code getScreenshotAs(OutputType.FILE)} writes a PNG into the system temp directory and only marks
 * it {@code deleteOnExit}. The recorder copied each frame into its own folder and kept the original: at two frames
 * a second that is 7,200 stray PNGs an hour per recording thread, in {@code /tmp} or the pod's ephemeral storage,
 * until the JVM exits. Every capture that copies the file must delete the original.
 */
public class ScreenshotTempFileTest {

	/** Hands out a real temp PNG per capture and remembers each one, so a leak is visible. */
	private static final class TrackingDriver extends RemoteWebDriver {
		final List<File> handedOut = Collections.synchronizedList(new ArrayList<>());

		@Override
		@SuppressWarnings("unchecked")
		public <X> X getScreenshotAs(OutputType<X> target) {
			try {
				File tmp = File.createTempFile("tracked-screenshot", ".png");
				tmp.deleteOnExit();
				ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", tmp);
				handedOut.add(tmp);
				return (X) tmp;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		long strays() {
			synchronized (handedOut) {
				return handedOut.stream().filter(File::exists).count();
			}
		}
	}

	@Test
	public void theRecorderDoesNotLeaveAFramePerCaptureInTheTempDirectory() throws InterruptedException {
		TrackingDriver driver = new TrackingDriver();
		VideoRecorder recorder = new VideoRecorder();
		recorder.start("tempFileLeakTest", driver);
		Thread.sleep(1700);                       // about three captures at 2 frames a second
		recorder.stop(true);                      // a passed test: frames are discarded

		Assert.assertTrue(driver.handedOut.size() >= 2, "the recorder captured frames: " + driver.handedOut.size());
		Assert.assertEquals(driver.strays(), 0L,
				driver.strays() + " of " + driver.handedOut.size() + " temp screenshots were left behind");
	}

	@Test
	public void copyThenDeleteKeepsTheCopyAndRemovesTheOriginal() throws IOException {
		File original = File.createTempFile("original", ".png");
		Files.writeString(original.toPath(), "image-bytes");
		File dir = Files.createTempDirectory("copy-target").toFile();
		File copy = new File(dir, "kept.png");
		try {
			ScreenshotUtils.copyThenDelete(original, copy);

			Assert.assertEquals(Files.readString(copy.toPath()), "image-bytes", "the copy has the content");
			Assert.assertFalse(original.exists(), "the temp original is gone");
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	@Test
	public void copyThenDeleteRemovesTheOriginalEvenWhenTheCopyFails() throws IOException {
		File original = File.createTempFile("original", ".png");
		Files.writeString(original.toPath(), "image-bytes");
		File dir = Files.createTempDirectory("copy-target").toFile();
		try {
			// A directory where the file should go: the copy cannot succeed (whichever exception type commons-io picks).
			Assert.expectThrows(Exception.class, () -> ScreenshotUtils.copyThenDelete(original, dir));
			Assert.assertFalse(original.exists(), "a failed copy must not leave the temp original behind either");
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(dir);
		}
	}

	@Test
	public void aViewportCaptureDoesNotLeaveItsTempFile() throws IOException {
		TrackingDriver driver = new TrackingDriver();
		File outputDir = Files.createTempDirectory("shots").toFile();
		try {
			String saved = ScreenshotUtils.captureViewport(driver, outputDir.getPath());

			Assert.assertNotNull(saved, "the screenshot was saved");
			Assert.assertTrue(new File(saved).isFile(), "and the saved copy exists");
			Assert.assertEquals(driver.strays(), 0L, "the temp original must be deleted");
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(outputDir);
		}
	}
}
