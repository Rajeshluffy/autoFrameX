package com.framework.utils;

import java.awt.AWTException;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.monte.media.Format;
import org.monte.media.FormatKeys.MediaType;
import org.monte.media.math.Rational;
import org.monte.screenrecorder.ScreenRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.monte.media.AudioFormatKeys.*;
import static org.monte.media.FormatKeys.*;
import static org.monte.media.VideoFormatKeys.*;

/**
 * Thin wrapper around Monte Screen Recorder for per-test AVI video capture.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   VideoRecorder vr = new VideoRecorder();
 *   vr.start("TC001_VerifyLogin");   // @BeforeMethod
 *   // ... test runs ...
 *   File video = vr.stop();          // @AfterMethod
 *   if (testPassed) video.delete();  // keep only on failure
 * </pre>
 *
 * <h3>Headless / CI</h3>
 * If {@code GraphicsEnvironment.isHeadless()} returns {@code true}, {@link #start}
 * is a no-op and {@link #stop} returns {@code null}. Tests never fail because of
 * a missing display.
 *
 * <h3>Output</h3>
 * Files are written to {@code reports/<timestamp>/videos/} — the same parent
 * folder used by screenshots. File name: {@code <testName>_<threadId>_<epochMs>.avi}.
 */
public class VideoRecorder {

    private static final Logger logger = LoggerFactory.getLogger(VideoRecorder.class);

    private static final int    FRAME_RATE       = 15;
    private static final int    KEY_FRAME_EVERY  = FRAME_RATE * 60; // every 60 s
    private static final float  QUALITY          = 1.0f;
    private static final int    COLOR_DEPTH      = 24;

    private ScreenRecorder screenRecorder;
    private File           outputDir;
    private boolean        active = false;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Starts recording the primary screen.
     *
     * @param testName used as part of the output file name
     */
    public void start(String testName) {
        if (GraphicsEnvironment.isHeadless()) {
            logger.debug("Headless environment — video recording skipped for: {}", testName);
            return;
        }

        try {
            outputDir = createOutputDir();

            GraphicsConfiguration gc = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();

            Rectangle screenBounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

            screenRecorder = new ScreenRecorder(
                gc,
                screenBounds,
                // Container format — AVI file
                new Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, MIME_AVI),
                // Video track — TechSmith lossless codec
                new Format(
                    MediaTypeKey,        MediaType.VIDEO,
                    EncodingKey,         ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
                    CompressorNameKey,   ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
                    DepthKey,            COLOR_DEPTH,
                    FrameRateKey,        Rational.valueOf(FRAME_RATE),
                    QualityKey,          QUALITY,
                    KeyFrameIntervalKey, KEY_FRAME_EVERY
                ),
                // Mouse cursor track
                new Format(
                    MediaTypeKey,  MediaType.VIDEO,
                    EncodingKey,   "black",
                    FrameRateKey,  Rational.valueOf(30)
                ),
                null,   // no audio
                outputDir
            );

            screenRecorder.start();
            active = true;
            logger.info("Video recording started for: {}", testName);

        } catch (IOException | AWTException e) {
            logger.warn("Video recording could not start for {}: {}", testName, e.getMessage());
            active = false;
        }
    }

    /**
     * Stops recording and returns the video file.
     *
     * @return the recorded {@link File}, or {@code null} if recording was not active
     */
    public File stop() {
        if (!active || screenRecorder == null) {
            return null;
        }

        try {
            screenRecorder.stop();
            active = false;

            List<File> files = screenRecorder.getCreatedMovieFiles();
            if (files != null && !files.isEmpty()) {
                File video = files.get(files.size() - 1);
                logger.info("Video recording stopped: {}", video.getAbsolutePath());
                return video;
            }

        } catch (IOException e) {
            logger.warn("Video recording stop failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Returns {@code true} if recording is currently active.
     */
    public boolean isActive() {
        return active;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private File createOutputDir() {
        String base = Reporter.folderName != null && !Reporter.folderName.isEmpty()
                ? Reporter.folderName
                : "reports/default";

        File dir = new File("./" + base + "/videos");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
