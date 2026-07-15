package com.framework.utils.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import com.framework.utils.EncryptionUtils;

/**
 * Logback pattern converter that masks credential-shaped substrings (passwords,
 * bearer tokens, etc.) via {@link EncryptionUtils#maskSensitiveValues} before a
 * message reaches any appender.
 *
 * <p>Registered as the {@code %mask} conversion word (see {@code logback.xml}'s
 * {@code conversionRule}), used in place of {@code %msg} in the console pattern
 * so masking applies automatically to every log line rather than requiring each
 * call site to remember to mask manually.
 */
public class MaskingMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return EncryptionUtils.maskSensitiveValues(event.getFormattedMessage());
    }
}
