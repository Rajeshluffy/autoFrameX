package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.testng.api.base.TestMetadata;
import com.leaftaps.pages.FindLeadPage;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "DeleteLead",
		description = "Verify if the lead has been deleted",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "DeleteLead"
)
public class TC005_DeleteLead extends ProjectSpecificMethods{

	@Test(dataProvider = "fetchData")
	public void runLogin(String uname,String pass,String firstName,String errorMsg) {
		String firstResultingLead =
		new LoginPage().enterUsername(uname).enterPassword(pass).clickLogin().clickCrmsfaLink().clickLeadsLink().clickFindLead()
	.enterLeadName(firstName)
	.clickFindleadsButton()
	.getFirstResultingLead();
	
	new FindLeadPage().clickFirstResultingLead()
	.clickDeleteLeadLink()
	.clickFindLead().enterLeadID(firstResultingLead).clickOnFindleadsButton().verifyErrorMsg(errorMsg);
	
	
	}

	

}
