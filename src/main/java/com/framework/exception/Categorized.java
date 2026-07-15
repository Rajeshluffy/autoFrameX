package com.framework.exception;

/**
 * Implemented by a {@link FrameworkException} subtype that knows its own
 * observability failure-category (see {@code com.framework.observability.FailureCategorizer}'s
 * schema: {@code ASSERTION}/{@code TIMEOUT}/{@code ELEMENT_NOT_FOUND}/{@code NETWORK}/{@code UNKNOWN}).
 *
 * <p>Exists so {@code FailureCategorizer} (a core/observability class, part of
 * {@code autoframex-core}) never needs to import a specific selenium-module
 * exception type just to categorize it — a real circular-module-dependency risk
 * this decouples (TD-20's multi-module split).
 */
public interface Categorized {
    String category();
}
