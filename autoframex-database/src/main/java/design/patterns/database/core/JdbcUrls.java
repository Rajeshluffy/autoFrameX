package design.patterns.database.core;

import java.util.regex.Pattern;

/** Makes a JDBC URL safe to write to a log or an exception message. */
public final class JdbcUrls {

    /** {@code password=...} / {@code pwd=...} as a URL parameter ({@code &}, {@code ;} or {@code ?} separated). */
    private static final Pattern PASSWORD_PARAMETER = Pattern.compile("(?i)((?:password|pwd)=)[^&;\\s]*");
    /** {@code user:password@host}. */
    private static final Pattern USER_INFO = Pattern.compile("(?<=//)([^/:@\\s]{1,200}):[^@/\\s]{1,200}@");

    private JdbcUrls() {}

    /**
     * Returns {@code url} with any password replaced by {@code ***}. A URL may carry its credentials
     * ({@code ?user=bob&password=...}, {@code //bob:...@host}), and the URL is what an error message and a debug
     * line naturally print.
     *
     * @return the redacted URL, or {@code <none>} for {@code null}
     */
    public static String redact(String url) {
        if (url == null) {
            return "<none>";
        }
        String redacted = PASSWORD_PARAMETER.matcher(url).replaceAll("$1***");
        return USER_INFO.matcher(redacted).replaceAll("$1:***@");
    }
}
