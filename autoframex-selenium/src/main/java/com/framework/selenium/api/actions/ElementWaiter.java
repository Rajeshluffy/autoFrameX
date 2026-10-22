package com.framework.selenium.api.actions;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/** The waits the other page actions rely on; {@link WaitActions} is the implementation. */
public interface ElementWaiter {

    WebDriverWait getWait(int timeoutInSeconds);

    <T> T waitFor(ExpectedCondition<T> condition, String errorMessage);

    <T> T waitFor(ExpectedCondition<T> condition, int timeoutInSeconds, String errorMessage);

    WebElement waitForClickable(WebElement ele);

    WebElement waitForVisibility(WebElement element);
}
