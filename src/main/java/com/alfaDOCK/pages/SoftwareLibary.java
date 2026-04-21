package com.alfaDOCK.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;
import com.gpn.pages.GPNHomePage;

public class SoftwareLibary extends ProjectSpecificMethods{

	
	public SoftwareLibary verifySoftwareLibary() {
		verifyDisplayed(locateElement(Locators.XPATH,"//div[@class='ui-g homemain']"));
		reportStep("Entered Software Libary successfully", "pass");
		return this;
	}
	
	public GPNHomePage selectGPN() {
		clickWithJs(locateElement(Locators.XPATH,"(//input[@src='assets/icons/aischeduler.png'])[1]"));
		reportStep("Clicked Software Libary successfully", "pass");
		return new GPNHomePage();
	}
	
}
