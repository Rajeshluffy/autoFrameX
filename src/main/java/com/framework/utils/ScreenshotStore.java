package com.framework.utils;

import java.io.File;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralised, environment-aware element screenshot storage.
 *
 * <h3>Folder layout</h3>
 * <pre>
 * Screenshots/
 *   QA/
 *     TC004_VerifyMODisplay/
 *       okabe_canvas.jpg          &lt;-- DataProvider: &lt;accountKey&gt;_&lt;elementName&gt;.jpg
 *       okabe_dropdown.jpg
 *   DEV/
 *     TC004_VerifyMODisplay/
 *       okabe_canvas.jpg          &lt;-- identical relative path → auto-paired for comparison
 *       okabe_dropdown.jpg
 *   Diff/
 *     TC004_VerifyMODisplay/
 *       okabe_canvas.jpg          &lt;-- red-on-dim diff image
 * </pre>
 *
 * <h3>Usage — DataProvider flow (one accountKey per Excel row)</h3>
 * <pre>
 *   ScreenshotStore.save(canvasElement, env, "TC004_VerifyMODisplay", companyUser, "canvas");
 * </pre>
 *
 * <h3>Usage — non-DataProvider flow (single account, no key needed)</h3>
 * <pre>
 *   ScreenshotStore.save(canvasElement, env, "TC001_SomeTest", "canvas");
 * </pre>
 *
 * <p>The base root defaults to {@code Screenshots/} relative to the working directory.
 * Override it with the system property {@code screenshot.root}, e.g.
 * {@code -Dscreenshot.root=target/screenshots}.
 *
 * <p>Thread-safe: all state is derived from parameters; no shared mutable state.
 */
public final class ScreenshotStore {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotStore.class);

    /** Base directory — override with -Dscreenshot.root=<path> */
    public static final String ROOT =
            System.getProperty("screenshot.root", "Screenshots");

    private ScreenshotStore() {}

    // -------------------------------------------------------------------------
    // DataProvider flow — accountKey identifies the account row
    // -------------------------------------------------------------------------

    /**
     * Captures {@code element} and saves it under
     * {@code <ROOT>/<ENV>/<testCaseName>/<accountKey>_<elementName>.jpg}.
     *
     * @param element      the WebElement to screenshot
     * @param env          environment tag, e.g. {@code "qa"} or {@code "dev"}
     * @param testCaseName test class simple name, e.g. {@code "TC004_VerifyMODisplay"}
     * @param accountKey   unique account identifier (companyUser from DataProvider row);
     *                     sanitised to remove characters invalid in file names
     * @param elementName  descriptive label for the element, e.g. {@code "canvas"}
     * @return absolute path of the saved file, or {@code null} on failure
     */
    public static String save(WebElement element,
                              String env,
                              String testCaseName,
                              String accountKey,
                              String elementName) {
        String safeKey  = sanitise(accountKey);
        String fileName = safeKey + "_" + sanitise(elementName) + ".jpg";
        return doSave(element, env, testCaseName, fileName);
    }

    // -------------------------------------------------------------------------
    // Non-DataProvider flow — no account key
    // -------------------------------------------------------------------------

    /**
     * Captures {@code element} and saves it under
     * {@code <ROOT>/<ENV>/<testCaseName>/<elementName>.jpg}.
     *
     * @param element      the WebElement to screenshot
     * @param env          environment tag, e.g. {@code "qa"} or {@code "dev"}
     * @param testCaseName test class simple name, e.g. {@code "TC001_SomeTest"}
     * @param elementName  descriptive label for the element, e.g. {@code "canvas"}
     * @return absolute path of the saved file, or {@code null} on failure
     */
    public static String save(WebElement element,
                              String env,
                              String testCaseName,
                              String elementName) {
        String fileName = sanitise(elementName) + ".jpg";
        return doSave(element, env, testCaseName, fileName);
    }

    // -------------------------------------------------------------------------
    // Path helpers (public so callers can build comparison paths consistently)
    // -------------------------------------------------------------------------

    /**
     * Returns the directory path for a given environment and test case.
     * Example: {@code Screenshots/QA/TC004_VerifyMODisplay}
     */
    public static String dirFor(String env, String testCaseName) {
        return ROOT + File.separator + env.toUpperCase()
                + File.separator + sanitise(testCaseName);
    }

    /**
     * Returns the full QA root path: {@code Screenshots/QA}
     */
    public static String qaRoot()   { return ROOT + File.separator + "QA"; }

    /**
     * Returns the full DEV root path: {@code Screenshots/DEV}
     */
    public static String devRoot()  { return ROOT + File.separator + "DEV"; }

    /**
     * Returns the full Diff root path: {@code Screenshots/Diff}
     */
    public static String diffRoot() { return ROOT + File.separator + "Diff"; }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static String doSave(WebElement element, String env,
                                 String testCaseName, String fileName) {
        String dir = dirFor(env, testCaseName);
        String path = ScreenshotUtils.captureElement(element, dir, fileName);
        if (path != null) {
            logger.info("[ScreenshotStore] Saved [{}/{}]: {}", env, testCaseName, path);
        } else {
            logger.warn("[ScreenshotStore] Failed to save [{}/{}]: {}", env, testCaseName, fileName);
        }
        return path;
    }

    /** Replaces any character that is not alphanumeric, hyphen, or underscore with '_'. */
    static String sanitise(String value) {
        if (value == null || value.isEmpty()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
