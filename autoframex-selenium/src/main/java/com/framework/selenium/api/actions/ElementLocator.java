package com.framework.selenium.api.actions;

import java.util.List;

import org.openqa.selenium.WebElement;

import com.framework.selenium.api.design.Locators;

/** The element lookups other page actions rely on; {@link LocatorActions} is the implementation. */
public interface ElementLocator {

    WebElement locateElement(Locators locatorType, String value);

    WebElement locateElement(String value);

    WebElement locateElement(Locators locatorType1, String value1, Locators locatorType2, String value2);

    List<WebElement> locateElements(Locators type, String value);
}
