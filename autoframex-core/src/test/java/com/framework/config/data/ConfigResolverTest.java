package com.framework.config.data;

import java.util.HashMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Coverage for {@link ConfigResolver}'s four-tier priority chain
 * (TestNG/Cucumber parameter → CI/CD environment variable → JVM system
 * property → caller-supplied default), including the fall-through-on-malformed-
 * value behavior its own Javadoc documents but which had no test of its own.
 *
 * <p>System properties are the only tier this test can control directly
 * (environment variables aren't settable from within the JVM) — every test
 * that touches one saves and restores it, since properties are JVM-global and
 * this test runs in the same forked JVM as the rest of the CI suite.
 */
public class ConfigResolverTest {

	private static final String UNUSED_ENV_KEY = "CONFIG_RESOLVER_TEST_ENV_VAR_THAT_DOES_NOT_EXIST";

	// =========================================================================
	// resolveString
	// =========================================================================

	@Test
	public void resolveStringPrefersParamOverEverythingElse() {
		Map<String, String> params = paramsOf("key", "fromParam");
		Assert.assertEquals(
				ConfigResolver.resolveString("key", UNUSED_ENV_KEY, params, "fromDefault"),
				"fromParam");
	}

	@Test
	public void resolveStringFallsBackToSystemPropertyWhenParamAbsent() {
		withSystemProperty("configResolverTest.str", "fromSysProp", () -> {
			Assert.assertEquals(
					ConfigResolver.resolveString("configResolverTest.str", UNUSED_ENV_KEY, null, "fromDefault"),
					"fromSysProp");
		});
	}

	@Test
	public void resolveStringFallsBackToDefaultWhenNothingSet() {
		Assert.assertEquals(
				ConfigResolver.resolveString(UNUSED_ENV_KEY, UNUSED_ENV_KEY, null, "fromDefault"),
				"fromDefault");
	}

	@Test
	public void resolveStringTreatsBlankParamAsAbsent() {
		Map<String, String> params = paramsOf("key", "   ");
		Assert.assertEquals(
				ConfigResolver.resolveString("key", UNUSED_ENV_KEY, params, "fromDefault"),
				"fromDefault",
				"A blank (whitespace-only) param value should fall through to the next tier, not win as an empty string.");
	}

	@Test
	public void resolveStringTrimsWhitespace() {
		Map<String, String> params = paramsOf("key", "  padded  ");
		Assert.assertEquals(
				ConfigResolver.resolveString("key", UNUSED_ENV_KEY, params, "fromDefault"),
				"padded");
	}

	// =========================================================================
	// resolveBoolean
	// =========================================================================

	@Test
	public void resolveBooleanPrefersParamOverDefault() {
		Map<String, String> params = paramsOf("flag", "true");
		Assert.assertTrue(ConfigResolver.resolveBoolean("flag", UNUSED_ENV_KEY, params, false));
	}

	@Test
	public void resolveBooleanFallsBackToDefaultWhenNothingSet() {
		Assert.assertTrue(ConfigResolver.resolveBoolean(UNUSED_ENV_KEY, UNUSED_ENV_KEY, null, true));
		Assert.assertFalse(ConfigResolver.resolveBoolean(UNUSED_ENV_KEY, UNUSED_ENV_KEY, null, false));
	}

	@Test
	public void resolveBooleanUnparsableParamFallsThroughLikeAnInt() {
		// Boolean.parseBoolean() turned every typo into false, silently, and let it override a valid lower
		// tier. A value that is not true/false is now logged and skipped, exactly as resolveInt() does.
		Map<String, String> params = paramsOf("flag", "not-a-boolean");
		Assert.assertTrue(ConfigResolver.resolveBoolean("flag", UNUSED_ENV_KEY, params, true),
				"the typo must not win as false; the default decides");
		withSystemProperty("flag", "false", () ->
				Assert.assertFalse(ConfigResolver.resolveBoolean("flag", UNUSED_ENV_KEY, params, true),
						"the next tier (the system property) decides, not the default"));
	}

	@Test
	public void resolveBooleanTreatsCommonTyposAsInvalidNotFalse() {
		for (String typo : new String[] { "ture", "yes", "1", "on", "tru" }) {
			Assert.assertTrue(ConfigResolver.resolveBoolean("flag", UNUSED_ENV_KEY, paramsOf("flag", typo), true),
					"'" + typo + "' is not a boolean, so it must not turn the default (true) into false");
		}
	}

	@Test
	public void resolveBooleanAcceptsAnyCaseAndSurroundingWhitespace() {
		Assert.assertTrue(ConfigResolver.resolveBoolean("flag", UNUSED_ENV_KEY, paramsOf("flag", "  TRUE "), false));
		Assert.assertFalse(ConfigResolver.resolveBoolean("flag", UNUSED_ENV_KEY, paramsOf("flag", "False"), true));
	}

	@Test
	public void resolveBooleanTreatsABlankValueAsNotSet() {
		// An empty Jenkins parameter reaches the process as HEADLESS="" (an empty variable). It used to count
		// as false and override every lower tier.
		Assert.assertTrue(ConfigResolver.resolveBoolean("flag", UNUSED_ENV_KEY, paramsOf("flag", "   "), true));
		Assert.assertTrue(ConfigResolver.resolveBoolean("flag", UNUSED_ENV_KEY, paramsOf("flag", ""), true));
	}

	// =========================================================================
	// unresolved ${placeholders} count as not set
	// =========================================================================

	@Test
	public void anUnresolvedPlaceholderIsNotAValue() {
		// testng.xml passes browser="${browser}"; with no -Dbrowser the literal text arrives.
		Map<String, String> params = paramsOf("browser", "${browser}");
		Assert.assertEquals(ConfigResolver.resolveString("browser", UNUSED_ENV_KEY, params, "chrome"), "chrome");
		Assert.assertTrue(ConfigResolver.resolveBoolean("headless", UNUSED_ENV_KEY, paramsOf("headless", "${headless}"), true));
		Assert.assertEquals(ConfigResolver.resolveInt("size", UNUSED_ENV_KEY, paramsOf("size", "${size}"), 5), 5);
	}

	@Test
	public void aPlaceholderInTheParameterLetsTheSystemPropertyDecide() {
		withSystemProperty("configResolverTest.placeholder", "fromSysProp", () ->
				Assert.assertEquals(ConfigResolver.resolveString("configResolverTest.placeholder", UNUSED_ENV_KEY,
						paramsOf("configResolverTest.placeholder", "${x}"), "fromDefault"), "fromSysProp"));
	}

	// =========================================================================
	// resolveInt
	// =========================================================================

	@Test
	public void resolveIntPrefersParamOverDefault() {
		Map<String, String> params = paramsOf("size", "7");
		Assert.assertEquals(ConfigResolver.resolveInt("size", UNUSED_ENV_KEY, params, 99), 7);
	}

	@Test
	public void resolveIntFallsBackToDefaultWhenNothingSet() {
		Assert.assertEquals(ConfigResolver.resolveInt(UNUSED_ENV_KEY, UNUSED_ENV_KEY, null, 42), 42);
	}

	@Test
	public void resolveIntFallsThroughOnMalformedParamInsteadOfGivingUp() {
		// The class's own Javadoc: a malformed higher-priority override shouldn't
		// shadow a perfectly valid lower-priority one. A malformed param must fall
		// through to system property, not straight to the default.
		withSystemProperty("configResolverTest.int", "13", () -> {
			Map<String, String> params = paramsOf("configResolverTest.int", "not-a-number");
			Assert.assertEquals(
					ConfigResolver.resolveInt("configResolverTest.int", UNUSED_ENV_KEY, params, 99),
					13,
					"A malformed param value should fall through to the system-property tier, not skip straight to the default.");
		});
	}

	@Test
	public void resolveIntFallsThroughToDefaultWhenEveryTierIsMalformedOrAbsent() {
		withSystemProperty("configResolverTest.intBad", "also-not-a-number", () -> {
			Map<String, String> params = paramsOf("configResolverTest.intBad", "still-not-a-number");
			Assert.assertEquals(
					ConfigResolver.resolveInt("configResolverTest.intBad", UNUSED_ENV_KEY, params, 5),
					5);
		});
	}

	@Test
	public void resolveIntTrimsWhitespaceBeforeParsing() {
		Map<String, String> params = paramsOf("size", "  9  ");
		Assert.assertEquals(ConfigResolver.resolveInt("size", UNUSED_ENV_KEY, params, 0), 9);
	}

	// =========================================================================
	// resolveShort
	// =========================================================================

	@Test
	public void resolveShortDelegatesToResolveInt() {
		Map<String, String> params = paramsOf("port", "8080");
		Assert.assertEquals(ConfigResolver.resolveShort("port", UNUSED_ENV_KEY, params, (short) 0), (short) 8080);
	}

	@Test
	public void resolveShortFallsBackToDefaultWhenOutOfShortRange() {
		// Short.MAX_VALUE is 32767 — a value beyond that must not silently
		// truncate/wrap, it must fall back to the caller's default.
		Map<String, String> params = paramsOf("port", "70000");
		Assert.assertEquals(
				ConfigResolver.resolveShort("port", UNUSED_ENV_KEY, params, (short) 4444),
				(short) 4444,
				"A value outside short range must fall back to the default, not wrap/truncate silently.");
	}

	// =========================================================================
	// HELPERS
	// =========================================================================

	private static Map<String, String> paramsOf(String key, String value) {
		Map<String, String> params = new HashMap<>();
		params.put(key, value);
		return params;
	}

	private static void withSystemProperty(String key, String value, Runnable body) {
		String previous = System.getProperty(key);
		try {
			System.setProperty(key, value);
			body.run();
		} finally {
			if (previous == null) {
				System.clearProperty(key);
			} else {
				System.setProperty(key, previous);
			}
		}
	}
}
