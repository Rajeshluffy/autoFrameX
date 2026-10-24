package com.api.rest.assured.base;

import com.framework.utils.EncryptionUtils;

/**
 * The one place the API module hides credentials before text leaves it: report steps, exception messages and
 * assertion failures. The log files were already masked by the logging layer, but the Extent report and the
 * messages TestNG prints were not, and a URL with {@code ?access_token=...} or a body preview with a token in it
 * went straight into them.
 */
public final class ApiRedaction {

    private ApiRedaction() {}

    /** {@code text} with passwords, secrets, tokens, keys and Authorization values replaced by {@code ***}. */
    public static String mask(String text) {
        return text == null ? null : EncryptionUtils.maskSensitiveValues(text);
    }

    /** {@link #mask} then cut to {@code maxChars}, for a body preview in a failure message. */
    public static String preview(String text, int maxChars) {
        if (text == null) {
            return "(none)";
        }
        String masked = mask(text);
        return masked.length() <= maxChars ? masked : masked.substring(0, maxChars) + "...";
    }
}
