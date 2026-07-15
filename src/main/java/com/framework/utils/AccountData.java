package com.framework.utils;

/**
 * Typed representation of a single test account row.
 *
 * <p>Column convention for Excel / CSV data files:
 * <pre>
 *   col 0 → accountId   (e.g. "ACC001")
 *   col 1 → userName    (login email / username)
 *   col 2 → password
 *   col 3 → url         (optional — leave blank to use global appUrl)
 * </pre>
 *
 * <p>In <b>Data Provider</b> mode, one instance is created per Excel row and
 * injected into each {@code @Test(dataProvider="fetchData")} method.
 * In <b>Targeted</b> mode, one instance is built from TestNG XML parameters.
 *
 * <p><b>Package placement:</b> lives alongside {@link DataLibrary} (its
 * primary constructor site, via {@link #fromRow}) rather than in
 * {@code com.framework.testng.api.base} deliberately — the previous placement
 * created a real circular package dependency ({@code utils → testng.api.base →
 * selenium.api.base → utils}, via {@code DataLibrary} importing it while
 * {@code SeleniumBase} extended {@code Reporter} (also in {@code utils}) and
 * {@code ProjectSpecificMethods} (in {@code testng.api.base}) extended
 * {@code SeleniumBase}).
 */
public class AccountData {

    private final String accountId;
    private final String userName;
    private final String password;
    private final String url;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public AccountData(String accountId, String userName, String password, String url) {
        this.accountId = accountId != null ? accountId.trim() : "";
        this.userName  = userName  != null ? userName.trim()  : "";
        this.password  = password  != null ? password.trim()  : "";
        this.url       = url       != null ? url.trim()       : "";
    }

    /**
     * Builds an {@code AccountData} from a raw Excel / CSV row ({@code Object[]}).
     * Column order: accountId, userName, password, url (url is optional).
     *
     * @param row raw data row — must have at least 3 elements
     * @return typed account object
     * @throws IllegalArgumentException if the row has fewer than 3 elements
     */
    public static AccountData fromRow(Object[] row) {
        if (row == null || row.length < 3) {
            throw new IllegalArgumentException(
                "Account data row must have at least 3 columns: [accountId, userName, password]");
        }
        String accountId = str(row, 0);
        String userName  = str(row, 1);
        String password  = str(row, 2);
        String url       = str(row, 3);
        return new AccountData(accountId, userName, password, url);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getAccountId() { return accountId; }
    public String getUserName()  { return userName;  }
    public String getPassword()  { return password;  }

    /**
     * Returns the per-account URL, or an empty string if not set.
     * When empty, the driver setup falls back to the global {@code appUrl} from config.
     */
    public String getUrl()       { return url; }

    /** {@code true} when this account has its own URL that should override the global one. */
    public boolean hasUrl()      { return url != null && !url.isEmpty(); }

    @Override
    public String toString() {
        return "AccountData{accountId='" + accountId + "', userName='" + userName + "'}";
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String str(Object[] row, int idx) {
        if (idx >= row.length || row[idx] == null) return "";
        return row[idx].toString().trim();
    }
}
