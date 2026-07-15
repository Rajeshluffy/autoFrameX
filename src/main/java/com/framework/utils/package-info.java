/**
 * Cross-cutting utility classes consumed by every layer of the framework.
 *
 * <p><b>Stability:</b> public API surface, safe for downstream projects to call
 * directly (e.g. {@code WaitUtils}, {@code ValidationUtils}, {@code EncryptionUtils}).
 *
 * <p><b>Thread safety varies by class</b> — check each class's own Javadoc.
 * Truly stateless helpers ({@code WaitUtils}, {@code ValidationUtils},
 * {@code EncryptionUtils}, {@code LogUtils}, {@code ScreenshotUtils}) are {@code final}
 * with a private constructor. Classes carrying real ThreadLocal or cache state
 * ({@code FakerDataFactory}, {@code DataLibrary}, {@code Reporter}, {@code VideoRecorder})
 * document their own lifecycle/cleanup contract — see
 * {@code docs/CODING_STANDARDS.md} for the convention.
 *
 * <p><b>Package boundary:</b> this package must never import
 * {@code com.framework.testng.api.base} or {@code com.framework.selenium.api.base} —
 * doing so previously created a real circular package dependency
 * ({@code utils → testng.api.base → selenium.api.base → utils}), fixed 2026-07-15 by
 * moving {@link com.framework.utils.TestMetadata} and
 * {@link com.framework.utils.AccountData} into this package instead.
 */
package com.framework.utils;
