package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.testng.api.base.TestMetadata;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "EditLead",
		description = "Verify whether the existing lead has been edited",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "EditLead"
)
public class TC004_EditLead extends ProjectSpecificMethods{

	@Test(dataProvider = "fetchData")
	public void runLogin(String uname,String pass,String firstName,String updateComName) {
	LoginPage lp=new LoginPage();
	lp.enterUsername(uname).enterPassword(pass).clickLogin().clickCrmsfaLink().clickLeadsLink().clickFindLead()
	.enterLeadName(firstName).clickFindleadsButton().clickFirstResultingLead().clickEditLeadLink()
	.updateCompanyName(updateComName).clickUpdateSubmit().verifyCompanyName(updateComName);

	}

	

}
