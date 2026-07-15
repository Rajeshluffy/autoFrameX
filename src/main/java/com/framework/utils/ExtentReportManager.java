package com.framework.utils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

/**
 * Owns the {@link ExtentReports} instance and the per-run report folder/file
 * naming — extracted from {@link Reporter} (TD-07 composition refactor) so
 * that non-TestNG code (Cucumber's {@code CucumberRunner}, which cannot
 * extend {@code Reporter}) and reporting-adjacent utilities
 * ({@code VideoRecorder}, {@code com.framework.selenium.api.actions.ScreenshotActions})
 * can reference the same report folder without depending on
 * {@code Reporter}'s TestNG-lifecycle-annotated surface.
 *
 * <p>This formalizes a pattern that was already in effect: {@code CucumberRunner}
 * and {@code ScenarioHooks} called {@code Reporter.initReportInfrastructure(...)}/
 * {@code Reporter.folderName} as plain static members with no inheritance
 * relationship to {@code Reporter} — those calls now point here instead.
 */
public final class ExtentReportManager {

	private static final Logger logger = LoggerFactory.getLogger(ExtentReportManager.class);

	private static final String DEFAULT_FILE_NAME = "result.html";
	private static final String DATE_PATTERN = "dd-MMM-yyyy HH-mm-ss";

	private static volatile ExtentReports extent;
	public static volatile String folderName = "";
	private static volatile String reportFileName = DEFAULT_FILE_NAME;

	private ExtentReportManager() {
	}

	/**
	 * Creates the shared per-run report folder (with {@code images/} and
	 * {@code videos/} subfolders) and the ExtentReports HTML reporter, naming
	 * the report file after the test suite instead of a static name.
	 *
	 * <p>Idempotent — the first caller wins.
	 *
	 * @param suiteName the TestNG suite name (falls back to a static name if
	 *                  null/blank, e.g. when no suite context is available)
	 */
	public static synchronized void initReportInfrastructure(String suiteName) {
		if (extent != null) {
			logger.debug("ExtentReports already initialized, skipping.");
			return;
		}

		String date = new SimpleDateFormat(DATE_PATTERN).format(new Date());
		folderName = "reports/" + date;

		File folder = new File("./" + folderName);
		if (!folder.exists()) {
			folder.mkdirs();
		}

		File images = new File("./" + folderName + "/images");
		if (!images.exists()) {
			images.mkdirs();
		}

		// VideoRecorder also lazily creates per-test subfolders here, but the
		// parent should exist from run start.
		File videos = new File("./" + folderName + "/videos");
		if (!videos.exists()) {
			videos.mkdirs();
		}

		reportFileName = (suiteName != null && !suiteName.isBlank())
				? sanitizeFileName(suiteName) + ".html"
				: DEFAULT_FILE_NAME;

		// ExtentReports 5.x: ChartLocation/setChartVisibilityOnOpen/setAppendExisting
		// are all removed — the Spark template handles chart placement automatically
		// and each run overwrites the file (fresh report).
		ExtentSparkReporter sparkReporter = new ExtentSparkReporter("./" + folderName + "/" + reportFileName);
		sparkReporter.config().setTheme(Theme.STANDARD);
		sparkReporter.config().setDocumentTitle("Automation Test Report");
		sparkReporter.config().setEncoding("utf-8");
		sparkReporter.config().setReportName("Automation Test Results");

		extent = new ExtentReports();
		extent.attachReporter(sparkReporter);

		extent.setSystemInfo("Java Version", System.getProperty("java.version"));
		extent.setSystemInfo("OS", System.getProperty("os.name"));
		extent.setSystemInfo("User", System.getProperty("user.name"));

		logger.info("ExtentReports initialized at: " + folderName + "/" + reportFileName);
	}

	/** Replaces characters invalid in file names with '_'. */
	private static String sanitizeFileName(String value) {
		return value.replaceAll("[^a-zA-Z0-9_-]", "_");
	}

	/** Package-private — {@link Reporter} is the only consumer that needs the raw {@link ExtentReports} instance. */
	static ExtentReports getExtent() {
		return extent;
	}

	public static String getReportFolder() {
		return folderName;
	}

	/** Suite-name-based HTML report file name (e.g. {@code MySuite.html}), resolved by {@link #initReportInfrastructure}. */
	public static String getReportFileName() {
		return reportFileName;
	}
}
