package com.framework.utils;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * A session-less driver for wait tests: it keeps an implicit wait that the code under test can read and change,
 * and answers {@code findElements} from a script, recording the implicit wait in force at every call and how many
 * calls were made.
 */
public class FakeImplicitWaitDriver extends RemoteWebDriver {

    /** The implicit wait the code under test last set (or the initial one). */
    public volatile Duration implicit;
    /** The implicit wait in force at each {@code findElements} call. */
    public final List<Duration> implicitDuringFind = Collections.synchronizedList(new ArrayList<>());
    /** Number of {@code findElements} calls. */
    public volatile int findCalls;

    private final BiFunction<By, Integer, List<WebElement>> script;
    private final WebDriver.Options options;

    /**
     * @param initialImplicit the implicit wait to start with
     * @param script          given the locator and the 1-based number of this {@code findElements} call, returns
     *                        the elements that call finds (an empty list for "not there")
     */
    public FakeImplicitWaitDriver(Duration initialImplicit, BiFunction<By, Integer, List<WebElement>> script) {
        super();
        this.implicit = initialImplicit;
        this.script = script;
        this.options = (WebDriver.Options) Proxy.newProxyInstance(WebDriver.Options.class.getClassLoader(),
                new Class<?>[] { WebDriver.Options.class }, (proxy, method, args) -> {
                    if (method.getName().equals("timeouts")) {
                        return timeouts();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private WebDriver.Timeouts timeouts() {
        return (WebDriver.Timeouts) Proxy.newProxyInstance(WebDriver.Timeouts.class.getClassLoader(),
                new Class<?>[] { WebDriver.Timeouts.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                    case "getImplicitWaitTimeout":
                        return implicit;
                    case "implicitlyWait":
                        implicit = (Duration) args[0];
                        return proxy;
                    default:
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @Override
    public WebDriver.Options manage() {
        return options;
    }

    @Override
    public List<WebElement> findElements(By by) {
        int call;
        synchronized (this) {
            call = ++findCalls;
        }
        implicitDuringFind.add(implicit);
        return script.apply(by, call);
    }

    @Override
    public WebElement findElement(By by) {
        List<WebElement> found = findElements(by);
        if (found.isEmpty()) {
            throw new NoSuchElementException("no element for " + by);
        }
        return found.get(0);
    }

    /** A bare element, identifiable by its label. */
    public static WebElement element(String label) {
        return (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(), new Class<?>[] { WebElement.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "element(" + label + ")";
                    }
                    if (method.getName().equals("hashCode")) {
                        return label.hashCode();
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
