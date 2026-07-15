package com.framework.selenium.exception;

import com.framework.exception.Categorized;
import com.framework.exception.FrameworkException;

/**
 * Thrown when a WebElement cannot be located using the provided locator strategy.
 *
 * <p>This exception replaces the previous pattern of returning {@code null} from
 * {@link com.framework.selenium.api.base.SeleniumBase#locateElement(Locators, String)},
 * which could cause NullPointerException in calling code.
 *
 * <h3>Usage</h3>
 * <pre>
 * try {
 *     WebElement element = page.locateElement(Locators.XPATH, "//invalid/path");
 * } catch (ElementNotFoundException e) {
 *     // Handle missing element — log, retry, or assert failure
 *     logger.error("Could not find element: " + e.getMessage());
 *     fail("Element not found on page");
 * }
 * </pre>
 *
 * <h3>Design rationale</h3>
 * <ul>
 *   <li>Exceptions are <strong>explicit</strong> — callers must explicitly
 *       acknowledge failure (catch or propagate).</li>
 *   <li>Returning {@code null} is implicit and easily missed, leading to
 *       NullPointerException downstream.</li>
 *   <li>This aligns with the interface contract defined in
 *       {@link com.framework.selenium.api.design.Browser}.</li>
 * </ul>
 *
 * @author Framework Team
 * @version 1.0
 * @see com.framework.selenium.api.base.SeleniumBase#locateElement(Locators, String)
 */
public class ElementNotFoundException extends FrameworkException implements Categorized {

    private static final long serialVersionUID = 1L;

    @Override
    public String category() {
        return "ELEMENT_NOT_FOUND";
    }

    /**
     * Constructs an ElementNotFoundException with a descriptive message.
     *
     * @param message The reason the element was not found
     *                (e.g., "Element not found - XPATH: //button[@id='submit']")
     */
    public ElementNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs an ElementNotFoundException with a message and root cause.
     *
     * @param message The reason the element was not found
     * @param cause   The underlying exception (e.g., NoSuchElementException)
     */
    public ElementNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
