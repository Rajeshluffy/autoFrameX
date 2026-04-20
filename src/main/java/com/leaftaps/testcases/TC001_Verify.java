package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.testng.api.base.TestMetadata;
import com.leaftaps.pages.LoginPage;

/**
 * Data-provider driven login verification.
 *
 * <p>Credentials come from {@code data/Login.xlsx} via the inherited
 * {@code fetchData} data provider (defined in {@link ProjectSpecificMethods}).
 * {@code @TestMetadata(excelFile = "Login")} sets {@code excelFileName} so the
 * provider knows which file to read — without it the provider returns an empty
 * dataset and no test runs.
 *
 * <p>The parent provider uses {@code indices = 0}, meaning only the first Excel
 * row is used per smoke-test run.  Remove that annotation attribute if you want
 * all rows executed.
 *
 * <p>Used by: {@code testng-test-dataprovider.xml}
 */
@TestMetadata(
        name        = "VerifyLogin",
        description = "Verify Login functionality using Excel data provider",
        authors     = "Rajesh",
        category    = "Smoke",
        excelFile   = "Login"     // links to data/Login.xlsx  (no extension)
)
public class TC001_Verify extends ProjectSpecificMethods {

    /**
     * Verifies the end-to-end login flow using a single pair of credentials
     * supplied by the inherited {@code fetchData} data provider.
     *
     * <p><b>Column order in Login.xlsx must match the parameter order here:</b>
     * <ol>
     *   <li>uname — username / email</li>
     *   <li>pass  — password</li>
     * </ol>
     *
     * @param uname username read from Excel column 1
     * @param pass  password read from Excel column 2
     */
    @Test(dataProvider = "fetchData")
    public void runLogin(String uname, String pass) {
        new LoginPage()
                .enterUsername(uname)
                .enterPassword(pass)
                .clickLogin();
    }
}