package design.patterns.factory.browser;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import design.patterns.object.pool.PoolConfig;

/**
 * Open, {@code String}-keyed registry of {@link BrowserProvider}s — the
 * mechanism behind every {@code browser=...} selection in this framework,
 * built-in or custom.
 *
 * <p>Pre-populated with the 6 built-in ids in the same uppercase form
 * {@code BrowserType.name()} already produces ({@code "CHROME"},
 * {@code "GRID_CHROME"}, ...), so existing log text, config-file values, and
 * resolution behavior are unchanged. A team can register any number of their
 * own browsers, each under its own id — unlike the single fixed
 * {@code BrowserType.CUSTOM} slot this replaces:
 * <pre>
 *   BrowserRegistry.register("MY_CUSTOM_BROWSER", config -&gt; new MyBrowser(...));
 *   // then: &lt;parameter name="browser" value="MY_CUSTOM_BROWSER"/&gt; in your TestNG XML
 * </pre>
 * Register ids in uppercase to match {@code DriverPoolManager}'s
 * uppercase-normalizing resolution of the {@code browser} parameter/env
 * var/system property/config value.
 */
public final class BrowserRegistry {

    private static final ConcurrentMap<String, BrowserProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        register("CHROME", config -> ChromeBrowser.getInstance());
        register("FIREFOX", config -> FireFoxBrowser.getInstance());
        register("EDGE", config -> EdgeBrowser.getInstance());
        register("GRID_CHROME", config -> new RemoteGridBrowser("chrome", config.getGridHubUrl()));
        register("GRID_FIREFOX", config -> new RemoteGridBrowser("firefox", config.getGridHubUrl()));
        register("GRID_EDGE", config -> new RemoteGridBrowser("edge", config.getGridHubUrl()));
    }

    private BrowserRegistry() {
    }

    /** Registers (or overwrites) the {@link BrowserProvider} for {@code id}. */
    public static void register(String id, BrowserProvider provider) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot register a browser under a null/blank id");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Cannot register a null BrowserProvider for id: " + id);
        }
        REGISTRY.put(id, provider);
    }

    /** Whether {@code id} has a registered provider. */
    public static boolean isRegistered(String id) {
        return id != null && REGISTRY.containsKey(id);
    }

    /**
     * Resolves the {@link Browser} registered under {@code id}, using {@code config}
     * to supply anything the provider needs (e.g. the grid hub URL).
     *
     * @throws IllegalStateException if nothing is registered under {@code id}
     */
    public static Browser resolve(String id, PoolConfig config) {
        BrowserProvider provider = REGISTRY.get(id);
        if (provider == null) {
            throw new IllegalStateException(
                    "browser=" + id + " was requested but no BrowserProvider is registered for it. "
                    + "Call BrowserRegistry.register(\"" + id + "\", yourProvider) before running tests.");
        }
        return provider.provide(config);
    }

    /** All currently-registered ids (built-in + custom), for diagnostics. */
    public static Set<String> registeredIds() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
