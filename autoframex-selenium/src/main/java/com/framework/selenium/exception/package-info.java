/**
 * Selenium-specific exceptions thrown by {@code com.framework.selenium.api.base}.
 *
 * <p><b>Stability:</b> public API surface — consuming projects should catch these
 * explicitly rather than a bare {@code RuntimeException} where they need to
 * distinguish framework-thrown failures from their own test logic's assertions.
 *
 * <p>All exceptions here extend {@link com.framework.exception.FrameworkException},
 * the shared root for every exception this framework throws (see that package's
 * Javadoc for the full list and why it exists).
 */
package com.framework.selenium.exception;
