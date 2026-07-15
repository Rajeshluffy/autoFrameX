/**
 * WebDriver object pool: {@code DriverPoolManager} (Facade, context-keyed registry),
 * {@code WebDriverPoolFactory} (the actual pool — pre-warming, borrow/release,
 * CAS-based driver state machine, idle eviction), and {@code PoolConfig}.
 *
 * <p><b>Stability:</b> framework internals. Test classes reach this only indirectly,
 * through {@code com.framework.testng.api.base.ProjectSpecificMethods}/
 * {@code com.framework.cucumber.api.base.CucumberProjectBase}'s
 * {@code getDriver()}/{@code getDriverManager()}.
 *
 * <p><b>Design intent:</b> this package (and {@code design.patterns.factory.browser})
 * is meant to be generic, framework-agnostic infrastructure with no knowledge of any
 * one project's configuration — in practice it still imports
 * {@code com.framework.config.data} directly (see that package's Javadoc and
 * {@code docs/TECHNICAL_DEBT_REGISTER.md}), a known, not-yet-fully-fixed violation of
 * that intent.
 *
 * <p><b>Thread safety:</b> {@code DriverPoolManager} is bound per-thread via a
 * {@code contextId} (see {@code com.framework.config.data} package docs) so multiple
 * applications can run concurrently in one JVM. Within one context,
 * {@code WebDriverPoolFactory} is a single shared pool across all worker threads —
 * only the specific driver a thread currently holds is {@code ThreadLocal}.
 */
package design.patterns.object.pool;
