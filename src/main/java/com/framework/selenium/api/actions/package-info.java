/**
 * Composable, single-responsibility Selenium action classes (clicks, typing,
 * waits, alerts, screenshots, windows/frames, navigation, JavaScript, file
 * upload, locators, element inspection) that {@code SeleniumBase} composes
 * and delegates to internally (TD-07 composition refactor).
 *
 * <p><b>Stability:</b> each class takes a {@code Supplier<RemoteWebDriver>}
 * (where it needs direct driver access) and a
 * {@code com.framework.utils.Reporter} for step logging, resolved at
 * call time rather than cached — safe to construct once and reuse across a
 * test's lifetime. Classes with cross-cutting needs (e.g. {@code ClickActions}
 * waiting for an element to be clickable first) take the smaller collaborator
 * class they depend on as a constructor argument, not the driver directly —
 * see each class's Javadoc for its dependencies.
 *
 * <p>{@code SeleniumBase} remains the intended entry point for anything
 * extending the framework's existing {@code Browser}/{@code Element}
 * contracts; these classes exist so a consumer that only needs one narrow
 * capability (e.g. just clicking) can compose that one class directly instead
 * of inheriting the full ~85-method surface.
 */
package com.framework.selenium.api.actions;
