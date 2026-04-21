package com.gpn.testcases;

import org.testng.annotations.Test;

import com.alfaDOCK.pages.AlfaDockHomePage;
import com.alfaDOCK.testcases.AlfaDockBaseTest;
import com.framework.testng.api.base.TestMetadata;
@TestMetadata(
		name = "GPN Filter",
		description = "Verify user login and search file functionality in Drawing Manager",
		authors = "Rajesh",
		category = "Smoke"
		)
public class TC001_VerifyFilterButton extends AlfaDockBaseTest  {

	@Test
	public void FilterButton() {
		drinst();
		new AlfaDockHomePage().selectSoftwareLibary().verifySoftwareLibary().selectGPN().verifyGPNHomePage().selectFilter();
	}
}
