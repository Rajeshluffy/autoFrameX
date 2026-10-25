package com.framework.utils.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import com.framework.utils.EncryptionUtils;

/**
 * Renders an exception and its stack trace like the default {@code %ex}, with credential-shaped substrings
 * masked via {@link EncryptionUtils#maskSensitiveValues}.
 *
 * <p>{@link MaskingMessageConverter} ({@code %mask}) masks only the log message. An exception's own message is
 * a separate part of the line, and it often carries what the message was careful to hide: a connection string,
 * a URL with a token, a request body. Without this, a console line that masked {@code password=***} in the
 * message printed {@code password=hunter2} two lines later in the stack trace. Registered as {@code %maskEx} in
 * {@code logback.xml}; the JSON appender masks stack traces separately in {@link MaskingLogstashEncoder}.
 */
public class MaskingThrowableConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return EncryptionUtils.maskSensitiveValues(super.convert(event));
    }
}
