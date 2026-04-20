package com.alfaDOCK.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class UserLogin extends ProjectSpecificMethods {

	
	public UserLogin enterUserUserName(String username) {
		clearAndType(locateElement(Locators.XPATH, "//input[@id='username' or @id='userlogin_username']"), username);
		reportStep("Succesfully Enter the User Username","pass");
		return this;
	}

	public UserLogin enterUserPassword(String password) {
		clearAndType(locateElement(Locators.XPATH, "//input[@id='password' or @id='userlogin_password']"), password);
		reportStep("Succesfully Enter the User Username","pass");
		return this;
	}
	
	public AlfaDockHomePage selectUserLogin() {
		click(locateElement(Locators.ID,"login"));
		reportStep("Succesfully select the User login button","pass");
		return new AlfaDockHomePage();
	}
	
	public UserLogin verifyUserLoginPage() {
		waitForApperance(locateElement(Locators.ID,"userlogin"));	
		return this;
	}

	
	public AlfaDockHomePage userLoginPage(String lng,String user, String password) {
		enterUserUserName(user);
		enterUserPassword(password);
		return selectUserLogin();
	}


	
	
	
}
