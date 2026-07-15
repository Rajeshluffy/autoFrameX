package com.framework.testng.api.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the global retry limit for a single {@code @Test} method or class.
 *
 * <h3>Usage</h3>
 * <pre>
 * // Retry this flaky test up to 2 times (overrides global limit)
 * {@literal @}Retry(limit = 2)
 * {@literal @}Test
 * public void flakyLoginTest() { ... }
 *
 * // Never retry this destructive test
 * {@literal @}Retry(limit = 0)
 * {@literal @}Test
 * public void deleteAllRecordsTest() { ... }
 * </pre>
 *
 * <h3>Priority</h3>
 * <ol>
 *   <li>Method-level {@code @Retry} (highest)</li>
 *   <li>Class-level {@code @Retry}</li>
 *   <li>Global {@code autoFrameX.test.retry.max.limit} in
 *       {@code frameworkConfig.properties} (lowest)</li>
 * </ol>
 *
 * <p>When absent, {@link RetryEngine} falls back to the global configured limit.
 *
 * @see RetryEngine
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Retry {

    /**
     * Maximum number of retry attempts for the annotated test.
     * {@code 0} disables retries entirely.
     *
     * @return retry limit (must be &ge; 0)
     */
    int limit();
}
