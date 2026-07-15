package com.framework.selenium.api.actions;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.framework.selenium.api.design.Locators;
import com.framework.utils.Reporter;

/**
 * Stateless helpers shared by several action classes (locator translation,
 * element description for logging, interrupted-safe pausing) — kept in one
 * place instead of duplicated across {@link ClickActions}, {@link WaitActions},
 * {@link LocatorActions}, etc.
 */
final class ElementSupport {

	private ElementSupport() {
	}

	/** Translates a {@link Locators} + value pair into a Selenium {@link By}, or {@code null} if unrecognized. */
	static By toBy(Locators locatorType, String value) {
		switch (locatorType) {
		case CLASS_NAME:
			return By.className(value);
		case CSS:
			return By.cssSelector(value);
		case ID:
			return By.id(value);
		case LINK_TEXT:
			return By.linkText(value);
		case NAME:
			return By.name(value);
		case PARTIAL_LINKTEXT:
			return By.partialLinkText(value);
		case TAGNAME:
			return By.tagName(value);
		case XPATH:
			return By.xpath(value);
		default:
			return null;
		}
	}

	/** Best-effort human-readable description of an element for log messages. */
	static String describe(WebElement ele) {
		try {
			String tag = ele.getTagName();
			String id = ele.getAttribute("id");
			String name = ele.getAttribute("name");

			if (id != null && !id.isEmpty()) {
				return tag + "[id='" + id + "']";
			} else if (name != null && !name.isEmpty()) {
				return tag + "[name='" + name + "']";
			} else {
				return tag;
			}
		} catch (Exception e) {
			return "unknown element";
		}
	}

	/** Gets element text without throwing; falls back to the {@code value} attribute. */
	static String safeGetText(WebElement ele) {
		try {
			String text = ele.getText();
			return text != null && !text.isEmpty() ? text : ele.getAttribute("value");
		} catch (Exception e) {
			return "";
		}
	}

	/** Sleeps, reporting (not throwing) if interrupted. */
	static void pause(Reporter reporter, int timeoutMs) {
		try {
			Thread.sleep(timeoutMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (reporter != null) {
				reporter.reportStep("Thread interrupted during pause", "warning", false);
			}
		}
	}
}
