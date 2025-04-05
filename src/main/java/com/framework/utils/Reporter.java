package com.framework.utils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.lang.reflect.Method;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.MediaEntityModelProvider;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.ChartLocation;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.framework.selenium.api.base.DriverInstance;

import design.patterns.factory.browser.WebDriverFactoryInterface;
import design.patterns.object.pool.WebDriverPoolFactory;


//public abstract class Reporter extends WebDriverPoolFactory {
//
//
//	public Reporter(WebDriverFactoryInterface factory) {
//		super(factory);
//		// TODO Auto-generated constructor stub
//	}
public abstract class Reporter extends WebDriverPoolFactory {

	public Reporter(WebDriverFactoryInterface factory) {
		super(factory);
		// TODO Auto-generated constructor stub
	}

	private static ExtentReports extent;
	private static final ThreadLocal<ExtentTest> parentTest = new ThreadLocal<>();
	private static final ThreadLocal<ExtentTest> test = new ThreadLocal<>();
	private static final ThreadLocal<String> testName = new ThreadLocal<>();

	private static final String fileName = "result.html";
	private static final String pattern = "dd-MMM-yyyy HH-mm-ss";

	public String testcaseName, testDescription, authors, category;
	public static String folderName = "";

	@BeforeSuite(alwaysRun = true)
	public synchronized void startReport() {
		String date = new SimpleDateFormat(pattern).format(new Date());
		folderName = "reports/" + date;

		File folder = new File("./" + folderName);
		if (!folder.exists()) {
			folder.mkdirs();
		}

		ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter("./" + folderName + "/" + fileName);
		htmlReporter.config().setTestViewChartLocation(ChartLocation.BOTTOM);
		htmlReporter.config().setChartVisibilityOnOpen(false);
		htmlReporter.config().setTheme(Theme.STANDARD);
		htmlReporter.config().setDocumentTitle("Automation Report");
		htmlReporter.config().setEncoding("utf-8");
		htmlReporter.config().setReportName("Automation Results");
		htmlReporter.setAppendExisting(false);

		extent = new ExtentReports();
		extent.attachReporter(htmlReporter);
	}

	@BeforeClass(alwaysRun = true)
	public void startTestCase() {
		ExtentTest parent = extent.createTest(testcaseName, testDescription)
				.assignAuthor(authors)
				.assignCategory(category);
		parentTest.set(parent);
		testName.set(testcaseName);
	}

	@BeforeMethod(alwaysRun = true)
	public void setNode(Method method) {
		String methodName = method.getName();
		ExtentTest child = parentTest.get().createNode(methodName);
		test.set(child);
	}

	public abstract long takeSnap();

	public void reportStep(String desc, String status, boolean bSnap) {
		synchronized (test) {
			MediaEntityModelProvider img = null;
			if (bSnap && !(status.equalsIgnoreCase("INFO") || status.equalsIgnoreCase("SKIPPED"))) {
				long snapNumber = takeSnap();
				try {
					String path = "./../../" + folderName + "/images/" + snapNumber + ".jpg";
					img = MediaEntityBuilder.createScreenCaptureFromPath(path).build();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			switch (status.toUpperCase()) {
			case "PASS":
				test.get().pass(desc, img);
				break;
			case "FAIL":
				test.get().fail(desc, img);
				throw new RuntimeException("Test failed: See report for details.");
			case "WARNING":
				test.get().warning(desc, img);
				break;
			case "SKIPPED":
				test.get().skip("Skipped: " + desc);
				break;
			case "INFO":
				test.get().info(desc);
				break;
			default:
				test.get().info(desc);
				break;
			}
		}
	}

	public void reportStep(String desc, String status) {
		reportStep(desc, status, true);
	}

	@AfterSuite(alwaysRun = true)
	public synchronized void endReport() {
		if (extent != null) {
			extent.flush();
		}
	}

	public String getTestName() {
		return testName.get();
	}

	public Status getTestStatus() {
		return parentTest.get().getModel().getStatus();
	}


}