package com.framework.selenium.api.design;

import java.util.List;

import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebElement;

import design.patterns.factory.browser.BrowserType;
import design.patterns.object.pool.WebDriverPoolFactory;

/**
 * <b>Interface Name:</b> Browser <br>
 * <b>Purpose:</b> This interface acts as a contract for all browser-level interactions 
 * within the automation framework. It abstracts the underlying WebDriver logic, 
 * ensuring consistency across different browser implementations.
 * * @author Framework Architect
 * @version 2.0
 */
public interface Browser {


	// --- Element Discovery ---

	/**
	 * Locates a single element based on a specific locator strategy.
	 * @param locatorType The strategy used (ID, XPATH, CSS, etc.).
	 * @param value The value of the locator string.
	 * @return The first matching {@link WebElement}.
	 * @throws com.framework.selenium.exception.ElementNotFoundException If no element is found
	 *                                                                    or locator type is invalid.
	 */
	public WebElement locateElement(Locators locatorType, String value);

	/**
	 * Locates an element by its ID attribute.
	 * @param value The ID value of the element to find.
	 * @return The first matching element on the current context.
	 * @throws com.framework.selenium.exception.ElementNotFoundException If no element
	 *                                                                    with the given ID is found.
	 */
	public WebElement locateElement(String value);

	/**
	 * Locates a single element based on a primary locator OR a secondary locator (fallback pattern).
	 * If the first locator fails, it will attempt to find the element using the second locator.
	 * @param locatorType1 The strategy used for the first locator.
	 * @param value1 The value of the first locator string.
	 * @param locatorType2 The strategy used for the second locator.
	 * @param value2 The value of the second locator string.
	 * @return The first matching {@link WebElement}.
	 * @throws com.framework.selenium.exception.ElementNotFoundException If no element is found
	 *                                                                    using either locator.
	 */
	public WebElement locateElement(Locators locatorType1, String value1, Locators locatorType2, String value2);


	/**
	 * Locates all elements matching the given criteria.
	 * @param locatorType The strategy used (ID, XPATH, CSS, etc.).
	 * @param value The value of the locator string.
	 * @return A List of {@link WebElement}; returns an empty list if none found.
	 */
	List<WebElement> locateElements(Locators locatorType, String value);

	// --- Wait Mechanisms (New Requirement) ---

	/**
	 * Wait for an element to be clickable before interacting.
	 * @param element The WebElement to wait for.
	 * @return 
	 */
	public  WebElement waitForClickable(WebElement element);

	/**
	 * Wait for an element to become visible in the DOM.
	 * @param element The WebElement to wait for.
	 */
	public WebElement waitForVisibility(WebElement element);

	/**
	 * Wait for an element to appear in the DOM.
	 * @param element The expected WebElement
	 */
	void waitForApperance(WebElement element);

	/**
	 * Wait for an element to disappear from the DOM.
	 * @param element The expected WebElement
	 */
	void waitForDisapperance(WebElement element);

	/**
	 * Sets a temporary implicit wait for the current driver session.
	 * @param seconds timeout in seconds
	 */
	void setImplicitWait(int seconds);

	/**
	 * Resets the implicit wait to the default value configured in the framework.
	 */
	void resetImplicitWait();

	// --- JavaScript & Advanced Actions (New Requirement) ---

	/**
	 * Executes JavaScript in the context of the currently selected frame or window.
	 * @param script The JavaScript snippet to execute.
	 * @param args The arguments to the script. May be empty.
	 * @return One of Boolean, Long, String, List, or WebElement. Or null.
	 */
	Object executeJs(String script, Object... args);

	/**
	 * Clicks an element using JavaScript (useful for hidden or obstructed elements).
	 * @param element The element to be clicked.
	 */
	void clickWithJs(WebElement element);

	// --- Alert Handling ---

	/**
	 * Switches focus to the currently active modal alert.
	 */
	void switchToAlert();

	/**
	 * Accepts the active alert (clicks OK/Yes).
	 */
	void acceptAlert();

	/**
	 * Dismisses the active alert (clicks Cancel/No).
	 */
	void dismissAlert();

	/**
	 * Retrieves the text message from the active alert.
	 * @return The text content of the alert.
	 */
	String getAlertText();

	/**
	 * This method will enter the value in the alert
	 * @param data- the data to be entered in alert
	 * @throws NoAlertPresentException
	 */
	public void typeAlert(String data);

	// --- Window and Frame Management ---

	/**
	 * Switches focus to a window based on its zero-based index.
	 * @param index The index of the window (0 is the parent).
	 */
	void switchToWindow(int index);

	/**
	 * This method will switch to the Window of interest using its title
	 * @param title The window title to be switched to first window 
	 * @return 
	 * @throws NoSuchWindowException
	 */
	public boolean switchToWindowByTitle(String title);

	/**
	 * This method will switch to the Window of interest using its url
	 * @param url The window url to be switched to first window 
	 * @return 
	 * @throws NoSuchWindowException
	 */
	public boolean switchToWindowByUrl(String url);
	/**
	 * This method will switch to the specific frame using index
	 * @param index   - The int (frame) to be switched
	 * @throws NoSuchFrameException 
	 */
	public void switchToFrame(int index);	

	/**
	 * Switches to a frame using a WebElement (e.g., an iframe element).
	 * @param ele The iframe WebElement.
	 */
	void switchToFrame(WebElement ele);

	/**
	 * This method will switch to the specific frame using Id (or) Name
	 * @param idOrName   - The String (frame) to be switched
	 * @throws NoSuchFrameException 
	 */
	public void switchToFrame(String idOrName);

	/**
	 * Switches focus to a frame using the specified XPath.
	 * @param xpath The XPath of the iframe element.
	 */
	void switchToFrameUsingXPath(String xpath);

	/**
	 * Switches focus back to the main document from a frame or window.
	 */
	void defaultContent();

	// --- Verification & Utility ---

	/**
	 * Verifies if the current browser URL matches the expected URL.
	 * @param url The expected URL string.
	 * @return true if the URL matches exactly.
	 */
	boolean verifyUrl(String url);

	/**
	 * Verifies if the current browser URL matches the expected URL.
	 * @param url The expected URL string.
	 * @return true if the URL matches contains.
	 */
	boolean verifyPartialUrl(String url);

	/**
	 * This method will verify browser actual title with expected
	 * @param title - The expected title of the browser
	 * @return true if the given object represents a String equivalent to this title, false otherwise
	 */
	public boolean verifyTitle(String title);

	/**
	 * Captures a screenshot of the current browser state.
	 * @param name The name of the file to be saved.
	 * @return The file path or base64 string of the snapshot.
	 */
	long takeSnap(String name);

	/**
	 * Closes the current active window.
	 */
	void close();

	/**
	 * Closes all windows associated with the driver session and kills the process.
	 */
	void quit();
}