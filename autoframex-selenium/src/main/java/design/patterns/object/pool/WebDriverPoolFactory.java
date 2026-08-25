
// ============================================================================
// DRIVER POOL - PRODUCTION-GRADE IMPLEMENTATION
// ============================================================================

package design.patterns.object.pool;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;

import design.patterns.factory.browser.BrowserFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production-grade, thread-safe WebDriver object pool.
 *
 * <h3>Checklist compliance</h3>
 * <ol>
 *   <li>✅ <b>Min / max size</b> — {@code minPoolSize} drivers are pre-warmed at
 *       construction; hard cap at {@code maxPoolSize} prevents resource exhaustion.</li>
 *   <li>✅ <b>Pre-warming</b> — drivers are created eagerly so the first test never
 *       pays the browser-launch cost.</li>
 *   <li>✅ <b>State machine</b> — each {@link PooledDriver} tracks
 *       {@code IDLE → IN_USE → POISONED} via {@link AtomicReference};
 *       double-borrow is detected immediately.</li>
 *   <li>✅ <b>Blocking borrow with timeout</b> — when the pool is at capacity,
 *       {@link #acquire} blocks up to {@code borrowTimeoutSeconds} (default 30 s)
 *       before throwing {@link DriverAcquisitionException}.</li>
 *   <li>✅ <b>POISONED state</b> — failed-test drivers are marked POISONED and
 *       quit asynchronously; they are never re-pooled.</li>
 *   <li>✅ <b>Ordered state reset on return</b> — cookies → localStorage →
 *       sessionStorage → {@code about:blank} (deferred to {@link #acquire} so
 *       cookies are cleared on the correct domain).</li>
 *   <li>✅ <b>Health-check on borrow only</b> — {@code getCurrentUrl()} is called
 *       inside {@link #tryReuseFromPool}, not in {@link #release}.</li>
 *   <li>✅ <b>Async {@code quit()} via executor</b> — browser teardown runs off
 *       the test thread so reported test duration is not inflated.</li>
 *   <li>✅ <b>Reuse cap</b> — driver is retired after {@code maxReuseCount} uses
 *       (default 75) to prevent Chrome/Firefox memory and fd leaks.</li>
 *   <li>✅ <b>Strategy pattern</b> — driver creation delegated to
 *       {@link BrowserFactory}; swap Chrome ↔ Firefox ↔ Remote Grid without
 *       touching this class.</li>
 * </ol>
 *
 * @author Framework Team
 * @version 3.0
 */
public class WebDriverPoolFactory implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WebDriverPoolFactory.class);

    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;
    private static final int CLEANUP_INTERVAL_MINUTES     = 5;

    // ── Configuration + factory ──────────────────────────────────────────────
    private final PoolConfig     config;
    private final BrowserFactory driverFactory;

    // ── Pool state ───────────────────────────────────────────────────────────
    /** IDLE drivers waiting to be borrowed, keyed by browser id (see {@code BrowserRegistry}). */
    private final ConcurrentMap<String, LinkedBlockingQueue<PooledDriver>> availableDrivers;
    /** All drivers currently checked out by a test thread. */
    private final ConcurrentMap<RemoteWebDriver, PooledDriver> activeDrivers;
    /** Total live instances per browser id (IDLE + IN_USE). */
    private final ConcurrentMap<String, AtomicInteger> poolSizeCounters;

    // ── Lifecycle ────────────────────────────────────────────────────────────
    private final AtomicBoolean            isShutdown     = new AtomicBoolean(false);
    private final ScheduledExecutorService cleanupExecutor;
    private final ScheduledFuture<?>       cleanupTask;

    /**
     * Dedicated executor for async {@code driver.quit()} calls.
     * Browser teardown (2–8 s) runs here so it never inflates test duration.
     */
    private final ExecutorService quitExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "DriverPool-Quit");
        t.setDaemon(true);
        return t;
    });

    // ── Statistics ───────────────────────────────────────────────────────────
    private final PoolStatistics statistics = new PoolStatistics();

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    /**
     * Constructs and pre-warms the pool.
     *
     * @param driverFactory factory for creating new driver instances (Strategy pattern)
     * @param config        immutable pool configuration
     */
    public WebDriverPoolFactory(BrowserFactory driverFactory, PoolConfig config) {
        if (driverFactory == null) throw new IllegalArgumentException("DriverFactory cannot be null");
        if (config == null)        throw new IllegalArgumentException("PoolConfig cannot be null");

        this.driverFactory = driverFactory;
        this.config        = config;

        this.availableDrivers  = new ConcurrentHashMap<>();
        this.activeDrivers     = new ConcurrentHashMap<>();
        this.poolSizeCounters  = new ConcurrentHashMap<>();

        for (String bt : config.getSupportedBrowsers()) {
            availableDrivers.put(bt, new LinkedBlockingQueue<>());
            poolSizeCounters.put(bt, new AtomicInteger(0));
        }

        this.cleanupExecutor = createCleanupExecutor();
        this.cleanupTask     = scheduleCleanup();

        // Pre-warm — create minPoolSize drivers eagerly so the first test pays
        // no browser-launch cost.
        preWarm();

        logger.info("WebDriverPool ready: " + config);
    }

    /** Creates {@code minPoolSize} IDLE drivers per browser type at startup. */
    private void preWarm() {
        int min = config.getMinPoolSize();
        if (min <= 0) return;

        for (String bt : config.getSupportedBrowsers()) {
            int succeeded = 0;
            for (int i = 0; i < min; i++) {
                try {
                    RemoteWebDriver driver = createNewDriver(bt);
                    incrementPoolSize(bt); // createNewDriver() no longer counts itself — see its Javadoc
                    PooledDriver pd = new PooledDriver(driver, bt); // state = IDLE
                    availableDrivers.get(bt).offer(pd);
                    succeeded++;
                    logger.debug("Pre-warmed driver " + (i + 1) + "/" + min + " for " + bt);
                } catch (Exception e) {
                    logger.warn("Pre-warm failed for " + bt + " (" + (i + 1) + "): " + e.getMessage());
                }
            }
            logger.info("Pre-warm complete for " + bt + ": " + succeeded + "/" + min + " drivers"
                    + (succeeded < min ? " (degraded — see warnings above)" : ""));
        }
    }

    // =========================================================================
    // ACQUIRE
    // =========================================================================

    /**
     * Borrows a WebDriver from the pool.
     *
     * <ol>
     *   <li>Non-blocking poll — returns immediately if an IDLE driver is available.</li>
     *   <li>Create new driver if the pool is below {@code maxPoolSize}.</li>
     *   <li>Blocking poll — waits up to {@code borrowTimeoutSeconds} when the pool
     *       is at capacity; throws {@link DriverAcquisitionException} on timeout.</li>
     * </ol>
     *
     * <p>Health-check runs on borrow (inside {@link #tryReuseFromPool}), not on
     * return — checking on return is useless because the next borrower could get a
     * zombie driver after an idle TTL gap anyway.
     *
     * @param browserType browser id requested (see {@code BrowserRegistry})
     * @param url         initial URL to navigate to (may be null)
     * @return a healthy, navigated WebDriver bound to the current thread context
     */
    public RemoteWebDriver acquire(String browserType, String url) {
        validateNotShutdown();
        validateBrowserType(browserType);

        long startTime = System.currentTimeMillis();
        RemoteWebDriver driver = null;
        boolean reused = false;

        try {
            // ── Step 1: non-blocking reuse ────────────────────────────────────
            driver = tryReuseFromPool(browserType);

            if (driver != null) {
                reused = true;
                statistics.incrementReused();
            } else {
                // ── Step 2: atomically reserve a capacity slot, then create ───
                // Reservation (the counter CAS below) and driver creation are
                // split into two steps so concurrent threads racing the capacity
                // check can never both "pass" it — only one thread's CAS can move
                // the counter past a given value, so at most maxPoolSize threads
                // ever hold a reservation at once, closing the F3 TOCTOU race.
                AtomicInteger counter = poolSizeCounters.get(browserType);
                int before = counter.getAndUpdate(n -> n < config.getMaxPoolSize() ? n + 1 : n);
                if (before < config.getMaxPoolSize()) {
                    try {
                        driver = createNewDriver(browserType);
                        logger.debug("Created new driver: " + browserType
                                + " [ID: " + System.identityHashCode(driver) + "]");
                    } catch (RuntimeException e) {
                        // Creation failed — release the reservation immediately so
                        // it doesn't become a phantom slot (this is F1's failure
                        // mode: a reservation with no live driver behind it).
                        counter.decrementAndGet();
                        throw e;
                    }
                } else {
                    // ── Step 3: pool full — blocking poll with timeout ────────
                    driver = borrowWithTimeout(browserType);
                    reused = true;
                    statistics.incrementReused();
                }
            }

            // ── Step 4: navigate + state reset (on correct domain) ────────────
            if (url != null && !url.isEmpty()) {
                navigateToUrl(driver, url);
                if (reused && config.isStateResetEnabled()) {
                    // Ordered reset: cookies → localStorage → sessionStorage
                    resetDriverState(driver);
                    navigateToUrl(driver, url); // clean page load
                }
            }

            // ── Step 5: register as active (IN_USE) ───────────────────────────
            registerActive(driver, browserType);

            logger.info(String.format("Driver acquired in %dms: %s [Reused=%s, ID=%d]",
                    System.currentTimeMillis() - startTime, browserType,
                    reused, System.identityHashCode(driver)));

            return driver;

        } catch (DriverAcquisitionException e) {
            // The driver, if any, is already counted in poolSizeCounters here —
            // either freshly reserved+created above, or borrowed while still
            // live from tryReuseFromPool()/borrowWithTimeout(). Destroying it
            // without decrementing would leak a phantom slot (F1) exactly the
            // way a genuine navigation failure after a successful create did.
            if (driver != null) {
                safelyDestroyAsync(driver, browserType);
                decrementPoolSize(browserType);
            }
            throw e;
        } catch (Exception e) {
            logger.error("Failed to acquire driver: " + browserType, e);
            if (driver != null) {
                safelyDestroyAsync(driver, browserType);
                decrementPoolSize(browserType);
            }
            throw new DriverAcquisitionException("Cannot acquire driver: " + browserType, e);
        }
    }

    /**
     * Blocks up to {@code borrowTimeoutSeconds} waiting for an IDLE driver.
     * Used when the pool is at max capacity.
     */
    private RemoteWebDriver borrowWithTimeout(String browserType) {
        LinkedBlockingQueue<PooledDriver> queue = availableDrivers.get(browserType);
        try {
            PooledDriver pd = queue.poll(config.getBorrowTimeoutSeconds(), TimeUnit.SECONDS);
            if (pd == null) {
                throw new DriverAcquisitionException(
                        "Borrow timeout after " + config.getBorrowTimeoutSeconds() + "s — "
                        + browserType + " pool exhausted (max=" + config.getMaxPoolSize() + "). "
                        + "Increase maxPoolSize or reduce parallel thread count.",
                        new IllegalStateException("Pool exhausted"));
            }
            // Health-check the driver we just borrowed under timeout
            if (!performHealthCheck(pd.getDriver())) {
                logger.warn("Timeout-borrowed driver failed health check, destroying");
                safelyDestroyAsync(pd.getDriver(), browserType);
                decrementPoolSize(browserType);
                return borrowWithTimeout(browserType); // try again (recursive — guarded by timeout)
            }
            pd.transitionTo(DriverState.IDLE, DriverState.IN_USE);
            pd.markAcquired();
            return pd.getDriver();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DriverAcquisitionException("Borrow interrupted for " + browserType, e);
        }
    }

    // =========================================================================
    // RELEASE
    // =========================================================================

    /**
     * Returns or poisons a driver after test execution.
     *
     * <p>When {@code poisoned=true} (test failed): the driver is marked
     * {@link DriverState#POISONED} and queued for asynchronous {@code quit()}.
     * It is never re-pooled — a failed test may have left a stale alert, broken
     * navigation, or an in-flight XHR that would corrupt the next test.
     *
     * <p>When {@code poisoned=false} (test passed): extra windows are closed,
     * alerts dismissed, and the driver transitions back to {@link DriverState#IDLE}
     * and is enqueued for the next borrower.
     *
     * @param driver   the driver to release
     * @param poisoned {@code true} if the test failed and the driver must be discarded
     */
    public void release(RemoteWebDriver driver, boolean poisoned) {
        if (driver == null) return;

        PooledDriver pd = activeDrivers.remove(driver);

        if (pd == null) {
            logger.warn("Releasing unknown driver [ID: " + System.identityHashCode(driver) + "], destroying");
            safelyDestroyAsync(driver, null);
            return;
        }

        if (isShutdown.get() || poisoned) {
            // Poison path — async quit, never re-pool
            try {
                pd.transitionTo(DriverState.IN_USE, DriverState.POISONED);
            } catch (IllegalStateException e) {
                logger.debug("State transition warning during poison: " + e.getMessage());
            }
            safelyDestroyAsync(driver, pd.getBrowserType());
            decrementPoolSize(pd.getBrowserType());
            if (poisoned) statistics.incrementPoisoned();
            logger.info("Driver POISONED (test failed) — async quit dispatched [ID: "
                    + System.identityHashCode(driver) + "]");
            return;
        }

        // Happy path — clean up and return to pool
        try {
            dismissAlerts(driver);
            closeExtraWindows(driver);

            // Capacity guard
            LinkedBlockingQueue<PooledDriver> queue = availableDrivers.get(pd.getBrowserType());
            int totalSize = poolSizeCounters.get(pd.getBrowserType()).get();

            if (totalSize > config.getMaxPoolSize()) {
                logger.debug("Pool over capacity (" + totalSize + "/" + config.getMaxPoolSize()
                        + ") — retiring driver [ID: " + System.identityHashCode(driver) + "]");
                safelyDestroyAsync(driver, pd.getBrowserType());
                decrementPoolSize(pd.getBrowserType());
                return;
            }

            pd.markReturned();
            pd.transitionTo(DriverState.IN_USE, DriverState.IDLE);
            queue.offer(pd);
            logger.debug(String.format("Driver returned to pool: %s [IDLE=%d, ID=%d]",
                    pd.getBrowserType(), queue.size(), System.identityHashCode(driver)));

        } catch (Exception e) {
            logger.warn("Error releasing driver — destroying", e);
            safelyDestroyAsync(driver, pd.getBrowserType());
            decrementPoolSize(pd.getBrowserType());
        }
    }

    /**
     * Backwards-compatible overload used by legacy callers (e.g. {@code closeWindowsAndRelease}).
     * Delegates to {@link #release(RemoteWebDriver, boolean)} with {@code poisoned=false}.
     */
    public void release(RemoteWebDriver driver) {
        release(driver, false);
    }

    // =========================================================================
    // DESTROY (public — backward compat for DriverPoolManager.destroy())
    // =========================================================================

    /**
     * Synchronously destroys a driver (legacy / forced teardown path).
     * Prefer {@link #release(RemoteWebDriver, boolean) release(driver, true)} for
     * test-failure teardown so quit runs off the test thread.
     */
    public void destroy(RemoteWebDriver driver) {
        if (driver == null) return;
        PooledDriver pd = activeDrivers.remove(driver);
        if (pd != null) {
            safelyDestroyAsync(driver, pd.getBrowserType());
            decrementPoolSize(pd.getBrowserType());
        } else {
            safelyDestroyAsync(driver, null);
        }
    }

    // =========================================================================
    // CLOSE WINDOWS + RELEASE (used by DriverPoolManager when closeAfterEach=true)
    // =========================================================================

    /**
     * Closes all browser windows (browser disappears visually) without calling
     * {@code quit()} — the WebDriver session stays alive and is returned to the
     * pool. The next test that acquires this driver will call {@code driver.get(url)}
     * which opens a fresh window in the still-running ChromeDriver process.
     */
    public void closeWindowsAndRelease(RemoteWebDriver driver) {
        if (driver == null) return;

        try {
            Set<String> handles = driver.getWindowHandles();
            String primary = handles.iterator().next();

            for (String h : handles) {
                if (!h.equals(primary)) {
                    try { driver.switchTo().window(h).close(); } catch (Exception e) { /* best-effort */ }
                }
            }
            driver.switchTo().window(primary);
            driver.get("about:blank"); // visually "closes" the page; session alive

        } catch (Exception e) {
            logger.warn("closeWindowsAndRelease failed — falling back to poison release: " + e.getMessage());
            release(driver, true);
            return;
        }

        release(driver, false);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Tries to borrow an IDLE driver from the queue (non-blocking).
     * Enforces health-check and reuse-cap on borrow, not on return.
     */
    private RemoteWebDriver tryReuseFromPool(String browserType) {
        LinkedBlockingQueue<PooledDriver> queue = availableDrivers.get(browserType);
        PooledDriver pd = queue.poll(); // non-blocking
        if (pd == null) return null;

        // ── Health-check on borrow ────────────────────────────────────────────
        if (!performHealthCheck(pd.getDriver())) {
            logger.warn("Driver failed health-check on borrow — retiring [ID: "
                    + System.identityHashCode(pd.getDriver()) + "]");
            safelyDestroyAsync(pd.getDriver(), browserType);
            decrementPoolSize(browserType);
            return tryReuseFromPool(browserType); // try next
        }

        // ── Reuse-cap enforcement ─────────────────────────────────────────────
        if (pd.getUsageCount() >= config.getMaxReuseCount()) {
            logger.info("Driver reached reuse cap (" + pd.getUsageCount()
                    + "/" + config.getMaxReuseCount() + ") — retiring: " + browserType
                    + " [ID: " + System.identityHashCode(pd.getDriver()) + "]");
            safelyDestroyAsync(pd.getDriver(), browserType);
            decrementPoolSize(browserType);
            return tryReuseFromPool(browserType); // try next
        }

        pd.transitionTo(DriverState.IDLE, DriverState.IN_USE);
        pd.markAcquired();
        return pd.getDriver();
    }

    /**
     * Launches a new browser process. Does <b>not</b> touch {@link #poolSizeCounters} —
     * capacity accounting is the caller's responsibility (an atomic CAS reservation
     * in {@link #acquire}'s Step 2, or a direct {@link #incrementPoolSize} call in
     * {@link #preWarm}), since only the caller knows whether it already reserved
     * the slot this creation is filling.
     */
    private RemoteWebDriver createNewDriver(String browserType) {
        try {
            RemoteWebDriver driver = driverFactory.createDriver(browserType, config);
            statistics.incrementCreated();
            return driver;
        } catch (Exception e) {
            statistics.incrementFailed();
            throw new DriverCreationException("Failed to create driver: " + browserType, e);
        }
    }

    private void registerActive(RemoteWebDriver driver, String browserType) {
        PooledDriver pd = activeDrivers.computeIfAbsent(driver,
                k -> new PooledDriver(k, browserType));
        // Transition: new drivers start IDLE, then must go IN_USE
        if (pd.getState() == DriverState.IDLE) {
            pd.transitionTo(DriverState.IDLE, DriverState.IN_USE);
        }
        pd.markAcquired();
        activeDrivers.put(driver, pd);
    }

    private void navigateToUrl(RemoteWebDriver driver, String url) {
        int maxRetries = 3;
        Exception last = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                driver.get(url);
                return;
            } catch (Exception e) {
                last = e;
                logger.warn("Navigation attempt " + attempt + " failed: " + url);
                if (attempt < maxRetries) sleep(1000);
            }
        }
        throw new NavigationException("Failed to navigate after " + maxRetries + " attempts: " + url, last);
    }

    /**
     * Ordered state reset — must run AFTER navigating to the target domain so
     * that cookie deletion applies to the correct origin.
     * Order: 1) cookies  2) localStorage  3) sessionStorage
     */
    private void resetDriverState(RemoteWebDriver driver) {
        try { driver.manage().deleteAllCookies(); }
        catch (Exception e) { logger.debug("Cookie clear failed: " + e.getMessage()); }

        if (driver instanceof JavascriptExecutor) {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            try { js.executeScript("window.localStorage.clear();"); }
            catch (Exception e) { logger.debug("localStorage clear failed: " + e.getMessage()); }

            try { js.executeScript("window.sessionStorage.clear();"); }
            catch (Exception e) { logger.debug("sessionStorage clear failed: " + e.getMessage()); }
        }
        logger.trace("State reset complete");
    }

    /** Health-check on borrow — ~50 ms cost prevents zombie sessions reaching test code. */
    private boolean performHealthCheck(RemoteWebDriver driver) {
        if (!config.isHealthCheckEnabled()) return true;
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            exec.submit(driver::getCurrentUrl).get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            logger.debug("Health check failed: " + e.getMessage());
            return false;
        } finally {
            exec.shutdownNow();
        }
    }

    private void dismissAlerts(RemoteWebDriver driver) {
        try { driver.switchTo().alert().dismiss(); } catch (Exception e) { /* no alert */ }
    }

    /** Closes every window except the primary one before returning driver to pool. */
    private void closeExtraWindows(RemoteWebDriver driver) {
        try {
            Set<String> handles = driver.getWindowHandles();
            if (handles.size() <= 1) return;
            String primary = handles.iterator().next();
            for (String h : handles) {
                if (!h.equals(primary)) {
                    try { driver.switchTo().window(h).close(); } catch (Exception e) { /* best-effort */ }
                }
            }
            driver.switchTo().window(primary);
            logger.debug("Closed " + (handles.size() - 1) + " extra window(s)");
        } catch (Exception e) {
            logger.warn("Extra-window cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Submits {@code driver.quit()} to the dedicated executor so the test thread
     * is never blocked by browser teardown (2–8 s).
     */
    private void safelyDestroyAsync(RemoteWebDriver driver, String browserType) {
        quitExecutor.submit(() -> {
            try {
                driver.quit();
                statistics.incrementDestroyed();
                logger.debug("Driver quit (async): " + (browserType != null ? browserType : "unknown")
                        + " [ID: " + System.identityHashCode(driver) + "]");
            } catch (Exception e) {
                logger.debug("Async quit error", e);
            }
        });
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    private void cleanupExpiredDrivers() {
        if (isShutdown.get()) return;
        int cleaned = 0;

        for (Map.Entry<String, LinkedBlockingQueue<PooledDriver>> entry : availableDrivers.entrySet()) {
            String bt = entry.getKey();
            LinkedBlockingQueue<PooledDriver> queue = entry.getValue();
            List<PooledDriver> expired = new ArrayList<>();

            for (PooledDriver pd : queue) {
                if (pd.isExpired(config.getMaxIdleMinutes())) expired.add(pd);
            }
            for (PooledDriver pd : expired) {
                if (queue.remove(pd)) {
                    safelyDestroyAsync(pd.getDriver(), bt);
                    decrementPoolSize(bt);
                    cleaned++;
                }
            }
        }
        if (cleaned > 0) logger.info("Cleanup: retired " + cleaned + " expired driver(s)");
    }

    private ScheduledExecutorService createCleanupExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DriverPool-Cleanup");
            t.setDaemon(true);
            return t;
        });
    }

    private ScheduledFuture<?> scheduleCleanup() {
        return cleanupExecutor.scheduleWithFixedDelay(
                this::cleanupExpiredDrivers,
                CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    public String getStatistics() {
        StringBuilder sb = new StringBuilder("=== WebDriver Pool Statistics ===\n");

        for (String bt : config.getSupportedBrowsers()) {
            int idle   = availableDrivers.get(bt).size();
            int total  = poolSizeCounters.get(bt).get();
            int inUse  = total - idle;
            sb.append(String.format("%-10s: IDLE=%-3d IN_USE=%-3d TOTAL=%-3d (max=%d, min=%d)%n",
                    bt, idle, Math.max(inUse, 0), total,
                    config.getMaxPoolSize(), config.getMinPoolSize()));
        }

        long poisonedActive = activeDrivers.values().stream()
                .filter(pd -> pd.getState() == DriverState.POISONED).count();
        if (poisonedActive > 0) sb.append("POISONED (pending quit): ").append(poisonedActive).append("\n");

        sb.append("\n").append(statistics);
        return sb.toString();
    }

    // =========================================================================
    // CLOSE
    // =========================================================================

    @Override
    public void close() {
        if (!isShutdown.compareAndSet(false, true)) return;

        logger.info("Shutting down WebDriver pool...\n" + getStatistics());

        if (cleanupTask != null) cleanupTask.cancel(false);

        shutdownExecutor(cleanupExecutor, "DriverPool-Cleanup");

        // Destroy all active (IN_USE / POISONED) drivers
        activeDrivers.values().parallelStream()
                .forEach(pd -> safelyDestroyAsync(pd.getDriver(), pd.getBrowserType()));
        activeDrivers.clear();

        // Destroy all IDLE drivers
        availableDrivers.values().parallelStream()
                .flatMap(Collection::stream)
                .forEach(pd -> safelyDestroyAsync(pd.getDriver(), pd.getBrowserType()));
        availableDrivers.values().forEach(LinkedBlockingQueue::clear);

        // Wait for all async quit() calls to complete
        shutdownExecutor(quitExecutor, "DriverPool-Quit");

        logger.info("Pool shutdown complete");
    }

    private void shutdownExecutor(ExecutorService exec, String name) {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn(name + " did not terminate in 30 s — forcing shutdown");
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            exec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private void validateNotShutdown() {
        if (isShutdown.get()) throw new IllegalStateException("Pool has been shutdown");
    }

    private void validateBrowserType(String bt) {
        if (!config.getSupportedBrowsers().contains(bt))
            throw new IllegalArgumentException("Unsupported browser: " + bt);
    }

    private void incrementPoolSize(String bt) { poolSizeCounters.get(bt).incrementAndGet(); }
    private void decrementPoolSize(String bt) { poolSizeCounters.get(bt).decrementAndGet(); }

    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Pooled driver wrapper with state machine, usage tracking, and idle TTL.
     */
    static class PooledDriver {

        private final RemoteWebDriver driver;
        private final String          browserType;
        private final Instant         createdAt;
        private final AtomicInteger   usageCount = new AtomicInteger(0);

        /**
         * State machine — transitions enforced atomically.
         * Initial state is {@link DriverState#IDLE} (pre-warmed or newly created).
         */
        private final AtomicReference<DriverState> state =
                new AtomicReference<>(DriverState.IDLE);

        private volatile Instant lastUsedAt;

        PooledDriver(RemoteWebDriver driver, String browserType) {
            this.driver      = driver;
            this.browserType = browserType;
            this.createdAt   = Instant.now();
            this.lastUsedAt  = createdAt;
        }

        /**
         * Atomically transitions from {@code expected} to {@code next}.
         *
         * @throws IllegalStateException if the current state is not {@code expected}
         *         (indicates a double-borrow or illegal lifecycle usage)
         */
        void transitionTo(DriverState expected, DriverState next) {
            if (!state.compareAndSet(expected, next)) {
                throw new IllegalStateException(String.format(
                        "Invalid state transition %s → %s (current=%s) [ID=%d]",
                        expected, next, state.get(), System.identityHashCode(driver)));
            }
        }

        DriverState getState()     { return state.get(); }
        RemoteWebDriver getDriver()  { return driver; }
        String getBrowserType()     { return browserType; }
        int getUsageCount()          { return usageCount.get(); }

        void markAcquired() {
            usageCount.incrementAndGet();
            lastUsedAt = Instant.now();
        }

        void markReturned() {
            lastUsedAt = Instant.now();
        }

        boolean isExpired(int maxIdleMinutes) {
            return Duration.between(lastUsedAt, Instant.now()).toMinutes() >= maxIdleMinutes;
        }
    }

    /**
     * Lifecycle counters for the pool.
     */
    private static class PoolStatistics {
        private final AtomicInteger created  = new AtomicInteger(0);
        private final AtomicInteger destroyed= new AtomicInteger(0);
        private final AtomicInteger reused   = new AtomicInteger(0);
        private final AtomicInteger failed   = new AtomicInteger(0);
        private final AtomicInteger poisoned = new AtomicInteger(0);

        void incrementCreated()   { created.incrementAndGet(); }
        void incrementDestroyed() { destroyed.incrementAndGet(); }
        void incrementReused()    { reused.incrementAndGet(); }
        void incrementFailed()    { failed.incrementAndGet(); }
        void incrementPoisoned()  { poisoned.incrementAndGet(); }

        @Override
        public String toString() {
            int c = created.get();
            double reuseRate = c > 0 ? reused.get() * 100.0 / c : 0.0;
            return String.format(
                    "Created=%d, Destroyed=%d, Reused=%d, Poisoned=%d, Failed=%d, ReuseRate=%.1f%%",
                    c, destroyed.get(), reused.get(), poisoned.get(), failed.get(), reuseRate);
        }
    }

    // =========================================================================
    // EXCEPTIONS
    // =========================================================================

    public static class DriverAcquisitionException extends com.framework.exception.FrameworkException {
        public DriverAcquisitionException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static class DriverCreationException extends com.framework.exception.FrameworkException {
        public DriverCreationException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static class NavigationException extends com.framework.exception.FrameworkException {
        public NavigationException(String msg, Throwable cause) { super(msg, cause); }
    }
}
