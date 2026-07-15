/**
 * Browser creation: the {@code Browser} interface, its built-in implementations
 * ({@code ChromeBrowser}, {@code FireFoxBrowser}, {@code EdgeBrowser},
 * {@code RemoteGridBrowser}), the open {@code BrowserRegistry} (String-keyed,
 * supports any number of custom browsers), the small backward-compatible
 * {@code BrowserType} enum, and {@code BrowserFactory} (applies timeouts after
 * creation).
 *
 * <p><b>Stability:</b> framework internals, reached only through
 * {@code design.patterns.object.pool.WebDriverPoolFactory}.
 *
 * <p><b>Extending with a custom browser:</b> {@link design.patterns.factory.browser.BrowserRegistry}
 * is an open, {@code String}-keyed registry — register as many custom
 * {@code Browser} implementations as needed, each under its own id:
 * <pre>
 *   BrowserRegistry.register("MY_CUSTOM_BROWSER", config -&gt; new MyBrowser(...));
 *   // then: &lt;parameter name="browser" value="MY_CUSTOM_BROWSER"/&gt; in your TestNG XML
 * </pre>
 * {@code BrowserType} (the enum) is kept only as a small, stable, backward-compatible
 * surface for a caller that already holds one of its 6 constants (e.g. via
 * {@code BrowserFactory.createDriver(BrowserType, ...)}) — the pool itself no longer
 * uses it as a key. See {@code BrowserRegistry}'s Javadoc and
 * {@code docs/CODING_STANDARDS.md}.
 */
package design.patterns.factory.browser;
