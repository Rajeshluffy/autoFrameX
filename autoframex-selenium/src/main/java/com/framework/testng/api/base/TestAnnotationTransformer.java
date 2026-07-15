package com.framework.testng.api.base;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

/**
 * Registers {@link RetryEngine} as the retry analyzer on every {@code @Test}
 * method at suite startup.
 *
 * <p>Separating annotation transformation from retry decision-making follows
 * the Single Responsibility Principle: this class only wires the analyzer;
 * {@link RetryEngine} only decides whether to retry.
 *
 * <p>Registered via:
 * <pre>META-INF/services/org.testng.ITestNGListener</pre>
 */
public class TestAnnotationTransformer implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {
        annotation.setRetryAnalyzer(RetryEngine.class);
    }
}
