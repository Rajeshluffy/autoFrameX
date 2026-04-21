package com.gpn.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class GPNHomePage extends ProjectSpecificMethods{

	public GPNHomePage verifyGPNHomePage(){
		verifyDisplayed(locateElement(Locators.ID,"ToolBarDiv"));
		reportStep("Successfully Entered GPN","pass");
		return this;
	}
	
	public GPNHomePage selectFilter(){
		clickWithJs(locateElement(Locators.XPATH,"//i[@title='フィルタ']"));
		reportStep("Selected Filter Button","pass");
		return this;
	}
	
}
