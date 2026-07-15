/**
 * Configuration resolution: {@code ConfigManager}, {@code ProjectDirector},
 * {@code ConfigResolver}, and the {@code ProjectAppConfiguration} contract downstream
 * projects implement.
 *
 * <p><b>Stability:</b> {@code ProjectAppConfiguration} (and its {@code @Key}/
 * {@code @DefaultValue} conventions) is the framework's primary public extension
 * point — see {@code CONTRIBUTING.md} for how a consuming project implements it.
 * {@code ConfigManager}/{@code ProjectDirector}/{@code ConfigResolver} are framework
 * internals; downstream projects should not call them directly.
 *
 * <p><b>Multi-context registry:</b> {@code ConfigManager} (and
 * {@code design.patterns.object.pool.DriverPoolManager}) are keyed by a
 * {@code contextId} string, bound per-thread — not a plain JVM-wide singleton. Any new
 * lifecycle entry point that touches either must call
 * {@link com.framework.config.data.ConfigManager#resolveContextId} +
 * {@code bindContext(...)} on the thread it runs on before calling {@code getInstance()}.
 * See {@code docs/CODING_STANDARDS.md} — a missed entry point here caused a real
 * regression once already (the {@code fetchData()} DataProvider).
 *
 * <p><b>Known coupling (not yet fixed):</b> {@code design.patterns.*} (meant to be
 * generic, framework-agnostic infrastructure) imports this package directly from
 * {@code PoolConfig}, {@code DriverPoolManager}, {@code BrowserFactory}, and
 * {@code RemoteGridBrowser} — see {@code docs/TECHNICAL_DEBT_REGISTER.md}.
 */
package com.framework.config.data;
