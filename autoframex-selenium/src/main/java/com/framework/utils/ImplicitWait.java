package com.framework.utils;

import java.time.Duration;
import java.util.function.Supplier;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs an action with the driver's implicit wait switched off.
 *
 * <p>The pool gives every driver an implicit wait (10 seconds by default). An explicit wait polls
 * {@code findElement}, and under an implicit wait each poll for an element that is not there blocks for the whole
 * implicit time before it can report "not found": waiting up to 5 seconds for a spinner to disappear could take
 * 10 or 20, and a fallback locator was only tried after the primary one had waited 10 seconds. Selenium's own
 * guidance is not to mix the two. The wait helpers run their polling through here.
 *
 * <p>The previous value is read first and put back afterwards, also when the action throws. If the driver cannot
 * report its implicit wait (or it is already zero) the action simply runs.
 */
public final class ImplicitWait {

    private static final Logger logger = LoggerFactory.getLogger(ImplicitWait.class);

    private ImplicitWait() {}

    /** The driver's implicit wait, or {@link Duration#ZERO} if it cannot be read. */
    public static Duration current(WebDriver driver) {
        try {
            Duration wait = driver.manage().timeouts().getImplicitWaitTimeout();
            return wait == null ? Duration.ZERO : wait;
        } catch (RuntimeException e) {
            return Duration.ZERO;
        }
    }

    /** Runs {@code action} with the implicit wait at zero and restores it afterwards. */
    public static <T> T without(WebDriver driver, Supplier<T> action) {
        Duration previous = current(driver);
        if (previous.isZero() || previous.isNegative()) {
            return action.get();
        }
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        try {
            return action.get();
        } finally {
            try {
                driver.manage().timeouts().implicitlyWait(previous);
            } catch (RuntimeException e) {
                // The session is gone (the test or the browser ended): nothing left to restore.
                logger.debug("Could not restore the implicit wait of {}: {}", previous, e.getMessage());
            }
        }
    }

    /** As {@link #without(WebDriver, Supplier)} for an action that returns nothing. */
    public static void without(WebDriver driver, Runnable action) {
        without(driver, () -> {
            action.run();
            return null;
        });
    }
}
