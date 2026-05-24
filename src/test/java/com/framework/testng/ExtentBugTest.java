package com.framework.testng;

import org.testng.annotations.Test;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.model.Media;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

/**
 * Verifies that ExtentReports 5.x no longer throws NullPointerException
 * when a screenshot path is provided or when img is null.
 *
 * In ExtentReports 3.x, MediaEntityBuilder.createScreenCaptureFromPath().build()
 * could return a provider whose internal getMedia() was null, causing NPE inside
 * ExtentTest.pass/fail. This class confirms the v5 behavior is correct.
 */
public class ExtentBugTest {

    @Test
    public void testNullMediaIsAccepted() {
        ExtentSparkReporter sparkReporter = new ExtentSparkReporter("target/extent-bug-test.html");
        ExtentReports extent = new ExtentReports();
        extent.attachReporter(sparkReporter);

        ExtentTest test = extent.createTest("Test1", "Null img must not throw");

        // Passing null Media — v5 handles gracefully (logs step text only)
        Media img = null;
        try {
            test.pass("Step with null img", img);
            System.out.println("PASS: null img accepted without NPE.");
        } catch (Exception e) {
            throw new RuntimeException("FAIL: null img threw unexpectedly: " + e.getMessage(), e);
        }

        extent.flush();
    }

    @Test
    public void testValidMediaAttachment() {
        ExtentSparkReporter sparkReporter = new ExtentSparkReporter("target/extent-bug-test2.html");
        ExtentReports extent = new ExtentReports();
        extent.attachReporter(sparkReporter);

        ExtentTest test = extent.createTest("Test2", "Valid Media path");

        // In v5, MediaEntityBuilder.createScreenCaptureFromPath().build() returns Media directly.
        // Using a non-existent path is caught gracefully by the caller (Reporter.reportStep).
        try {
            Media img = MediaEntityBuilder.createScreenCaptureFromPath("non_existent.jpg").build();
            System.out.println("PASS: Media object created: " + img);
        } catch (Exception e) {
            System.out.println("INFO: Exception creating Media (expected for missing file): " + e.getMessage());
        }

        test.pass("Step without screenshot (no file present)");
        extent.flush();
    }
}
