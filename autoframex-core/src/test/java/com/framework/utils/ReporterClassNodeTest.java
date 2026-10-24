package com.framework.utils;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.aventstack.extentreports.ExtentTest;

/**
 * The class's parent report node belongs to the test-class instance. It used to be a ThreadLocal set on the
 * @BeforeClass thread, so a worker thread that ran methods of two different classes filed the second class's
 * methods under the first class's node.
 */
public class ReporterClassNodeTest {

	public static class ClassA extends Reporter {
		public ClassA() {
			testcaseName = "ClassA";
			testDescription = "first class";
			authors = "framework";
			category = "Test";
		}

		@Override
		public long takeSnap() {
			return 0;
		}
	}

	public static class ClassB extends Reporter {
		public ClassB() {
			testcaseName = "ClassB";
			testDescription = "second class";
			authors = "framework";
			category = "Test";
		}

		@Override
		public long takeSnap() {
			return 0;
		}
	}

	@SuppressWarnings("unused")
	public void someTestMethod() {
	}

	private static Method aMethod() throws NoSuchMethodException {
		return ReporterClassNodeTest.class.getDeclaredMethod("someTestMethod");
	}

	@Test
	public void methodsOfTwoClassesOnOneWorkerThreadLandUnderTheirOwnClass() throws Exception {
		ExtentReportManager.initReportInfrastructure("ReporterClassNodeTest");

		ClassA a = new ClassA();
		ClassB b = new ClassB();
		a.startTestCase();   // both classes start on this thread, as TestNG's @BeforeClass would
		b.startTestCase();

		AtomicReference<String> parentOfA = new AtomicReference<>();
		AtomicReference<String> parentOfB = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();

		// One worker thread runs a method of class A, then a method of class B.
		Thread worker = new Thread(() -> {
			try {
				a.setNode(aMethod(), null);
				parentOfA.set(parentName(Reporter.captureCurrentNode()));
				b.setNode(aMethod(), null);
				parentOfB.set(parentName(Reporter.captureCurrentNode()));
			} catch (Throwable t) {
				failure.set(t);
			}
		}, "class-node-worker");
		worker.start();
		worker.join();

		Assert.assertNull(failure.get(), String.valueOf(failure.get()));
		Assert.assertEquals(parentOfA.get(), "ClassA");
		Assert.assertEquals(parentOfB.get(), "ClassB",
				"a method of ClassB must not be filed under the node of the class the thread ran before");
	}

	@Test
	public void aWorkerThreadThatNeverRanBeforeStillFindsTheClassNode() throws Exception {
		ExtentReportManager.initReportInfrastructure("ReporterClassNodeTest");

		ClassA a = new ClassA();
		a.startTestCase();                       // on this thread

		AtomicReference<String> parent = new AtomicReference<>();
		Thread worker = new Thread(() -> {
			try {
				a.setNode(aMethod(), null);      // on another one
				parent.set(parentName(Reporter.captureCurrentNode()));
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}, "class-node-fresh-worker");
		worker.start();
		worker.join();

		Assert.assertEquals(parent.get(), "ClassA",
				"the class node created in @BeforeClass is found from any thread, not only the one that created it");
	}

	private static String parentName(ExtentTest node) {
		Assert.assertNotNull(node, "setNode should have bound a method node to this thread");
		return node.getModel().getParent().getName();
	}
}
