package com.framework.testng;

import org.testng.Assert;
import org.testng.annotations.Test;

public class RetryTest {
    
    // We will make it fail on line 11 every time
    @Test
    public void testSameLineFailure() {
        System.out.println("Running testSameLineFailure");
        Assert.fail("Failing on same line");
    }

    private static int attempt = 0;

    @Test
    public void testDiffLineFailure() {
        System.out.println("Running testDiffLineFailure, attempt " + attempt);
        if (attempt == 0) {
            attempt++;
            Assert.fail("Failing on line 21");
        } else if (attempt == 1) {
            attempt++;
            Assert.fail("Failing on line 24");
        } else {
            Assert.fail("Failing on line 27");
        }
    }
}
