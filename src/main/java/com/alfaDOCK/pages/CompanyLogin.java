package com.alfaDOCK.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class CompanyLogin extends ProjectSpecificMethods {

	public CompanyLogin selectLanguage(String language) {
		selectDropDownUsingValue(locateElement(Locators.XPATH, "//select[@id='mySelect' or ancestor::div[@id='program']]"), language);
		reportStep("Succesfully language changed","pass");
		return this;
	}

	public CompanyLogin enterCompanyUserName(String username) {
		clearAndType(locateElement(Locators.XPATH, "//input[@id='username' or @id='complogin_username']"), username);
		reportStep("Succesfully Entered the Comapny Username","pass");
		return this;
	}

	public CompanyLogin enterCompanyPassword(String password) {
		clearAndType(locateElement(Locators.XPATH, "//input[@id='password' or @id='complogin_password']"), password);
		reportStep("Succesfully Entered the Comapny password","pass");
		return this;
	}

	public UserLogin selectCompanyUserLogin() {
		click(locateElement(Locators.ID,"logmein"));
		reportStep("Succesfully select the company login button","pass");
		return new UserLogin();
	}



	public UserLogin loginCompanyPage(String lng,String user, String password) {
		selectLanguage(lng);
		enterCompanyUserName(user);
		enterCompanyPassword(password);
		return selectCompanyUserLogin();

	}


}
