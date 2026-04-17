package com.framework.testng;

import org.testng.annotations.Test;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.MediaEntityModelProvider;
import com.aventstack.extentreports.MediaEntityBuilder;

public class ExtentBugTest {
    @Test
    public void testNullProvider() {
        ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter("test.html");
        ExtentReports extent = new ExtentReports();
        extent.attachReporter(htmlReporter);
        
        ExtentTest test = extent.createTest("Test1", "Desc");
        
        MediaEntityModelProvider img = null;
        try {
            System.out.println("Trying to pass null...");
            test.pass("Step 1", img);
            System.out.println("Passed null successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            System.out.println("Trying to pass an invalid build...");
            MediaEntityModelProvider badImg = MediaEntityBuilder.createScreenCaptureFromPath("non_existent.jpg").build();
            test.pass("Step 2", badImg);
            System.out.println("Passed invalid successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        extent.flush();
    }
}
