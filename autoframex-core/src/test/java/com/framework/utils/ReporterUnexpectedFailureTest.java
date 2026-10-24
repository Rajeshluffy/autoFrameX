package com.framework.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

import org.testng.Assert;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.Test;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.framework.exception.TestStepFailedException;

/**
 * A test can end with something other than a failed report step: a plain {@code Assert} failure, a
 * {@code TimeoutException}, a {@code NullPointerException}. Those used to leave the Extent node green
 * (the earlier PASS / INFO steps) while TestNG marked the test failed, with no stack trace in the report.
 */
public class ReporterUnexpectedFailureTest {

	private static final class TestReporter extends Reporter {
		@Override
		public long takeSnap() {
			return 0;
		}

		static void noteEvidence(String fileName) {
			noteFailureEvidence(fileName);
		}
	}

	@SuppressWarnings("unused")
	public void someTestMethod() {
	}

	// ------------------------------------------------------------------- stubs

	@SuppressWarnings("unchecked")
	private static <T> T stub(Class<T> type, Function<String, Object> answers) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
			Object answer = answers.apply(method.getName());
			if (answer != null) {
				return answer;
			}
			Class<?> returns = method.getReturnType();
			if (returns == boolean.class) return false;
			if (returns == int.class) return 0;
			if (returns == long.class) return 0L;
			return null;
		});
	}

	private static ITestResult result(int status, Throwable thrown) {
		// No ITestContext / ISuite stub on purpose: those interfaces mention Guice's Injector, which is not
		// on this module's test classpath, so a proxy of them fails to load. tearDownTest builds the
		// TestEvent inside a try/catch and a missing context only costs that event, which is not under test.
		ITestNGMethod method = stub(ITestNGMethod.class, name -> "getCurrentInvocationCount".equals(name) ? 1 : null);
		return stub(ITestResult.class, name -> {
			switch (name) {
			case "getStatus": return status;
			case "isSuccess": return status == ITestResult.SUCCESS;
			case "getThrowable": return thrown;
			case "getMethod": return method;
			default: return null;
			}
		});
	}

	private static ExtentTest realNode(String name) throws IOException {
		File report = File.createTempFile("unexpected-failure", ".html");
		report.deleteOnExit();
		ExtentReports extent = new ExtentReports();
		extent.attachReporter(new ExtentSparkReporter(report.getAbsolutePath()));
		return extent.createTest(name);
	}

	private static void tearDownWithNode(ExtentTest node, ITestResult result) throws Exception {
		Method method = ReporterUnexpectedFailureTest.class.getDeclaredMethod("someTestMethod");
		Reporter.runWithNode(node, () -> new TestReporter().tearDownTest(method, result));
	}

	// ------------------------------------------------------------------- tests

	@Test
	public void anAssertionFailureIsWrittenToTheNode() throws Exception {
		ExtentTest node = realNode("assertion-failure");
		Assert.assertEquals(node.getModel().getLogs().size(), 0);

		tearDownWithNode(node, result(ITestResult.FAILURE, new AssertionError("expected [5] but found [4]")));

		Assert.assertEquals(node.getModel().getStatus(), Status.FAIL, "the node must not stay green");
		Assert.assertEquals(node.getModel().getLogs().size(), 1, "one entry carrying the exception");
	}

	@Test
	public void anUnexpectedExceptionKeepsItsStackTraceInTheReport() throws Exception {
		ExtentTest node = realNode("unexpected-exception");

		tearDownWithNode(node, result(ITestResult.FAILURE, new IllegalStateException("driver exploded")));

		Assert.assertEquals(node.getModel().getStatus(), Status.FAIL);
		var log = node.getModel().getLogs().get(0);
		Assert.assertTrue(log.hasException(), "the entry carries the exception, not just a text line");
		Assert.assertTrue(log.getException().getStackTrace().contains("driver exploded"),
				"the exception message and stack trace should be in the report entry");
	}

	@Test
	public void aFailureWithAScreenshotStillRecordsTheFailure() throws Exception {
		ExtentTest node = realNode("failure-with-screenshot");

		TestReporter.noteEvidence("viewport_1.png");     // the selenium module does this before tearing the driver down
		tearDownWithNode(node, result(ITestResult.FAILURE, new AssertionError("with a picture")));

		Assert.assertEquals(node.getModel().getStatus(), Status.FAIL);
		Assert.assertEquals(node.getModel().getLogs().size(), 1);
		Assert.assertTrue(node.getModel().getLogs().get(0).hasScreenCapture(), "the screenshot is attached to the entry");
	}

	@Test
	public void aFailedReportStepIsNotRecordedASecondTime() throws Exception {
		ExtentTest node = realNode("already-reported");

		tearDownWithNode(node, result(ITestResult.FAILURE, new TestStepFailedException("Test failed: button missing")));

		Assert.assertEquals(node.getModel().getLogs().size(), 0,
				"reportStep already wrote this failure, with its screenshot");
	}

	@Test
	public void aPassingTestIsLeftAlone() throws Exception {
		ExtentTest node = realNode("passing");

		tearDownWithNode(node, result(ITestResult.SUCCESS, null));

		Assert.assertEquals(node.getModel().getLogs().size(), 0);
	}

	@Test
	public void evidenceFromOneTestDoesNotLeakIntoTheNext() throws Exception {
		ExtentTest first = realNode("leak-first");
		TestReporter.noteEvidence("left-over.png");
		tearDownWithNode(first, result(ITestResult.SUCCESS, null));   // a pass: the note must still be cleared

		ExtentTest second = realNode("leak-second");
		tearDownWithNode(second, result(ITestResult.FAILURE, new AssertionError("no screenshot this time")));

		Assert.assertEquals(second.getModel().getLogs().size(), 1);
		Assert.assertFalse(second.getModel().getLogs().get(0).hasMedia(),
				"the earlier test's screenshot must not be attached to this one");
	}
}
