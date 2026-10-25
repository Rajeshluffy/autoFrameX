package com.framework.security;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * {@code SecretDetector.scan(Map)} looked only at values, so {@code dbPassword=hunter2hunter2} passed: the value
 * has no "password" in it and is shorter than a base64 credential. It now also reads the key. Placeholders and
 * non-secret settings that merely contain a credential word must stay quiet, or the scan gets switched off.
 */
public class SecretDetectorTest {

	private static Map<String, String> map(String... keyValues) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	// ------------------------------------------------------------- credential-named keys

	@Test
	public void aCredentialNamedKeyWithALiteralValueIsReported() {
		Map<String, String> config = map(
				"dbPassword", "hunter2hunter2",
				"api.token", "abc123abc123abc1",
				"service.now.client.secret", "s3cr3t",
				"DB_PASSWORD", "x",
				"encryption.key", "k3y-material",
				"apiKey", "ZZZ-123",
				"app.url", "https://example.org");
		assertEquals(SecretDetector.scan(config),
				List.of("dbPassword", "api.token", "service.now.client.secret", "DB_PASSWORD", "encryption.key", "apiKey"));
	}

	@Test
	public void aPlaceholderBlankOrMaskIsNotAHardcodedSecret() {
		Map<String, String> config = map(
				"db.password", "${DB_PASSWORD}",
				"api.token", "<your-token>",
				"client.secret", "***",
				"db.passwd", "",
				"encrypted.password", "ENC(Zm9vYmFy)",
				"env.password", "$DB_PASSWORD",
				"win.password", "%DB_PASSWORD%",
				"templated.secret", "{{ vault.secret }}");
		assertTrue(SecretDetector.scan(config).isEmpty(), "reported: " + SecretDetector.scan(config));
	}

	@Test
	public void aSettingThatOnlyMentionsACredentialWordIsNotACredential() {
		Map<String, String> config = map(
				"autoFrameX.api.capture.mask.secrets", "false",
				"oauth.token.lifespan", "1800",
				"password.policy.minLength", "12",
				"session.token.expiry", "30m",
				"primary.key", "id",
				"keyboard.layout", "us",
				"show.password", "true");
		assertTrue(SecretDetector.scan(config).isEmpty(), "reported: " + SecretDetector.scan(config));
	}

	@Test
	public void wordsAreSplitOnCamelCaseAndSeparators() {
		assertTrue(SecretDetector.isCredentialKeyWithLiteral("dbClientSecret", "abc"));
		assertTrue(SecretDetector.isCredentialKeyWithLiteral("db-client-secret", "abc"));
		assertFalse(SecretDetector.isCredentialKeyWithLiteral("secretsEnabled", "yes"));
		assertFalse(SecretDetector.isCredentialKeyWithLiteral(null, "abc"));
		assertFalse(SecretDetector.isCredentialKeyWithLiteral("db.password", null));
	}

	// ------------------------------------------------------------------- value patterns

	@Test
	public void aSecretInsideTextIsStillFound() {
		assertTrue(SecretDetector.scan("login password=hunter2hunter2 failed"));
		assertTrue(SecretDetector.scan("{\"password\":\"hunter2hunter2\"}"), "JSON-quoted form");
		assertTrue(SecretDetector.scan("AKIAABCDEFGHIJKLMNOP"));
		assertTrue(SecretDetector.scan("-----BEGIN RSA PRIVATE KEY-----"));
		assertTrue(SecretDetector.scan("jdbc:mysql://user:hunter2@db.example/app"), "user:pass@host");
		assertTrue(SecretDetector.scan("jdbc:mysql://db.example/app?user=app&password=hunter2"), "password as a URL parameter");
	}

	@Test
	public void placeholdersAndPlainTextAreNotFindings() {
		assertFalse(SecretDetector.scan("password=${DB_PASSWORD}"));
		assertFalse(SecretDetector.scan("{\"password\":\"<redacted>\"}"));
		assertFalse(SecretDetector.scan("token: ***"));
		assertFalse(SecretDetector.scan("the user typed a password into the form"));
		assertFalse(SecretDetector.scan("jdbc:mysql://db.example/app?user=app&password=${DB_PASSWORD}"));
		assertFalse(SecretDetector.scan((String) null));
		assertFalse(SecretDetector.scan("   "));
	}

	// ------------------------------------------------------------------- the assertion

	@Test
	public void assertNoSecretsFailsNamingTheKeysButNeverTheValues() {
		try {
			SecretDetector.assertNoSecrets(map("db.password", "VERY-SECRET-VALUE-1", "app.name", "demo"), "application.properties");
			AssertJUnit.fail("a hardcoded password must fail the check");
		} catch (AssertionError e) {
			assertTrue(e.getMessage().contains("db.password"), e.getMessage());
			assertTrue(e.getMessage().contains("application.properties"), e.getMessage());
			assertFalse(e.getMessage().contains("VERY-SECRET-VALUE-1"), "the value must not appear: " + e.getMessage());
		}
	}

	@Test
	public void assertNoSecretsPassesForPlaceholdersAndOrdinarySettings() {
		SecretDetector.assertNoSecrets(map("db.password", "${DB_PASSWORD}", "app.name", "demo", "retries", "3"), "ok.properties");
	}
}
