package com.framework.utils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe, per-test MP4 video recorder using WebDriver screenshots + FFmpeg.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>On {@link #start}, a background scheduler captures the browser viewport at
 *       {@value #CAPTURE_FPS} frames/second, saving images as
 *       {@code frame_00001.png, frame_00002.png, …} inside a thread-private temp directory.</li>
 *   <li>On {@link #stop(boolean)}, the scheduler is shut down and FFmpeg assembles
 *       the image sequence into an H.264 MP4. The {@value #OUTPUT_FPS}-fps output means
 *       each captured frame occupies {@value #OUTPUT_FPS}/{@value #CAPTURE_FPS} =
 *       {@value #FRAMES_PER_CAPTURE} output frames, giving smooth playback.</li>
 *   <li>The frame temp directory is deleted after a successful encode.</li>
 * </ol>
 *
 * <h3>Parallel isolation</h3>
 * Each instance writes to its own subdirectory:
 * <pre>
 *   reports/&lt;run&gt;/videos/frames_t&lt;threadId&gt;_&lt;epoch&gt;_&lt;testName&gt;/
 * </pre>
 *
 * <h3>Pass / fail retention</h3>
 * <ul>
 *   <li>{@code stop(true)}  — frames are deleted; no video is produced. Returns {@code null}.</li>
 *   <li>{@code stop(false)} — frames are assembled into MP4 in the shared {@code videos/} folder.
 *       Returns the {@code .mp4} {@link File}.</li>
 * </ul>
 *
 * <h3>CI / headless</h3>
 * WebDriver screenshot capture works in headless Chrome/Edge, so this recorder functions
 * in CI environments where the old Monte Screen Recorder (which required a display) could not.
 *
 * <h3>FFmpeg requirement</h3>
 * FFmpeg must be on the system {@code PATH}, or its path set via the
 * {@code ffmpeg.path} system property (e.g. {@code -Dffmpeg.path=/usr/bin/ffmpeg}).
 *
 * <h3>Lifecycle (used from {@code ProjectSpecificMethods})</h3>
 * <pre>
 *   // @BeforeMethod
 *   VideoRecorder vr = new VideoRecorder();
 *   vr.start(method.getName(), getDriver());
 *   videoRecorder.set(vr);
 *
 *   // @AfterMethod
 *   File mp4 = vr.stop(result.isSuccess());   // null on pass, mp4 path on fail
 *   videoRecorder.remove();
 * </pre>
 */
public class VideoRecorder {

    private static final Logger logger = LoggerFactory.getLogger(VideoRecorder.class);

    // ---- Capture / encode settings ------------------------------------------
    /** Screenshots captured per second during test execution. */
    private static final int CAPTURE_FPS      = 2;
    /** Output video frame rate — each captured frame lasts OUTPUT_FPS/CAPTURE_FPS frames. */
    private static final int OUTPUT_FPS       = 24;
    /** Number of output frames each captured image occupies (for smooth playback). */
    static final int FRAMES_PER_CAPTURE = OUTPUT_FPS / CAPTURE_FPS;

    private static final String FRAME_PREFIX  = "frame_";
    // Selenium's getScreenshotAs(FILE) always returns PNG bytes — the frame
    // extension must match so FFmpeg's image2 demuxer picks the right decoder.
    private static final String FRAME_PATTERN = "frame_%05d.png"; // FFmpeg glob input

    // ---- FFmpeg executable --------------------------------------------------
    // Override with -Dffmpeg.path=/absolute/path/to/ffmpeg if not on system PATH.
    private static final String FFMPEG = System.getProperty("ffmpeg.path", "ffmpeg");

    // ---- Per-instance state (one VideoRecorder = one test thread) -----------
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>        captureFuture;
    private RemoteWebDriver           driver;
    private File                      framesDir;
    private String                    testName;
    private long                      threadId;
    private final AtomicInteger       frameCounter = new AtomicInteger(0);
    private volatile boolean          active       = false;
    private volatile String           lastError    = null;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Starts frame capture for the given test.
     *
     * @param testName method name used in the output file name and log messages
     * @param driver   WebDriver whose viewport will be captured each tick
     */
    public void start(String testName, RemoteWebDriver driver) {
        if (driver == null) {
            lastError = "driver was null at start — no frames captured";
            logger.warn("VideoRecorder.start: driver is null — recording skipped for: {}", testName);
            return;
        }

        this.testName  = testName;
        this.driver    = driver;
        this.threadId  = Thread.currentThread().getId();

        framesDir = createFramesDir(testName);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "video-capture-t" + threadId);
            t.setDaemon(true);
            return t;
        });

        captureFuture = scheduler.scheduleAtFixedRate(
                this::captureFrame,
                0L,
                1000L / CAPTURE_FPS,
                TimeUnit.MILLISECONDS);

        active = true;
        logger.info("[Thread-{}] Video capture started for: {} ({}fps → {}fps output)",
                threadId, testName, CAPTURE_FPS, OUTPUT_FPS);
    }

    /**
     * Stops capture and applies the pass/fail retention policy.
     *
     * <ul>
     *   <li><b>passed = true</b> — frames deleted, no MP4. Returns {@code null}.</li>
     *   <li><b>passed = false</b> — frames assembled into MP4. Returns the {@code .mp4} file.</li>
     * </ul>
     *
     * @param passed {@code true} if the test passed
     * @return the MP4 file (fail path only), or {@code null}
     */
    public File stop(boolean passed) {
        if (!active) return null;

        stopScheduler();
        active = false;

        int captured = frameCounter.get();
        logger.debug("[Thread-{}] Capture stopped: {} frames collected for '{}'",
                threadId, captured, testName);

        if (passed) {
            deleteFramesDir();
            logger.debug("[Thread-{}] Video discarded (test passed): {}", threadId, testName);
            return null;
        }

        if (captured == 0) {
            lastError = "no frames were captured (driver may have been unavailable during recording)";
            logger.warn("[Thread-{}] No frames captured for '{}' — skipping video assembly",
                    threadId, testName);
            deleteFramesDir();
            return null;
        }

        return assembleVideo();
    }

    /**
     * Backward-compatible overload — stops capture and always attempts MP4 assembly.
     * Prefer {@link #stop(boolean)} for cleaner lifecycle ownership.
     *
     * @return the MP4 file, or {@code null} on failure or if no frames were captured
     */
    public File stop() {
        if (!active) return null;
        stopScheduler();
        active = false;
        if (frameCounter.get() == 0) {
            deleteFramesDir();
            return null;
        }
        return assembleVideo();
    }

    /** Returns {@code true} if recording is currently active. */
    public boolean isActive() {
        return active;
    }

    // =========================================================================
    // FRAME CAPTURE (runs on background scheduler thread)
    // =========================================================================

    private void captureFrame() {
        try {
            File tmp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

            // Sequence number (and frameCounter itself) is only assigned/incremented
            // after a frame genuinely lands on disk. Incrementing before the attempt
            // let a driver that fails every screenshot (e.g. died mid-test) still
            // report a nonzero frameCounter — stop()'s "zero frames captured" guard
            // then couldn't tell "no frames" from "N failed attempts", and
            // assembleVideo() would invoke FFmpeg against an empty frames directory
            // instead of skipping cleanly. This executor is single-threaded
            // (newSingleThreadScheduledExecutor), so no concurrent captureFrame()
            // call can ever race this get()-then-later-increment.
            int seq = frameCounter.get() + 1;
            String name = FRAME_PREFIX + String.format("%05d", seq) + ".png";
            File dest = new File(framesDir, name);

            FileUtils.copyFile(tmp, dest);
            frameCounter.incrementAndGet();

        } catch (Exception e) {
            // Never propagate from a ScheduledExecutorService — it would cancel future ticks.
            logger.debug("[Thread-{}] Frame capture skipped: {}", threadId, e.getMessage());
        }
    }

    // =========================================================================
    // FFMPEG ASSEMBLY
    // =========================================================================

    /**
     * Invokes FFmpeg to stitch the captured JPEG sequence into an H.264 MP4.
     *
     * <p>FFmpeg command:
     * <pre>
     *   ffmpeg -y
     *          -framerate {CAPTURE_FPS}
     *          -i "frame_%05d.jpg"
     *          -c:v libx264
     *          -pix_fmt yuv420p
     *          -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2"
     *          -r {OUTPUT_FPS}
     *          output.mp4
     * </pre>
     * The {@code scale} filter pads width and height to even numbers (required by libx264).
     */
    private File assembleVideo() {
        String safeName = testName.replaceAll("[^a-zA-Z0-9_-]", "_");
        long   epoch    = System.currentTimeMillis();
        File   mp4      = new File(framesDir.getParentFile(),
                safeName + "_t" + threadId + "_" + epoch + ".mp4");

        String inputPattern = new File(framesDir, FRAME_PATTERN).getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(
                FFMPEG,
                "-y",
                "-framerate", String.valueOf(CAPTURE_FPS),
                "-i",          inputPattern,
                "-c:v",        "libx264",
                "-pix_fmt",    "yuv420p",
                "-vf",         "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                "-r",          String.valueOf(OUTPUT_FPS),
                mp4.getAbsolutePath()
        );
        pb.redirectErrorStream(true); // merge stderr into stdout for a single log stream

        try {
            logger.info("[Thread-{}] Assembling video from {} frames → {} (FFmpeg: {})",
                    threadId, frameCounter.get(), mp4.getName(), FFMPEG);

            Process proc = pb.start();

            // Capture FFmpeg output for diagnostic logging on failure
            StringBuilder ffmpegOutput = new StringBuilder();
            Thread drainer = new Thread(() -> {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        ffmpegOutput.append(line).append('\n');
                    }
                } catch (IOException ignored) {}
            }, "ffmpeg-drain-t" + threadId);
            drainer.setDaemon(true);
            drainer.start();

            boolean finished = proc.waitFor(5, TimeUnit.MINUTES);
            drainer.join(5_000); // let the drainer finish reading before we inspect output

            if (!finished) {
                proc.destroyForcibly();
                lastError = "FFmpeg process timed out (5-minute limit)";
                logger.warn("[Thread-{}] FFmpeg timed out for '{}' — destroying process",
                        threadId, testName);
                deleteFramesDir();
                return null;
            }

            int exit = proc.exitValue();
            if (exit != 0) {
                lastError = "FFmpeg exited with code " + exit + ". Output: "
                        + ffmpegOutput.toString().lines()
                                      .filter(l -> l.contains("Error") || l.contains("error") || l.contains("Invalid"))
                                      .findFirst().orElse("see logs for full output");
                logger.warn("[Thread-{}] FFmpeg exited with code {} for '{}':\n{}",
                        threadId, exit, testName, ffmpegOutput);
                deleteFramesDir();
                return null;
            }

            logger.info("[Thread-{}] MP4 saved for failure analysis: {}",
                    threadId, mp4.getAbsolutePath());
            deleteFramesDir();
            return mp4;

        } catch (IOException e) {
            lastError = "FFmpeg could not be launched ('" + FFMPEG + "'): " + e.getMessage();
            logger.warn("[Thread-{}] {}", threadId, lastError);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = "video assembly was interrupted";
            logger.warn("[Thread-{}] Video assembly interrupted for '{}'", threadId, testName);
        }

        deleteFramesDir();
        return null;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private File createFramesDir(String testName) {
        String base = (ExtentReportManager.folderName != null && !ExtentReportManager.folderName.isEmpty())
                ? ExtentReportManager.folderName
                : "reports/default";

        long   epoch    = System.currentTimeMillis();
        String safeName = testName.replaceAll("[^a-zA-Z0-9_-]", "_");

        File dir = new File("./" + base + "/videos/frames_t" + threadId + "_" + epoch + "_" + safeName);
        dir.mkdirs();
        return dir;
    }

    private void stopScheduler() {
        if (captureFuture != null) captureFuture.cancel(false);
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void deleteFramesDir() {
        if (framesDir == null || !framesDir.exists()) return;
        File[] frames = framesDir.listFiles();
        if (frames != null) {
            Arrays.stream(frames)
                  .sorted(Comparator.comparing(File::getName))
                  .forEach(f -> { if (!f.delete()) logger.debug("Could not delete frame: {}", f.getName()); });
        }
        if (!framesDir.delete()) {
            logger.debug("[Thread-{}] Could not delete frames dir: {}", threadId, framesDir.getAbsolutePath());
        }
    }
}
