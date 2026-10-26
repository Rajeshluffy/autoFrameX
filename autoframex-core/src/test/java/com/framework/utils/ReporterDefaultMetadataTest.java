package com.framework.utils;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * A test class with neither a {@code @TestMetadata} annotation nor {@code setValues()} fields got a default
 * <em>name</em> (the class name) but a {@code null} author and category, and {@code startTestCase()} threw a
 * {@code NullPointerException} from inside ExtentReports. That runs in {@code @BeforeClass}, so every method of
 * the class was skipped, with an error that never said a metadata annotation was the missing piece.
 */
public class ReporterDefaultMetadataTest {

	public static class UnannotatedTests extends Reporter {
		@Override
		public long takeSnap() {
			return 0;
		}

		String name() {
			return testcaseName;
		}

		String author() {
			return authors;
		}

		String categoryName() {
			return category;
		}
	}

	@Test
	public void aClassWithNoMetadataStillGetsAReportNode() {
		ExtentReportManager.initReportInfrastructure("ReporterDefaultMetadataTest");
		UnannotatedTests tests = new UnannotatedTests();

		tests.startTestCase();   // used to throw NullPointerException

		Assert.assertEquals(tests.name(), "UnannotatedTests", "named after the class");
		Assert.assertNotNull(tests.author(), "an author is always set");
		Assert.assertNotNull(tests.categoryName(), "a category is always set");
	}
}
