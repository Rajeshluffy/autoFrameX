package com.framework.selenium.api.design;

import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

/**
 * <b>Interface Name:</b> Element <br>
 * <b>Purpose:</b> This interface defines all web-element level interactions 
 * performed within the automation framework. It ensures consistent behavior 
 * for clicking, typing, verifying, and handling complex UI components like dropdowns.
 * * @author Framework Architect
 * @version 2.0
 */

public interface Element {

	/**
     * Executes a JavaScript snippet on a specific web element.
     * @param js The JavaScript code to execute.
     * @param ele The element context for the script.
     */
	void executeTheScript(String js, WebElement ele);

	/**
	 * This method will click the element and take snap
	 * @param ele   - The Webelement (button/link/element) to be clicked
	 * @see locateElement method in Browser Class
	 * @throws StaleElementReferenceException
	 */
	void click(WebElement ele);


	/**
	 * This method will enter the value in the given text field 
	 * @param ele   - The Webelement (text field) in which the data to be entered
	 * @param data  - The data to be sent to the webelement
	 * @see locateElement method in Browser Class
	 * @throws ElementNotInteractable,IllegalArgumentException(throws if keysToSend is null)	
	 */
	void append(WebElement ele, String data);

	/**
	 * This method will clear the value in the given text field 
	 * @param ele   - The Webelement (text field) in which the data to be entered
	 * @see locateElement method in Browser Class
	 * @throws InvalidElementStateException	(throws if not user-editable element)	 
	 */
	void clear(WebElement ele);

	/**
	 * This method will clear and type the value in the given text field 
	 * @param ele   - The Webelement (text field) in which the data to be entered
	 * @param data  - The data to be sent to the webelement
	 * @see locateElement method in Browser Class
	 * @throws ElementNotInteractable,IllegalArgumentException(throws if keysToSend is null)		 
	 */
	void clearAndType(WebElement ele,String data);

	/**
	 * This method will get the visible text of the element
	 * @param ele   - The Webelement (button/link/element) in which text to be retrieved
	 * @see locateElement method in Browser Class
	 */
	String getElementText(WebElement ele);	

	/**
	 * This method will get the Color values of the element
	 * @param ele   - The Webelement (button/link/element) in which text to be retrieved
	 * @see locateElement method in Browser Class
	 * @return The visible text of this element.
	 */
	String getBackgroundColor(WebElement ele);

	/**
	 * This method will get the text of the element textbox
	 * @param ele   - The Webelement (button/link/element) in which text to be retrieved
	 * @see locateElement method in Browser Class
	 * @return The attribute/property's current value (or) null if the value is not set.
	 */
	String getTypedText(WebElement ele);


	/**
	 * This method will select the drop down visible text
	 * @param ele   - The Webelement (dropdown) to be selected
	 * @param value The value to be selected (visibletext) from the dropdown
	 * @see locateElement method in Browser Class 
	 * @throws NoSuchElementException
	 */
	void selectDropDownUsingText(WebElement ele, String value) ;

	/**
	 * This method will select the drop down using index
	 * @param ele   - The Webelement (dropdown) to be selected
	 * @param index The index to be selected from the dropdown
	 * @see locateElement method in Browser Class
	 * @throws NoSuchElementException
	 */
	void selectDropDownUsingIndex(WebElement ele, int index) ;

	/**
	 * This method will select the drop down using index
	 * @param ele   - The Webelement (dropdown) to be selected
	 * @param value - The value to be selected (value) from the dropdown 
	 * @see locateElement method in Browser Class
	 * @throws NoSuchElementException
	 */
	void selectDropDownUsingValue(WebElement ele, String value) ;

	/**
	 * This method will verify exact given text with actual text on the given element
	 * @param ele   - The Webelement in which the text to be need to be verified
	 * @param expectedText  - The expected text to be verified
	 * @see locateElement method in Browser Class
	 * @return true if the given object represents a String equivalent to this string, false otherwise
	 */
	boolean verifyExactText(WebElement ele, String expectedText);

	/**
	 * This method will verify given text contains actual text on the given element
	 * @param ele   - The Webelement in which the text to be need to be verified
	 * @param expectedText  - The expected text to be verified
	 * @see locateElement method in Browser Class
	 * @return true if this String represents the same sequence of characters as the specified string, false otherwise
	 */
	boolean verifyPartialText(WebElement ele, String expectedText);
	
	/**
	 * This method will verify exact given attribute's value with actual value on the given element
	 * @param ele   - The Webelement in which the attribute value to be need to be verified
	 * @param attribute  - The attribute to be checked (like value, href etc)
	 * @param value  - The value of the attribute
	 * @see locateElement method in Browser Class
	 * @return true if this String represents the same sequence of characters as the specified value, false otherwise
	 */
	boolean verifyExactAttribute(WebElement ele, String attribute, String value);

