package com.framework.observability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The collector is process-wide. After the first suite's flush it kept {@code initialized == true} but had no
 * writer, so a second suite in the same JVM (several suite files in one run) lost every event.
 */
public class TestEventCollectorRestartTest {

	private static TestEvent event(String id) {
		return TestEvent.builder().testId(id).status("PASS").build();
	}

	private static String contentOf(Path dir) throws IOException {
		Path file = dir.resolve("test-events.json");
		return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
	}

	@Test
	public void aSecondInitAfterAFlushStartsANewWriter() throws IOException {
		Path first = Files.createTempDirectory("collector-first");
		Path second = Files.createTempDirectory("collector-second");

		TestEventCollector.flush();                   // start from a known state, whatever ran before in this JVM

		TestEventCollector.init(first.toString());
		TestEventCollector.publish(event("first-suite-event"));
		TestEventCollector.flush();

		TestEventCollector.init(second.toString());
		TestEventCollector.publish(event("second-suite-event"));
		TestEventCollector.flush();

		Assert.assertTrue(contentOf(first).contains("first-suite-event"), "the first suite's event is written");
		Assert.assertTrue(contentOf(second).contains("second-suite-event"),
				"the second suite's event is written too (it was silently dropped)");
		Assert.assertFalse(contentOf(second).contains("first-suite-event"));
	}

	@Test
	public void eventsPublishedAfterAFlushAreDroppedQuietlyNotQueuedForever() throws IOException {
		TestEventCollector.flush();
		TestEventCollector.publish(event("nobody-is-listening"));   // must not throw or block
		TestEventCollector.flush();                                 // and flushing again is a no-op
	}
}
