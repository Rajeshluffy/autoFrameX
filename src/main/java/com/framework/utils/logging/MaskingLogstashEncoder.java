package com.framework.utils.logging;

import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.spi.ILoggingEvent;

import com.framework.utils.EncryptionUtils;

import net.logstash.logback.encoder.LogstashEncoder;

/**
 * Drop-in replacement for {@link LogstashEncoder} that masks credential-shaped
 * substrings (passwords, bearer tokens, etc.) in the serialized JSON line via
 * {@link EncryptionUtils#maskSensitiveValues} before it's written to disk.
 *
 * <p>Logback {@code Filter}s can only accept/deny an event, not rewrite its
 * content, so masking has to happen at the encoder layer — this is the file
 * (ELK-bound JSON) counterpart to {@link MaskingMessageConverter}, which
 * covers the console appender's pattern-based output.
 */
public class MaskingLogstashEncoder extends LogstashEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        String json = new String(super.encode(event), StandardCharsets.UTF_8);
        return EncryptionUtils.maskSensitiveValues(json).getBytes(StandardCharsets.UTF_8);
    }
}
