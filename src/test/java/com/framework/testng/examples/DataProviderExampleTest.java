package com.framework.testng.examples;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.AccountData;
import com.framework.utils.TestMetadata;
import com.framework.utils.ValidationUtils;

/**
 * Reference example for {@code testng-data-provider.xml} (DATA_PROVIDER mode)
 * — a new project team's first {@code mvn test} against this suite runs this
 * class against every account row in {@code data/accounts.xlsx} and passes,
 * demonstrating the {@code fetchData} DataProvider wiring end-to-end without
 * needing any app of your own. Replace with your real page objects/tests once
 * the wiring makes sense; the account's own URL (column 4 in the Excel file)
 * drives navigation, so this needs no external app to run out of the box.
 */
@TestMetadata(name = "DataProviderExampleTest", authors = "framework", category = "Example")
public class DataProviderExampleTest extends ProjectSpecificMethods {

    @Test(dataProvider = "fetchData")
    public void verifyAccountUrl(AccountData account) {
        getDriver().get(account.getUrl());
        ValidationUtils.assertTextContains(getDriver().getTitle(), "Example Domain",
                "DataProvider example: page title check for account " + account.getAccountId());
    }
}
