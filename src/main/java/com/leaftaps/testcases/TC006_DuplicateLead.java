package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.testng.api.base.TestMetadata;
import com.leaftaps.pages.FindLeadPage;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "DuplicateLead",
		description = "Verify if the lead is duplicated",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "DuplicateLead"
)
public class TC006_DuplicateLead extends ProjectSpecificMethods{

	@Test(dataProvider = "fetchData")
	public void runLogin(String uname,String pass,String firstName) {
		
		
		new LoginPage().enterUsername(uname).enterPassword(pass).clickLogin().clickCrmsfaLink().clickLeadsLink().clickFindLead()
	.enterLeadName(firstName)
	.clickFindleadsButton()
	.getFirstResultingLead();
	
	new FindLeadPage().clickFirstResultingLead()
	.clickDuplicateLink()
	.clickCreateLeadDublicate()
	.verifyFirstName(firstName);
	
	
	}

	

}
