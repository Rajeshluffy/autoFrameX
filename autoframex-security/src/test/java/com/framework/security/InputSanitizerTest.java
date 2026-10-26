package com.framework.security;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * {@code InputSanitizer} had no test. Run against ordinary text and against real payloads it was wrong both ways:
 * any sentence with the word "select", "update" or "create" in it was called SQL injection (so
 * {@code assertSafeInput} failed on a normal page), while payloads such as {@code <details open ontoggle=...>} or
 * {@code ' OR 'a'='a} were called safe (so a reflected payload passed). A check that is wrong in both directions
 * proves nothing either way.
 */
public class InputSanitizerTest {

    @DataProvider(name = "ordinaryText")
    public Object[][] ordinaryText() {
        return new Object[][] {
                { "Please select an option from the list" },
                { "Update your profile to continue" },
                { "Create a new account" },
                { "Delete this item?" },
                { "Convert the file to PDF" },
                { "Execute the order at market price" },
                { "Cast of characters" },
                { "Union Bank of India" },
                { "Declare your income by March" },
                { "Script writing course" },
                { "Tom & Jerry's house" },
                { "O'Brien and Sons" },
                { "I <3 testing, and 2 < 3 > 1" },
                { "onion = tasty, onboarding = fun" },
                { "drop me a line, or alter your plans" },
                { "" },
        };
    }

    @Test(dataProvider = "ordinaryText")
    public void ordinaryTextIsNotCalledAnAttack(String text) {
        Assert.assertFalse(InputSanitizer.containsSqlInjection(text), "SQL injection wrongly reported for: " + text);
        Assert.assertFalse(InputSanitizer.containsXss(text), "XSS wrongly reported for: " + text);
        InputSanitizer.assertSafeInput(text, "ordinary");
    }

    @DataProvider(name = "sqlPayloads")
    public Object[][] sqlPayloads() {
        return new Object[][] {
                { "' OR '1'='1" },
                { "1 OR 1=1" },
                { "' OR 'a'='a" },
                { "admin'--" },
                { "admin' #" },
                { "'; DROP TABLE users;--" },
                { "1; DELETE FROM orders" },
                { "1 UNION SELECT username, password FROM users" },
                { "1 union all select null,null" },
                { "x' AND 1=1 --" },
                { "1; EXEC xp_cmdshell('dir')" },
                { "1' AND SLEEP(5)--" },
                { "1; WAITFOR DELAY '0:0:5'" },
                { "INSERT INTO users VALUES ('a','b')" },
                { "UPDATE users SET admin=1" },
                { "SELECT name FROM sys.tables" },
                { "1 /* comment */ OR 1=1" },
        };
    }

    @Test(dataProvider = "sqlPayloads")
    public void sqlInjectionPayloadsAreDetected(String payload) {
        Assert.assertTrue(InputSanitizer.containsSqlInjection(payload), "missed SQL injection: " + payload);
        Assert.expectThrows(AssertionError.class, () -> InputSanitizer.assertSafeInput(payload, "field"));
    }

    @DataProvider(name = "xssPayloads")
    public Object[][] xssPayloads() {
        return new Object[][] {
                { "<script>alert(1)</script>" },
                { "<SCRIPT SRC=//evil/x.js></SCRIPT>" },
                { "<img src=x onerror=alert(1)>" },
                { "<svg/onload=alert(1)>" },
                { "<details open ontoggle=alert(1)>" },
                { "<body onpageshow=steal()>" },
                { "<input autofocus onfocus=steal()>" },
                { "\" onmouseenter=\"steal()" },
                { "<a href=\"javascript:steal()\">x</a>" },
                { "<a href=\"vbscript:msgbox(1)\">x</a>" },
                { "<iframe src=\"//evil\"></iframe>" },
                { "<marquee onstart=steal()>" },
                { "<video><source onerror=steal()>" },
                { "data:text/html;base64,PHNjcmlwdD4=" },
                { "<style>@import 'http://evil/x.css';</style>" },
                { "document.cookie" },
                { "eval(atob('...'))" },
        };
    }

    @Test(dataProvider = "xssPayloads")
    public void xssPayloadsAreDetected(String payload) {
        Assert.assertTrue(InputSanitizer.containsXss(payload), "missed XSS: " + payload);
        Assert.expectThrows(AssertionError.class, () -> InputSanitizer.assertSafeInput(payload, "field"));
    }

    @Test
    public void nullIsNotAnAttack() {
        Assert.assertFalse(InputSanitizer.containsSqlInjection(null));
        Assert.assertFalse(InputSanitizer.containsXss(null));
        InputSanitizer.assertSafeInput(null, "field");
    }

    @Test
    public void longHostileInputIsCheckedInLinearTime() {
        // An unclosed comment opener repeated, an unfinished tag, a long column list: each must not make the
        // check take seconds (the masking regex in EncryptionUtils once took 16 s on 20,000 characters).
        String[] inputs = {
                "/*".repeat(30_000),
                "<".repeat(30_000),
                "select " + "a,".repeat(15_000),
                "or 1".repeat(10_000),
                "'".repeat(30_000),
        };
        for (String input : inputs) {
            long started = System.nanoTime();
            InputSanitizer.containsSqlInjection(input);
            InputSanitizer.containsXss(input);
            long ms = (System.nanoTime() - started) / 1_000_000;
            Assert.assertTrue(ms < 1_000, "took " + ms + " ms on " + input.length() + " characters");
        }
    }

    @Test
    public void theFailureMessageNamesTheFieldAndNeverTheValue() {
        AssertionError error = Assert.expectThrows(AssertionError.class,
                () -> InputSanitizer.assertSafeInput("<script>alert('secret-marker')</script>", "comment"));
        Assert.assertTrue(error.getMessage().contains("comment"), error.getMessage());
        Assert.assertFalse(error.getMessage().contains("secret-marker"), "the attack string must stay out of test output");
    }

    @Test
    public void sanitizeEscapesHtmlAndSqlSpecialCharacters() {
        Assert.assertEquals(InputSanitizer.sanitize("<b>\"a\" & 'b';</b>"),
                "&lt;b&gt;&quot;a&quot; &amp; &#x27;b&#x27;&#x3B;&lt;/b&gt;");
        Assert.assertEquals(InputSanitizer.sanitize(null), "");
    }

    @Test
    public void sanitizeNeutralisesLineBreaksSoInputCannotForgeLogLines() {
        // The documented use is "safely included in log messages". HTML escaping does nothing about the attack on a
        // log: a value with a line break in it starts a second, forged line.
        String forged = InputSanitizer.sanitize("user\n2026-10-25 INFO Login succeeded for admin\r\nand more");

        Assert.assertFalse(forged.contains("\n") || forged.contains("\r"), "no raw line break may survive: " + forged);
        Assert.assertTrue(forged.contains("\\n") && forged.contains("\\r"), "the break stays visible, escaped: " + forged);
        Assert.assertEquals(List.of(forged.split("\n")).size(), 1);
    }
}
