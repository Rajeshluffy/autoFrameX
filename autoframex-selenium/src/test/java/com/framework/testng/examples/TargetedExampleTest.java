package com.framework.testng.examples;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.AccountData;
import com.framework.utils.TestMetadata;
import com.framework.utils.ValidationUtils;

/**
 * Reference example for {@code testng-targeted.xml} (TARGETED mode) — a new
 * project team's first {@code mvn test} against this suite runs this class
 * once against the single account configured in the suite XML, demonstrating
 * {@link #getAccount()} without any DataProvider. Replace with your real page
 * objects/tests once the wiring makes sense.
 */
@TestMetadata(name = "TargetedExampleTest", authors = "framework", category = "Example")
public class TargetedExampleTest extends ProjectSpecificMethods {

    @Test
    public void verifyConfiguredAccountUrl() {
        AccountData account = getAccount();
        getDriver().get(account.getUrl());
        ValidationUtils.assertTextContains(getDriver().getTitle(), "Example Domain",
                "Targeted example: page title check for account " + account.getAccountId());
    }
}
