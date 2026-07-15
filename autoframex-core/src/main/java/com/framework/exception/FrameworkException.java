package com.framework.exception;

/**
 * Root of every exception this framework throws.
 *
 * <p>Before this existed, framework-thrown exceptions were declared ad hoc as
 * static nested classes inside unrelated utility classes ({@code RetryUtils},
 * {@code WebDriverPoolFactory}, ...), each independently extending
 * {@link RuntimeException} with no shared root — a consumer couldn't
 * {@code catch (FrameworkException e)} to handle "any error this framework
 * throws" without listing every specific subtype.
 *
 * <p>New framework-thrown exceptions should extend this rather than
 * {@link RuntimeException} directly. Existing ones
 * ({@code RetryUtils.RetryExhaustedException}, {@code RetryUtils.CircuitOpenException},
 * {@code WebDriverPoolFactory.DriverAcquisitionException},
 * {@code WebDriverPoolFactory.DriverCreationException},
 * {@code WebDriverPoolFactory.NavigationException},
 * {@link com.framework.selenium.exception.ElementNotFoundException}) now extend it —
 * a superclass swap only, their own constructors and call sites are unchanged.
 */
public class FrameworkException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FrameworkException(String message) {
        super(message);
    }

    public FrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
