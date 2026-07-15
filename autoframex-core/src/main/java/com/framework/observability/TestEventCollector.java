package com.framework.observability;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Singleton that asynchronously writes {@link TestEvent} objects as NDJSON
 * (one JSON object per line) to {@code logs/test-events.json}.
 *
 * <p>The background writer thread is a daemon so it never prevents JVM exit.
 * The {@link #flush()} method drains the queue cleanly before suite shutdown.
 *
 * <p>All I/O errors are logged as warnings — they never propagate to test threads.
 */
public final class TestEventCollector {

    private static final Logger logger = LoggerFactory.getLogger(TestEventCollector.class);
    private static final int QUEUE_CAPACITY = 10_000;

    private static final TestEventCollector INSTANCE = new TestEventCollector();

    private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final ObjectMapper mapper;

    // Sentinel object used to signal the writer thread to stop
    private static final Object POISON_PILL = new Object();

    private volatile BufferedWriter writer;
    private volatile Thread writerThread;
    private volatile boolean initialized = false;

    private TestEventCollector() {
        mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public static TestEventCollector getInstance() { return INSTANCE; }

    /**
     * Creates the output directory, opens the NDJSON file, and starts the
     * background writer thread. Safe to call multiple times — only initializes once.
     *
     * @param outputDir directory for the output file (created if absent)
     */
    public synchronized void init(String outputDir) {
        if (initialized) return;
        try {
            File dir = new File(outputDir);
            if (!dir.exists()) dir.mkdirs();

            File outFile = new File(dir, "test-events.json");
            writer = new BufferedWriter(new FileWriter(outFile, true));

            writerThread = new Thread(this::drainLoop, "TestEventCollector-Writer");
            writerThread.setDaemon(true);
            writerThread.start();

            initialized = true;
            logger.info("TestEventCollector initialized — writing to: {}", outFile.getAbsolutePath());
        } catch (IOException e) {
            logger.warn("TestEventCollector init failed — events will not be persisted: {}", e.getMessage());
        }
    }

    /**
     * Enqueues a {@link TestEvent} for async writing.
     * Non-blocking: if the queue is full the event is dropped with a warning.
     *
     * @param event the event to publish
     */
    public void publish(TestEvent event) {
        if (!initialized || event == null) return;
        if (!queue.offer(event)) {
            logger.warn("TestEventCollector queue full — dropping event for: {}", event.getTestId());
        }
    }

    /**
     * Drains remaining events and shuts down the writer thread.
     * Blocks until the writer thread finishes or 10 seconds elapse.
     * Call from @AfterSuite.
     */
    public void flush() {
        if (!initialized) return;
        try {
            queue.put(POISON_PILL);
            if (writerThread != null) {
                writerThread.join(10_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("TestEventCollector flush interrupted");
        }
    }

    // -------------------------------------------------------------------------
    // Background writer
    // -------------------------------------------------------------------------

    private void drainLoop() {
        try {
            while (true) {
                Object item = queue.poll(5, TimeUnit.SECONDS);
                if (item == null) continue;
                if (item == POISON_PILL) break;
                if (item instanceof TestEvent) {
                    writeEvent((TestEvent) item);
                }
            }
            // Drain any remaining events after poison pill
            Object remaining;
            while ((remaining = queue.poll()) != null) {
                if (remaining instanceof TestEvent) {
                    writeEvent((TestEvent) remaining);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeWriter();
        }
    }

    private void writeEvent(TestEvent event) {
        if (writer == null) return;
        try {
            writer.write(mapper.writeValueAsString(event));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            logger.warn("Failed to write test event for {}: {}", event.getTestId(), e.getMessage());
        }
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                logger.warn("Failed to close TestEventCollector writer: {}", e.getMessage());
            }
        }
    }
}
