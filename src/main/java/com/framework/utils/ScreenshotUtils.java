package com.framework.utils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Stateless, thread-safe screenshot utility.
 *
 * <p>Captures viewport screenshots, element-level crops, and full-page
 * screenshots via JS scroll-and-stitch. All images are written to the
 * {@code outputDir} supplied by the caller so this class has no dependency
 * on the Reporter's folder state.
 */
public final class ScreenshotUtils {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotUtils.class);
    private static final String TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss_SSS";

    private ScreenshotUtils() {}

    // =========================================================================
    // VIEWPORT SCREENSHOT
    // =========================================================================

    /**
     * Captures the current viewport and saves it as a JPEG.
     *
     * @param driver    WebDriver for the current thread
     * @param outputDir directory to write the image into (created if absent)
     * @return absolute path of the saved file, or {@code null} on failure
     */
    public static String captureViewport(RemoteWebDriver driver, String outputDir) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String path = buildPath(outputDir, "viewport");
            FileUtils.copyFile(src, new File(path));
            logger.debug("Viewport screenshot saved: " + path);
            return path;
        } catch (IOException e) {
            logger.warn("Viewport screenshot failed: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // ELEMENT SCREENSHOT
    // =========================================================================

    /**
     * Captures a screenshot cropped to the bounding box of {@code element}.
     *
     * @param element   the target WebElement
     * @param outputDir directory to write the image into
     * @return absolute path of the saved file, or {@code null} on failure
     */
    public static String captureElement(WebElement element, String outputDir) {
        try {
            File src = element.getScreenshotAs(OutputType.FILE);
            String path = buildPath(outputDir, "element");
            FileUtils.copyFile(src, new File(path));
            logger.debug("Element screenshot saved: " + path);
            return path;
        } catch (IOException e) {
            logger.warn("Element screenshot failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Locates an element by {@code locator} and captures its screenshot.
     *
     * @param driver    WebDriver for the current thread
     * @param locator   By locator for the target element
     * @param outputDir directory to write the image into
     * @return absolute path of the saved file, or {@code null} on failure
     */
    public static String captureElement(RemoteWebDriver driver, By locator, String outputDir) {
        try {
            WebElement element = driver.findElement(locator);
            return captureElement(element, outputDir);
        } catch (Exception e) {
            logger.warn("Element not found for screenshot (" + locator + "): " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // FULL-PAGE SCREENSHOT (scroll-and-stitch via JS)
    // =========================================================================

    /**
     * Captures a full-page screenshot by scrolling the page in viewport-height
     * increments and stitching the images together.
     *
     * <p>Falls back to a plain viewport capture if the JS executor is unavailable
     * or the page height cannot be determined.
     *
     * @param driver    WebDriver for the current thread
     * @param outputDir directory to write the image into
     * @return absolute path of the saved file, or {@code null} on failure
     */
    public static String captureFullPage(RemoteWebDriver driver, String outputDir) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long originalScrollY = 0;
        try {
            originalScrollY = ((Number) js.executeScript("return window.scrollY")).longValue();
            long totalHeight = ((Number) js.executeScript(
                    "return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)"))
                    .longValue();
            long viewportHeight = ((Number) js.executeScript("return window.innerHeight")).longValue();

            if (totalHeight <= viewportHeight) {
                // Page fits in one viewport — plain capture is sufficient
                return captureViewport(driver, outputDir);
            }

            // Scroll to top, capture each strip
            js.executeScript("window.scrollTo(0, 0)");
            java.util.List<java.awt.image.BufferedImage> strips = new java.util.ArrayList<>();
            int totalHeightInt = (int) totalHeight;
            int viewportHeightInt = (int) viewportHeight;

            for (int scrollY = 0; scrollY < totalHeightInt; scrollY += viewportHeightInt) {
                js.executeScript("window.scrollTo(0, " + scrollY + ")");
                sleepMs(150); // allow repaint
                File strip = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                strips.add(javax.imageio.ImageIO.read(strip));
            }

            // Stitch strips vertically
            int width = strips.get(0).getWidth();
            java.awt.image.BufferedImage stitched =
                    new java.awt.image.BufferedImage(width, totalHeightInt, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = stitched.createGraphics();
            int y = 0;
            for (java.awt.image.BufferedImage strip : strips) {
                g.drawImage(strip, 0, y, null);
                y += strip.getHeight();
            }
            g.dispose();

            String path = buildPath(outputDir, "fullpage");
            ensureDir(outputDir);
            javax.imageio.ImageIO.write(stitched, "jpg", new File(path));
            logger.debug("Full-page screenshot saved: " + path);
            return path;

        } catch (Exception e) {
            logger.warn("Full-page screenshot failed, falling back to viewport: " + e.getMessage());
            return captureViewport(driver, outputDir);
        } finally {
            try {
                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, " + originalScrollY + ")");
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // FAILURE CONTEXT CAPTURE
    // =========================================================================

    /**
     * Captures a failure evidence bundle: viewport screenshot + DOM snapshot +
     * browser console logs. Intended to be called from {@code @AfterMethod} or
     * a TestNG listener on test failure.
     *
     * @param driver    WebDriver for the current thread
     * @param outputDir directory to write artifacts into
     * @return {@link FailureEvidence} containing paths to all captured artifacts
     */
    public static FailureEvidence captureFailureEvidence(RemoteWebDriver driver, String outputDir) {
        String screenshotPath = captureViewport(driver, outputDir);
        String domPath = captureDomSnapshot(driver, outputDir);
        String consoleLogs = captureBrowserConsoleLogs(driver);
        return new FailureEvidence(screenshotPath, domPath, consoleLogs);
    }

    /**
     * Saves the current page DOM as an HTML file.
     *
     * @param driver    WebDriver for the current thread
     * @param outputDir directory to write the file into
     * @return absolute path of the saved file, or {@code null} on failure
     */
    public static String captureDomSnapshot(RemoteWebDriver driver, String outputDir) {
        try {
            String dom = driver.getPageSource();
            String path = buildPath(outputDir, "dom", ".html");
            ensureDir(outputDir);
            FileUtils.writeStringToFile(new File(path), dom, "UTF-8");
            logger.debug("DOM snapshot saved: " + path);
            return path;
        } catch (IOException e) {
            logger.warn("DOM snapshot failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves browser console logs as a formatted string.
     *
     * @param driver WebDriver for the current thread
     * @return formatted log entries, or an empty string if unavailable
     */
    public static String captureBrowserConsoleLogs(RemoteWebDriver driver) {
        try {
            org.openqa.selenium.logging.LogEntries entries =
                    driver.manage().logs().get(org.openqa.selenium.logging.LogType.BROWSER);
            if (entries == null) return "";
            StringBuilder sb = new StringBuilder();
            entries.forEach(e -> sb.append(e.getLevel()).append(" ").append(e.getMessage()).append("\n"));
            return sb.toString();
        } catch (Exception e) {
            logger.debug("Browser console logs unavailable: " + e.getMessage());
            return "";
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static String buildPath(String dir, String prefix) {
        return buildPath(dir, prefix, ".jpg");
    }

    private static String buildPath(String dir, String prefix, String ext) {
        ensureDir(dir);
        String ts = new SimpleDateFormat(TIMESTAMP_PATTERN).format(new Date());
        return dir + File.separator + prefix + "_" + ts + "_" + Thread.currentThread().getId() + ext;
    }

    private static void ensureDir(String dir) {
        File d = new File(dir);
        if (!d.exists()) d.mkdirs();
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // =========================================================================
    // VALUE OBJECT
    // =========================================================================

    /** Holds paths to all artifacts captured during a test failure. */
    public static final class FailureEvidence {
        public final String screenshotPath;
        public final String domSnapshotPath;
        public final String consoleLogs;

        FailureEvidence(String screenshotPath, String domSnapshotPath, String consoleLogs) {
            this.screenshotPath = screenshotPath;
            this.domSnapshotPath = domSnapshotPath;
            this.consoleLogs = consoleLogs;
        }

        @Override
        public String toString() {
            return "FailureEvidence{screenshot=" + screenshotPath
                    + ", dom=" + domSnapshotPath
                    + ", consoleLogs=" + (consoleLogs.isEmpty() ? "none" : consoleLogs.length() + " chars") + "}";
        }
    }
}
