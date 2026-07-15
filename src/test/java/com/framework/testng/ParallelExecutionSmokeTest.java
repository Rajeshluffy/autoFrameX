package com.framework.testng;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.TestMetadata;
import com.framework.utils.ValidationUtils;

/**
 * Runs several trivial browser tests under real {@code parallel="methods"}
 * execution (see {@code testng-parallel-smoke.xml}) to prove the WebDriver
 * pool, driver reuse, and per-thread context isolation actually hold up under
 * genuine concurrency — not just in a single-threaded suite.
 *
 * <p>Deliberately more methods than {@code thread-count} / pool size so drivers
 * must be borrowed, released, and reused under contention rather than each
 * method getting its own driver for free.
 *
 * <p>Uses a public, always-available site (no local app or mock server
 * dependency) so this suite can run in any CI environment unmodified.
 */
@TestMetadata(name = "ParallelExecutionSmokeTest", authors = "framework", category = "Smoke")
public class ParallelExecutionSmokeTest extends ProjectSpecificMethods {

    private void verifyExampleDomain() {
        getDriver().get("https://example.com");
        ValidationUtils.assertTextContains(getDriver().getTitle(), "Example Domain",
                "Parallel smoke: page title check");
    }

    @Test public void smoke01() { verifyExampleDomain(); }
    @Test public void smoke02() { verifyExampleDomain(); }
    @Test public void smoke03() { verifyExampleDomain(); }
    @Test public void smoke04() { verifyExampleDomain(); }
    @Test public void smoke05() { verifyExampleDomain(); }
    @Test public void smoke06() { verifyExampleDomain(); }
    @Test public void smoke07() { verifyExampleDomain(); }
    @Test public void smoke08() { verifyExampleDomain(); }
}
