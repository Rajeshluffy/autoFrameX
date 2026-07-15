package design.patterns.factory.browser;

import design.patterns.object.pool.PoolConfig;

/**
 * Factory for a {@link Browser}, given the current {@link PoolConfig} — the
 * registration unit for {@link BrowserRegistry}.
 *
 * <p>Receiving {@code PoolConfig} at resolution time (not baked in at
 * registration time) is what lets a grid-backed provider read
 * {@link PoolConfig#getGridHubUrl()} fresh on every driver-creation call
 * instead of reaching into {@code ConfigManager} itself — see
 * {@code RemoteGridBrowser}'s two-constructor design.
 */
@FunctionalInterface
public interface BrowserProvider {
    Browser provide(PoolConfig config);
}
