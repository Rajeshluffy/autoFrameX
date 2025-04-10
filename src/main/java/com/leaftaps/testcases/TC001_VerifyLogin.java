package com.leaftaps.testcases;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.leaftaps.pages.LoginPage;

public class TC001_VerifyLogin extends ProjectSpecificMethods{
	
	@BeforeTest
	public void setValues() {
		testcaseName = "VerifyLogin";
		testDescription ="Verify Login functionality with positive data";
		authors="Hari";
		category ="Smoke";
		excelFileName="Login";
	}
	
	@Test(dataProvider = "fetchData")
	public void runLogin(String uname,String pass) {
	LoginPage lp=new LoginPage();
	lp.enterUsername(uname);
	}

	@Test(dataProvider = "fetchData")
	public void runLogin1(String uname,String pass) {
	LoginPage lp=new LoginPage();
	lp.enterPassword(pass);
	}
	@Test(dataProvider = "fetchData")
	public void runLogin2(String uname,String pass) {
	LoginPage lp=new LoginPage();
	lp.enterUsername(uname).enterPassword(pass).clickLogin();
	}

}