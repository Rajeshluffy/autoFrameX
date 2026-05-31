package com.framework.testng.api.base;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import com.framework.config.data.ConfigManager;

/**
 * Decides whether a failing test should be retried.
 *
 * <h3>Retry limit resolution (highest → lowest priority)</h3>
 * <ol>
 *   <li>{@link Retry#limit()} on the test method</li>
 *   <li>{@link Retry#limit()} on the test class</li>
 *   <li>{@code autoFrameX.test.retry.max.limit} in {@code frameworkConfig.properties}</li>
 * </ol>
 *
 * <h3>Safety guards</h3>
 * <ul>
 *   <li>If the same source line fails twice in a row, retrying stops immediately
 *       (deterministic failure — no point retrying).</li>
 *   <li>Retry count and previous failing line are keyed by
 *       {@code qualifiedMethodName + paramHash} so parallel and data-driven
 *       tests are tracked independently.</li>
 * </ul>
 *
 * <p>Wired to every {@code @Test} by {@link TestAnnotationTransformer}.
 */
public class RetryEngine implements IRetryAnalyzer {

    // Thread-safe maps — one entry per unique test invocation key
    private final Map<String, Integer> countMap        = new ConcurrentHashMap<>();
    private final Map<String, Integer> previousLineMap = new ConcurrentHashMap<>();

    @Override
    public boolean retry(ITestResult result) {
        if (result.isSuccess()) {
            result.setStatus(ITestResult.SUCCESS);
            return false;
        }

        int maxTry   = resolveLimit(result);
        String key   = buildKey(result);
        int count    = countMap.getOrDefault(key, 0);
        int prevLine = previousLineMap.getOrDefault(key, -1);
        int currLine = extractFailingLine(result);

        // Same line failed again → deterministic failure, stop retrying
        if (currLine != -1 && currLine == prevLine) {
            result.setStatus(ITestResult.FAILURE);
            return false;
        }

        if (count < maxTry) {
            countMap.put(key, count + 1);
            previousLineMap.put(key, currLine);
            result.setStatus(ITestResult.FAILURE);
            return true;
        }

        result.setStatus(ITestResult.FAILURE);
        return false;
    }

    /**
     * Resolves the effective retry limit for a test result.
     * Checks method annotation, then class annotation, then global config.
     */
    private int resolveLimit(ITestResult result) {
        Method method = result.getMethod().getConstructorOrMethod().getMethod();
        if (method != null) {
            Retry methodAnnotation = method.getAnnotation(Retry.class);
            if (methodAnnotation != null) {
                return Math.max(0, methodAnnotation.limit());
            }
            Retry classAnnotation = method.getDeclaringClass().getAnnotation(Retry.class);
            if (classAnnotation != null) {
                return Math.max(0, classAnnotation.limit());
            }
        }
        return ConfigManager.getInstance().getConfig().getRetryLimit();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildKey(ITestResult result) {
        StringBuilder key = new StringBuilder(result.getMethod().getQualifiedName());
        Object[] params = result.getParameters();
        if (params != null && params.length > 0) {
            key.append('_').append(java.util.Arrays.deepHashCode(params));
        }
        return key.toString();
    }

    private int extractFailingLine(ITestResult result) {
        Throwable throwable = result.getThrowable();
        if (throwable == null) {
            return -1;
        }

        StackTraceElement[] stack = throwable.getStackTrace();
        if (stack == null || stack.length == 0) {
            return -1;
        }

        // Prefer the frame that belongs to the test class itself
        if (result.getTestClass() != null) {
            String testClassName = result.getTestClass().getName();
            for (StackTraceElement frame : stack) {
                if (frame.getClassName().equals(testClassName)) {
                    return frame.getLineNumber();
                }
            }
        }

        return stack[0].getLineNumber();
    }
}
