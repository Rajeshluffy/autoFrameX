package com.framework.performance;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.api.design.ResponseAPI;

/**
 * A load test against a service that is down reported "100% failed" and nothing else: the exception behind each
 * failure was caught and thrown away, so a refused connection, an expired token and a bad URL all looked the same.
 * The result now keeps a few distinct reasons.
 */
public class LoadTestFailureReasonTest {

    private static ResponseAPI answering(int status) {
        return (ResponseAPI) Proxy.newProxyInstance(ResponseAPI.class.getClassLoader(), new Class<?>[] { ResponseAPI.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getStatusCode")) {
                        return status;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    public void anExceptionIsKeptAsAReason() {
        ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(() -> {
            throw new IllegalStateException("connection refused: localhost:9");
        }, 2, 1, 5);

        Assert.assertTrue(result.getFailureCount() > 0);
        Assert.assertTrue(result.getFailureSamples().contains("IllegalStateException: connection refused: localhost:9"),
                result.getFailureSamples().toString());
        Assert.assertTrue(result.toString().contains("connection refused"), "the reason shows in the report line: " + result);
    }

    @Test
    public void anErrorStatusIsKeptAsAReason() {
        ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(() -> answering(401), 2, 1, 5);

        Assert.assertEquals(result.getSuccessCount(), 0);
        Assert.assertEquals(result.getFailureSamples(), java.util.List.of("HTTP 401"));
    }

    @Test
    public void reasonsAreDistinctAndCapped() {
        AtomicInteger counter = new AtomicInteger();
        ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(() -> {
            throw new IllegalStateException("failure " + counter.incrementAndGet());
        }, 1, 1, 5);

        Assert.assertTrue(result.getFailureCount() > 5, "enough failures to exceed the cap: " + result.getFailureCount());
        Assert.assertEquals(result.getFailureSamples().size(), 5, "at most five reasons are kept");
        Assert.assertEquals(new java.util.HashSet<>(result.getFailureSamples()).size(), 5, "and they are distinct");
    }

    @Test
    public void aRunWithNoFailuresHasNoReasons() {
        ApiPerformanceUtils.LoadTestResult result = ApiPerformanceUtils.runLoadTest(() -> answering(200), 1, 1, 5);

        Assert.assertEquals(result.getFailureCount(), 0);
        Assert.assertTrue(result.getFailureSamples().isEmpty());
        Assert.assertFalse(result.toString().contains("reasons"), result.toString());
    }
}
