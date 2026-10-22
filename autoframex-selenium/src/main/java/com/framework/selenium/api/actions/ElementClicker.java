package com.framework.selenium.api.actions;

import org.openqa.selenium.WebElement;

/** The clicks other page actions fall back on; {@link ClickActions} is the implementation. */
public interface ElementClicker {

    void clickWithJs(WebElement ele);

    void hoverAndClick(WebElement ele);
}
