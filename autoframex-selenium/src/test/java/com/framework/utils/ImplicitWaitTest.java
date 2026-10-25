package com.framework.utils;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Explicit waits ran under the driver's implicit wait, so each poll for an absent element blocked for the whole
 * implicit time (10 seconds by default) and a 5-second wait could take far longer. The helper switches the implicit
 * wait off for the duration of an action and puts it back, whatever happens. No browser: the timeouts are a recording stub.
 */
public class ImplicitWaitTest {

	/** Remembers the implicit wait and every change made to it. */
	static final class TimeoutsRecorder {
		Duration implicit;
		final List<Duration> changes = new ArrayList<>();
		boolean unreadable;
		boolean unwritable;

		TimeoutsRecorder(Duration initial) {
			this.implicit = initial;
		}

		WebDriver.Timeouts timeouts() {
			return (WebDriver.Timeouts) Proxy.newProxyInstance(WebDriver.Timeouts.class.getClassLoader(),
					new Class<?>[] { WebDriver.Timeouts.class }, (proxy, method, args) -> {
						switch (method.getName()) {
						case "getImplicitWaitTimeout":
							if (unreadable) {
								throw new UnsupportedOperationException("not supported by this driver");
							}
							return implicit;
						case "implicitlyWait":
							if (unwritable) {
								throw new org.openqa.selenium.NoSuchSessionException("session is gone");
							}
							implicit = (Duration) args[0];
							changes.add(implicit);
							return proxy;
						default:
							throw new UnsupportedOperationException(method.getName());
						}
					});
		}

		WebDriver.Options options() {
			return (WebDriver.Options) Proxy.newProxyInstance(WebDriver.Options.class.getClassLoader(),
					new Class<?>[] { WebDriver.Options.class }, (proxy, method, args) -> {
						if (method.getName().equals("timeouts")) {
							return timeouts();
						}
						throw new UnsupportedOperationException(method.getName());
					});
		}

		RemoteWebDriver driver() {
			WebDriver.Options options = options();
			return new RemoteWebDriver() {
				@Override
				public Options manage() {
					return options;
				}
			};
		}
	}

	@Test
	public void theActionRunsWithNoImplicitWaitAndTheValueIsRestored() {
		TimeoutsRecorder recorder = new TimeoutsRecorder(Duration.ofSeconds(10));
		List<Duration> seenDuring = new ArrayList<>();

		String result = ImplicitWait.without(recorder.driver(), () -> {
			seenDuring.add(recorder.implicit);
			return "done";
		});

		Assert.assertEquals(result, "done");
		Assert.assertEquals(seenDuring, List.of(Duration.ZERO), "no implicit wait while the action runs");
		Assert.assertEquals(recorder.implicit, Duration.ofSeconds(10), "restored afterwards");
		Assert.assertEquals(recorder.changes, List.of(Duration.ZERO, Duration.ofSeconds(10)));
	}

	@Test
	public void theValueIsRestoredEvenWhenTheActionThrows() {
		TimeoutsRecorder recorder = new TimeoutsRecorder(Duration.ofSeconds(7));
		Assert.expectThrows(IllegalStateException.class, () -> ImplicitWait.without(recorder.driver(), () -> {
			throw new IllegalStateException("boom");
		}));
		Assert.assertEquals(recorder.implicit, Duration.ofSeconds(7));
	}

	@Test
	public void nothingIsChangedWhenTheImplicitWaitIsAlreadyZero() {
		TimeoutsRecorder recorder = new TimeoutsRecorder(Duration.ZERO);
		Assert.assertEquals(ImplicitWait.without(recorder.driver(), () -> 42), Integer.valueOf(42));
		Assert.assertTrue(recorder.changes.isEmpty(), "no needless round trips to the browser: " + recorder.changes);
	}

	@Test
	public void aDriverThatCannotReportItsImplicitWaitJustRunsTheAction() {
		TimeoutsRecorder recorder = new TimeoutsRecorder(Duration.ofSeconds(10));
		recorder.unreadable = true;
		Assert.assertEquals(ImplicitWait.without(recorder.driver(), () -> "ran"), "ran");
		Assert.assertTrue(recorder.changes.isEmpty());
		Assert.assertEquals(ImplicitWait.current(recorder.driver()), Duration.ZERO);
	}

	@Test
	public void aSessionThatEndsDuringTheActionDoesNotHideTheActionsResult() {
		TimeoutsRecorder recorder = new TimeoutsRecorder(Duration.ofSeconds(10));
		String result = ImplicitWait.without(recorder.driver(), () -> {
			recorder.unwritable = true;      // the browser is gone by the time the value is put back
			return "finished";
		});
		Assert.assertEquals(result, "finished");
	}

	@Test
	public void theWaitUtilsPollingRunsWithoutTheImplicitWait() {
		TimeoutsRecorder recorder = new TimeoutsRecorder(Duration.ofSeconds(10));
		List<Duration> seenDuringPoll = new ArrayList<>();

		String value = WaitUtils.waitFor(recorder.driver(), null, driver -> {
			seenDuringPoll.add(recorder.implicit);
			return "ready";
		}, 2, "never reported");

		Assert.assertEquals(value, "ready");
		Assert.assertEquals(seenDuringPoll.get(0), Duration.ZERO, "the condition was polled with no implicit wait");
		Assert.assertEquals(recorder.implicit, Duration.ofSeconds(10), "and the implicit wait is back afterwards");
	}
}
