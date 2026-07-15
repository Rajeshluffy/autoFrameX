package design.patterns.object.pool;

/**
 * State machine for a pooled WebDriver instance.
 *
 * <pre>
 *   IDLE ──── acquire() ────► IN_USE
 *   IN_USE ── release(false) ► IDLE       (healthy return to pool)
 *   IN_USE ── release(true)  ► POISONED   (test failed — async quit)
 * </pre>
 *
 * <p>Transitions are enforced via {@code AtomicReference.compareAndSet} inside
 * {@code PooledDriver}, so double-borrow race conditions are detected immediately
 * rather than surfacing as mysterious test failures later.
 *
 * <p><b>Why POISONED matters:</b> when a test throws, the browser may be in any
 * state — stale alert open, half-completed XHR, broken navigation history.
 * Marking the driver POISONED (rather than silently re-pooling it) prevents that
 * undefined state from contaminating the next test.
 */
public enum DriverState {

    /** Sitting in the pool queue — available to borrow. */
    IDLE,

    /** Checked out by a test thread — must not be borrowed again until released. */
    IN_USE,

    /**
     * Test threw an exception — driver may be in undefined state.
     * Must be quit asynchronously; must never be re-pooled.
     */
    POISONED
}
