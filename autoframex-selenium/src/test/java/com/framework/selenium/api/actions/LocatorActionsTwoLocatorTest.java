package com.framework.selenium.api.actions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.selenium.api.design.Locators;
import com.framework.selenium.exception.ElementNotFoundException;
import com.framework.utils.FakeImplicitWaitDriver;
import com.framework.utils.StepReporter;

/**
 * The two-locator lookup probed the primary locator, then the fallback, each under the driver's implicit wait: a
 * fallback was only tried after the primary had waited the whole implicit period (10 s by default) for an element
 * that was not coming, and a miss on both took twice that. It now polls both together, with the implicit wait off,
 * for one implicit period in total.
 */
public class LocatorActionsTwoLocatorTest {

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

	private static final By PRIMARY = By.id("primary");
	private static final By FALLBACK = By.cssSelector(".fallback");

	private static LocatorActions locator(FakeImplicitWaitDriver driver, RecordingReporter reporter) {
		return new LocatorActions(() -> driver, reporter);
	}

	@Test
	public void thePrimaryLocatorWinsWhenItIsThere() {
		WebElement primary = FakeImplicitWaitDriver.element("primary");
		FakeImplicitWaitDriver driver = new FakeImplicitWaitDriver(Duration.ofSeconds(10),
				(by, call) -> by.equals(PRIMARY) ? List.of(primary) : List.of(FakeImplicitWaitDriver.element("fallback")));
		RecordingReporter reporter = new RecordingReporter();

		WebElement found = locator(driver, reporter).locateElement(Locators.ID, "primary", Locators.CSS, ".fallback");

		Assert.assertSame(found, primary);
		Assert.assertTrue(reporter.steps.get(0).contains("primary locator"), reporter.steps.toString());
	}

	@Test
	public void theFallbackIsUsedAtOnceWhenThePrimaryIsAbsent() {
		WebElement fallback = FakeImplicitWaitDriver.element("fallback");
		FakeImplicitWaitDriver driver = new FakeImplicitWaitDriver(Duration.ofSeconds(10),
				(by, call) -> by.equals(FALLBACK) ? List.of(fallback) : List.of());
		RecordingReporter reporter = new RecordingReporter();

		long started = System.nanoTime();
		WebElement found = locator(driver, reporter).locateElement(Locators.ID, "primary", Locators.CSS, ".fallback");
		long tookMs = (System.nanoTime() - started) / 1_000_000;

		Assert.assertSame(found, fallback);
		Assert.assertTrue(tookMs < 1_000, "the fallback must not wait for the primary: took " + tookMs + " ms");
		Assert.assertTrue(reporter.steps.get(0).contains("fallback locator"), reporter.steps.toString());
	}

	@Test
	public void pollingHappensWithNoImplicitWaitAndItIsRestored() {
		FakeImplicitWaitDriver driver = new FakeImplicitWaitDriver(Duration.ofSeconds(10),
				(by, call) -> by.equals(FALLBACK) ? List.of(FakeImplicitWaitDriver.element("f")) : List.of());
		locator(driver, new RecordingReporter()).locateElement(Locators.ID, "primary", Locators.CSS, ".fallback");

		Assert.assertFalse(driver.implicitDuringFind.isEmpty());
		Assert.assertTrue(driver.implicitDuringFind.stream().allMatch(Duration::isZero),
				"every probe ran with the implicit wait off: " + driver.implicitDuringFind);
		Assert.assertEquals(driver.implicit, Duration.ofSeconds(10), "restored afterwards");
	}

	@Test
	public void anElementThatAppearsLaterIsFoundWithinTheImplicitPeriod() {
		WebElement late = FakeImplicitWaitDriver.element("late primary");
		// Each poll makes two findElements calls (primary, then fallback); the primary shows up on the 3rd poll.
		FakeImplicitWaitDriver driver = new FakeImplicitWaitDriver(Duration.ofSeconds(5),
				(by, call) -> by.equals(PRIMARY) && call >= 5 ? List.of(late) : List.of());

		WebElement found = locator(driver, new RecordingReporter()).locateElement(Locators.ID, "primary", Locators.CSS, ".fallback");

		Assert.assertSame(found, late);
		Assert.assertTrue(driver.findCalls >= 5, "it polled until the element appeared: " + driver.findCalls);
	}

	@Test
	public void missingBothWaitsOnePeriodNotTwo() {
		FakeImplicitWaitDriver driver = new FakeImplicitWaitDriver(Duration.ofMillis(800), (by, call) -> List.of());
		RecordingReporter reporter = new RecordingReporter();

		long started = System.nanoTime();
		ElementNotFoundException e = Assert.expectThrows(ElementNotFoundException.class,
				() -> locator(driver, reporter).locateElement(Locators.ID, "primary", Locators.CSS, ".fallback"));
		long tookMs = (System.nanoTime() - started) / 1_000_000;

		Assert.assertTrue(e.getMessage().contains("primary") && e.getMessage().contains(".fallback"), e.getMessage());
		Assert.assertTrue(tookMs >= 700, "it did wait for the implicit period: " + tookMs + " ms");
		Assert.assertTrue(tookMs < 1_600, "but only once, not once per locator: " + tookMs + " ms");
	}

	@Test
	public void withNoImplicitWaitThereIsASingleLook() {
		FakeImplicitWaitDriver driver = new FakeImplicitWaitDriver(Duration.ZERO, (by, call) -> List.of());
		Assert.assertThrows(ElementNotFoundException.class,
				() -> locator(driver, new RecordingReporter()).locateElement(Locators.ID, "primary", Locators.CSS, ".fallback"));
		Assert.assertEquals(driver.findCalls, 2, "one look at each locator");
	}
}
