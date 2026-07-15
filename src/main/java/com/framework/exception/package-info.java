/**
 * Shared exception hierarchy for the framework, rooted at
 * {@link com.framework.exception.FrameworkException}.
 *
 * <p>Added 2026-07-15 to unify what had been ad-hoc {@code RuntimeException}
 * subclasses declared as static nested classes inside unrelated utility classes
 * ({@code RetryUtils}, {@code WebDriverPoolFactory}) with no shared root. Those
 * existing types now extend {@code FrameworkException} (a superclass swap only —
 * their own constructors and every call site are unchanged); new framework-thrown
 * exceptions should extend it too rather than {@code RuntimeException} directly.
 *
 * <p><b>Stability:</b> public API surface — consuming projects can
 * {@code catch (FrameworkException e)} to handle "any error this framework throws"
 * without listing every specific subtype.
 */
package com.framework.exception;
