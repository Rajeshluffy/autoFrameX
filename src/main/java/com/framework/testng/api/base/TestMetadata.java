package com.framework.testng.api.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative metadata for a test class, consumed by {@link com.framework.utils.Reporter}
 * when building the ExtentReports test node.
 *
 * <p>Usage — annotate your test class instead of overriding {@code setValues()}:
 * <pre>
 * {@literal @}TestMetadata(
 *     name        = "VerifyLogin",
 *     description = "Verify login with valid credentials",
 *     authors     = "QA Team",
 *     category    = "Smoke"
 * )
 * public class TC001_VerifyLogin extends ProjectSpecificMethods { ... }
 * </pre>
 *
 * <p>If both the annotation and the legacy {@code setValues()} approach are present,
 * the annotation takes precedence so migrated classes behave correctly even when
 * {@code setValues()} is left in place temporarily.
 *
 * <p>The {@code excelFile} attribute replaces the {@code excelFileName} field for
 * data-driven tests:
 * <pre>
 * {@literal @}TestMetadata(name = "CreateLead", excelFile = "CreateLead", ...)
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TestMetadata {

    /** Short identifier shown as the test-case name in the HTML report. */
    String name();

    /** One-sentence description of what the test verifies. */
    String description() default "";

    /** Comma-separated list of authors (e.g. "Alice, Bob"). */
    String authors() default "";

    /**
     * Test category used for filtering in reports.
     * Typical values: "Smoke", "Regression", "Sanity".
     */
    String category() default "";

    /**
     * Base name of the Excel data file (without the {@code .xlsx} extension).
     * Maps to {@code ./data/<excelFile>.xlsx}. Leave empty for non-data-driven tests.
     */
    String excelFile() default "";
}
