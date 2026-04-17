package com.framework.testng.api.base;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import com.framework.config.data.ConfigManager;

/**
 * Decides whether a failing test should be retried.
 *
 * <p>Retry logic:
 * <ul>
 *   <li>Retries up to {@code ConfigManager.getConfig().getRetryLimit()} times.</li>
 *   <li>If the same source line fails twice in a row, retrying is stopped
 *       immediately — this prevents infinite retry loops when the failure is
 *       deterministic (e.g. a missing element, not a transient network issue).</li>
 *   <li>Both the retry count and the previous failing line are keyed by
 *       {@code qualifiedMethodName + paramHash} so parallel and data-driven
 *       tests are tracked independently.</li>
 * </ul>
 *
 * <p>Wired to every {@code @Test} method by {@link TestAnnotationTransformer},
 * which is registered as a TestNG listener via the service-loader file
 * {@code META-INF/services/org.testng.ITestNGListener}.
 */
public class RetryEngine implements IRetryAnalyzer {

    // Thread-safe maps — one entry per unique test invocation key
    private final Map<String, Integer> countMap       = new ConcurrentHashMap<>();
    private final Map<String, Integer> previousLineMap = new ConcurrentHashMap<>();

    @Override
    public boolean retry(ITestResult result) {
        if (result.isSuccess()) {
            result.setStatus(ITestResult.SUCCESS);
            return false;
        }

        int maxTry  = ConfigManager.getInstance().getConfig().getRetryLimit();
        String key  = buildKey(result);
        int count   = countMap.getOrDefault(key, 0);
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
