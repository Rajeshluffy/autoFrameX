package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.testng.api.base.TestMetadata;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "CreateLead",
		description = "Verify that the lead is created",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "CreateLead"
)
public class TC003_CreateLead extends ProjectSpecificMethods{

	@Test(dataProvider = "fetchData")
	public void runLogin(String uname,String pass,String compantName,String firstName,String lastName) {
	LoginPage lp=new LoginPage();
	lp.enterUsername(uname).enterPassword(pass).clickLogin().clickCrmsfaLink().clickLeadsLink().clickCreateLeadLink()
	.enterCompanyName(compantName).enterFirstName(firstName).enterLastName(lastName).clickCreateLeadButton().verifyFirstName(firstName);
	}

	
}
