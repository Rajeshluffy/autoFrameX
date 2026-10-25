package com.framework.config.data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * "Headless" is resolved once, in the framework's usual order: suite parameter, then the HEADLESS variable, then
 * -Dheadless, then the project file. The browser providers used to ignore this and read only -Dheadless and the
 * variable, so a suite parameter had no effect; the pool now takes this resolved value. These tests pin the
 * resolution itself, which is what the pool's browsers follow.
 */
public class ProjectDirectorHeadlessTest {

	private String savedHeadless;

	@BeforeMethod
	public void clearProperty() {
		savedHeadless = System.clearProperty("headless");
	}

	@AfterMethod(alwaysRun = true)
	public void restoreProperty() {
		if (savedHeadless == null) {
			System.clearProperty("headless");
		} else {
			System.setProperty("headless", savedHeadless);
		}
	}

	private static void requireNoHeadlessVariable() {
		String variable = System.getenv("HEADLESS");
		if (variable != null && !variable.isBlank()) {
			throw new SkipException("HEADLESS is set in this process; the cases that rely on it being absent cannot run here");
		}
	}

	private static ConcurrentMap<String, String> params(String... keyValues) {
		ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	@Test
	public void theSuiteParameterTurnsHeadlessOn() {
		Assert.assertTrue(ProjectDirector.construct(params("headless", "true")).isHeadless());
	}

	@Test
	public void theSuiteParameterBeatsAJvmProperty() {
		System.setProperty("headless", "true");
		Assert.assertFalse(ProjectDirector.construct(params("headless", "false")).isHeadless(),
				"the suite said windowed; -Dheadless=true must not override it");
	}

	@Test
	public void theJvmPropertyIsUsedWhenThereIsNoSuiteParameter() {
		requireNoHeadlessVariable();
		System.setProperty("headless", "true");
		Assert.assertTrue(ProjectDirector.construct(params()).isHeadless());
	}

	@Test
	public void withNothingConfiguredTheBrowserHasAWindow() {
		requireNoHeadlessVariable();
		Assert.assertFalse(ProjectDirector.construct(params()).isHeadless());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void theOldRemoteAccessorStillReturnsTheSameValue() {
		Assert.assertTrue(ProjectDirector.construct(params("headless", "true")).isRemote(),
				"isRemote() was always the headless value under a misleading name; it is kept so callers keep compiling");
	}
}
