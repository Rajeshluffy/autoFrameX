package com.framework.selenium.api.actions;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsDriver;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.SessionId;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.utils.StepReporter;

/**
 * When {@code sendKeys} on a file input is refused, the upload falls back to driving the OS file dialog with a
 * Robot. On a Selenium Grid session the dialog is on the node, so the keystrokes would go to whatever window has
 * focus on the test runner. A remote session now fails the step with the reason instead.
 */
public class FileUploadRemoteSessionTest {

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

	private static WebElement refusingElement(WebDriver owner, AtomicInteger sendKeysCalls) {
		return (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(),
				new Class<?>[] { WebElement.class, WrapsDriver.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "sendKeys":
						sendKeysCalls.incrementAndGet();
						throw new IllegalArgumentException("not a file input");
					case "getWrappedDriver":
						return owner;
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	private static ElementClicker clicker(AtomicInteger clicks) {
		return new ElementClicker() {
			@Override
			public void clickWithJs(WebElement ele) {
				clicks.incrementAndGet();
			}

			@Override
			public void hoverAndClick(WebElement ele) {
				clicks.incrementAndGet();
			}
		};
	}

	@Test
	public void aRefusedUploadOnARemoteSessionFailsInsteadOfDrivingTheLocalKeyboard() {
		RecordingReporter reporter = new RecordingReporter();
		AtomicInteger clicks = new AtomicInteger();
		AtomicInteger sendKeys = new AtomicInteger();
		FileUploadActions upload = new FileUploadActions(reporter, clicker(clicks), e -> true);
		WebElement input = refusingElement(null, sendKeys);

		upload.fileUpload(input, "C:\\data\\report.pdf");
		upload.fileUploadWithJs(input, "C:\\data\\report.pdf");

		Assert.assertEquals(sendKeys.get(), 2, "sendKeys is still tried first");
		Assert.assertEquals(clicks.get(), 0, "no dialog was opened on the node");
		long failures = reporter.steps.stream().filter(s -> s.startsWith("FAIL") && s.contains("remote (Grid) browser")
				&& s.contains("report.pdf")).count();
		Assert.assertEquals(failures, 2, reporter.steps.toString());
	}

	/** A plain {@link RemoteWebDriver} (the class a Grid session uses) whose "hub" is a stub that grants any session. */
	private static RemoteWebDriver sessionWithoutABrowser() {
		CommandExecutor stubHub = command -> {
			Response response = new Response(new SessionId("stub-session"));
			response.setState("success");
			response.setValue(Map.of("browserName", "chrome"));
			return response;
		};
		return new RemoteWebDriver(stubHub, new MutableCapabilities());
	}

	@Test
	public void aPlainRemoteWebDriverIsRecognisedAsRemote() {
		WebDriver localStyle = new RemoteWebDriver() { };   // a subclass, like ChromeDriver or FirefoxDriver
		Assert.assertFalse(FileUploadActions.isRemoteSession(refusingElement(localStyle, new AtomicInteger())),
				"a subclass of RemoteWebDriver (ChromeDriver, FirefoxDriver, ...) is a local browser");
		Assert.assertTrue(FileUploadActions.isRemoteSession(refusingElement(sessionWithoutABrowser(), new AtomicInteger())),
				"a plain RemoteWebDriver is what a Grid session is");
	}

	@Test
	public void anElementThatDoesNotExposeItsDriverIsTreatedAsLocal() {
		WebElement bare = (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(),
				new Class<?>[] { WebElement.class }, (proxy, method, args) -> {
					throw new UnsupportedOperationException(method.getName());
				});
		Assert.assertFalse(FileUploadActions.isRemoteSession(bare));
	}
}
