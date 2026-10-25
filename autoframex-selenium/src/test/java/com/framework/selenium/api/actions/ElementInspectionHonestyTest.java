package com.framework.selenium.api.actions;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.utils.StepReporter;

/**
 * {@code verifyPartialAttribute} returned nothing, so a caller could not tell a match from a mismatch (both were
 * only a line in the report), unlike every other {@code verify*} method. {@code getBackgroundColor} read the CSS
 * {@code color} property, which is the text colour. No browser: hand-written stubs only.
 */
public class ElementInspectionHonestyTest {

	private static final class RecordingReporter implements StepReporter {
		final List<String> steps = new ArrayList<>();

		@Override
		public void reportStep(String desc, String status, boolean bSnap) {
			steps.add(status.toUpperCase() + ": " + desc);
		}

		@Override
		public void reportStep(String desc, String status) {
			reportStep(desc, status, true);
		}
	}

	private static final class NoWaiter implements ElementWaiter {
		@Override
		public WebDriverWait getWait(int timeoutInSeconds) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T waitFor(ExpectedCondition<T> condition, String errorMessage) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T waitFor(ExpectedCondition<T> condition, int timeoutInSeconds, String errorMessage) {
			throw new UnsupportedOperationException();
		}

		@Override
		public WebElement waitForClickable(WebElement ele) {
			throw new UnsupportedOperationException();
		}

		@Override
		public WebElement waitForVisibility(WebElement element) {
			throw new UnsupportedOperationException();
		}
	}

	/** An element with the given attributes and CSS values; every CSS property asked for is recorded. */
	private static WebElement element(Map<String, String> attributes, Map<String, String> css, List<String> cssAsked) {
		return (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(), new Class<?>[] { WebElement.class },
				(proxy, method, args) -> {
					switch (method.getName()) {
					case "getAttribute":
						return attributes.get(args[0]);
					case "getCssValue":
						cssAsked.add((String) args[0]);
						return css.get(args[0]);
					case "toString":
						return "element";
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	private static WebElement brokenElement() {
		return (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(), new Class<?>[] { WebElement.class },
				(proxy, method, args) -> {
					throw new org.openqa.selenium.StaleElementReferenceException("stale");
				});
	}

	private static ElementInspectionActions actions(RecordingReporter reporter) {
		return new ElementInspectionActions(reporter, new NoWaiter());
	}

	@Test
	public void aMatchReturnsTrue() {
		RecordingReporter reporter = new RecordingReporter();
		WebElement link = element(Map.of("class", "btn btn-primary active"), Map.of(), new ArrayList<>());
		Assert.assertTrue(actions(reporter).verifyPartialAttribute(link, "class", "primary"));
		Assert.assertTrue(reporter.steps.get(0).startsWith("PASS"), reporter.steps.toString());
	}

	@Test
	public void aMismatchReturnsFalseSoTheCallerCanDecide() {
		RecordingReporter reporter = new RecordingReporter();
		WebElement link = element(Map.of("class", "btn btn-primary"), Map.of(), new ArrayList<>());
		Assert.assertFalse(actions(reporter).verifyPartialAttribute(link, "class", "danger"));
		Assert.assertTrue(reporter.steps.get(0).startsWith("WARNING") && reporter.steps.get(0).contains("class"),
				"the report names the attribute: " + reporter.steps);
	}

	@Test
	public void aMissingAttributeIsAMismatchNotAnError() {
		Assert.assertFalse(actions(new RecordingReporter())
				.verifyPartialAttribute(element(Map.of(), Map.of(), new ArrayList<>()), "data-state", "open"));
	}

	@Test
	public void aStaleElementReturnsFalseAndReportsAFailure() {
		RecordingReporter reporter = new RecordingReporter();
		Assert.assertFalse(actions(reporter).verifyPartialAttribute(brokenElement(), "class", "x"));
		Assert.assertTrue(reporter.steps.get(0).startsWith("FAIL"), reporter.steps.toString());
	}

	@Test
	public void backgroundColourReadsTheBackgroundColourProperty() {
		List<String> asked = new ArrayList<>();
		WebElement banner = element(Map.of(), Map.of("background-color", "rgba(0, 128, 0, 1)", "color", "rgba(255, 255, 255, 1)"), asked);

		String colour = actions(new RecordingReporter()).getBackgroundColor(banner);

		Assert.assertEquals(colour, "rgba(0, 128, 0, 1)", "the background, not the white text colour");
		Assert.assertEquals(asked, List.of("background-color"));
	}
}
