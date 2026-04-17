package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.testng.api.base.TestMetadata;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "VerifyLogOut",
		description = "Verify LogOut functionality with positive data",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "Login"
)
public class TC002_VerifyLogout extends ProjectSpecificMethods{

	@Test(dataProvider = "fetchData")
	public void runLogin(String uname,String pass) {
	LoginPage lp=new LoginPage();
	lp.enterUsername(uname).enterPassword(pass).clickLogin().clickLogOut();
	}

	

	

}
