/**
 * Test-execution observability: correlation IDs, structured NDJSON test events, flaky-test
 * tracking, and failure categorization.
 *
 * <p><b>Stability:</b> public API surface. {@code CorrelationContext},
 * {@code TestEventCollector}, and {@code FlakyTestTracker} are wired into
 * {@code com.framework.utils.Reporter}'s TestNG lifecycle automatically — most consumers
 * never call this package directly.
 *
 * <p><b>Known limitation:</b> {@code CorrelationContext} resolves build/environment/
 * commit/container IDs once at class-load time into {@code static final} fields —
 * correct for the framework's one-shot {@code mvn test}-per-run execution model, but
 * would need revisiting if this framework were ever hosted in a long-running
 * test-execution service rather than invoked fresh per run.
 */
package com.framework.observability;