	/**
	 * This method will verify partial given attribute's value with actual value on the given element
	 * @param ele   - The Webelement in which the attribute value to be need to be verified
	 * @param attribute  - The attribute to be checked (like value, href etc)
	 * @param value  - The value of the attribute
	 * @see locateElement method in Browser Class
	 * @return true if this String represents the same sequence of characters as the specified value, false otherwise
	 * 
	 */
	void verifyPartialAttribute(WebElement ele, String attribute, String value);

	/**
	 * This method will verify if the element is visible in the DOM
	 * @param ele   - The Webelement to be checked
	 * @see locateElement method in Browser Class
	 * @return true if the element is displayed or false otherwise
	 */
	boolean verifyDisplayed(WebElement ele);

	/**
	 * This method will checking the element to be invisible
	 * @param ele   - The Webelement to be checked
	 * @see locateElement method in Browser Class
	 */
	boolean verifyDisappeared(WebElement ele);	

	/**
	 * This method will verify if the input element is Enabled
	 * @param ele   - The Webelement (Radio button, Checkbox) to be verified
	 * @return true - if the element is enabled else false
	 * 
	 * @see locateElement method in Browser Class
	 * @return True if the element is enabled, false otherwise.
	 */
	boolean verifyEnabled(WebElement ele);	

	/**
	 * This method will verify if the element (Radio button, Checkbox) is selected
	 * @param ele   - The Webelement (Radio button, Checkbox) to be verified
	 * @see locateElement method in Browser Class
	 * @return True if the element is currently selected or checked, false otherwise.
	 */
	boolean verifySelected(WebElement ele);
	
	/**
     * Performs a mouse hover action over the specified element.
     * @param ele The WebElement to hover over.
     */
    void moveToElement(WebElement ele);

    /**
     * Drags an element and drops it onto a target element.
     * @param source The element to be moved.
     * @param target The destination element.
     */
    void dragAndDrop(WebElement source, WebElement target);

	/**
	 * Gets the specified attribute of the element.
	 * @param ele The WebElement
	 * @param attributeValue The attribute to retrieve
	 * @return The attribute value string
	 */
	String getAttribute(WebElement ele, String attributeValue);

	/**
	 * Sets a slider value using JavaScript.
	 * @param slider The slider WebElement
	 * @param value The value to set
	 */
	void setSliderValueJS(WebElement slider, String value);

	/**
	 * Checks if an element is visually visible via CSS property.
	 * @param element The WebElement
	 * @return true if visibility CSS is 'visible'
	 */
	boolean isElementVisuallyVisible(WebElement element);

	/**
	 * Performs a right-click (context click) on the element.
	 * @param ele The WebElement
	 */
	void contextClick(WebElement ele);

	/**
	 * Hovers over the specified element and clicks it.
	 * @param ele The WebElement
	 */
	void hoverAndClick(WebElement ele);

	/**
	 * Performs a double click on the specified element.
	 * @param ele The WebElement
	 */
	void doubleClick(WebElement ele);

	/**
	 * Enters a date using JavaScript.
	 * @param ele The date input WebElement
	 * @param data The date string to enter
	 */
	void chooseDate(WebElement ele, String data);

	/**
	 * Uploads a file by clicking the element and using native OS controls (Robot).
	 * @param ele The upload WebElement
	 * @param filePath The absolute path of the file
	 */
	void fileUpload(WebElement ele, String filePath);

	/**
	 * Uploads a file by executing JS click on the element and using native OS controls.
	 * @param ele The upload WebElement
	 * @param filePath The absolute path of the file
	 */
	void fileUploadWithJs(WebElement ele, String filePath);

	/**
	 * Locates an element and clicks on it.
	 * @param locatorType Strategy to locate with
	 * @param value Locator value
	 */
	void click(Locators locatorType, String value);

	/**
	 * Clicks an element without taking a screenshot.
	 * @param ele The WebElement
	 */
	void clickWithNoSnap(WebElement ele);

	/**
	 * Types data into an element without clearing it.
	 * @param ele The WebElement
	 * @param data Data to input
	 */
	void type(WebElement ele, String data);

	/**
	 * Types data and presses the TAB key.
	 * @param ele The WebElement
	 * @param data Data to input
	 */
	void typeAndTab(WebElement ele, String data);

	/**
	 * Types data and presses the ENTER key.
	 * @param ele The WebElement
	 * @param data Data to input
	 */
	void typeAndEnter(WebElement ele, String data);

	/**
	 * Scrolls to the given element.
	 * @param ele The WebElement to scroll to
	 */
	void scroll(WebElement ele);
}




