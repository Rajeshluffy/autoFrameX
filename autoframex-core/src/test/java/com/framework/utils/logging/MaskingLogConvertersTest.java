package com.framework.utils.logging;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;

/**
 * The console pattern in logback.xml masks the message ({@code %mask}) and, since an exception's own message is a
 * separate part of the line, the stack trace ({@code %maskEx}). This builds a layout from the same pattern, so a
 * logback upgrade or a pattern edit that stops masking either part fails here, not in a leaked log.
 */
public class MaskingLogConvertersTest {

    /** Must stay equal to the CONSOLE pattern in autoframex-core's logback.xml (minus the timestamp and thread). */
    private static final String CONSOLE_PATTERN = "%-5level %logger{36} - %mask%n%maskEx";

    private static LoggingEvent event(String message, Throwable thrown) {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger("probe");
        return new LoggingEvent(Logger.class.getName(), logger, Level.WARN, message, thrown, null);
    }

    private static PatternLayout consoleLayout() {
        LoggerContext context = new LoggerContext();
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.getInstanceConverterMap().put("mask", MaskingMessageConverter::new);
        layout.getInstanceConverterMap().put("maskEx", MaskingThrowableConverter::new);
        layout.setPattern(CONSOLE_PATTERN);
        layout.start();
        return layout;
    }

    @Test
    public void theMessageAndTheExceptionMessageAreBothMasked() {
        String line = consoleLayout().doLayout(event(
                "login password=hunter2hunter2 failed",
                new IllegalStateException("db failed: jdbc:mysql://h/db?user=a&password=hunter2hunter2")));

        assertFalse(line.contains("hunter2hunter2"), line);
        assertTrue(line.contains("password=***"), line);
        assertTrue(line.contains("IllegalStateException"), "the exception is still shown: " + line);
        assertTrue(line.contains("db failed"), "the rest of the exception message is kept: " + line);
    }

    @Test
    public void aNestedCauseIsMaskedToo() {
        Throwable cause = new RuntimeException("upstream said token=ABCDEF123456");
        String line = consoleLayout().doLayout(event("call failed", new IllegalStateException("wrapper", cause)));

        assertFalse(line.contains("ABCDEF123456"), line);
        assertTrue(line.contains("Caused by"), line);
    }

    @Test
    public void aLineWithoutAnExceptionHasNoStackTraceSection() {
        String line = consoleLayout().doLayout(event("plain message", null));
        assertEquals(line.trim(), "WARN  probe - plain message");
    }

    @Test
    public void theExceptionConverterMasksOnItsOwnWhenUsedDirectly() {
        MaskingThrowableConverter converter = new MaskingThrowableConverter();
        converter.start();
        String rendered = converter.convert(event("m", new IllegalStateException("password=hunter2hunter2")));
        assertFalse(rendered.contains("hunter2hunter2"), rendered);
        assertEquals(converter.convert(event("m", null)), "", "no exception, nothing to render");
    }
}
