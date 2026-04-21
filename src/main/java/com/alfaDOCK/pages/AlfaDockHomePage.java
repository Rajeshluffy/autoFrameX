package com.alfaDOCK.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class AlfaDockHomePage extends ProjectSpecificMethods {


	public DrawingManager selectDrawingManager() {
		clickWithJs(locateElement(Locators.XPATH,"//input[@src='assets/icons/hyperLink.png']/following::img[1]"));
		reportStep("Succesfully select the User login button","pass");
		return new DrawingManager();
	}

	public AlfaDockHomePage verifyHomePage() {
		
		verifyDisplayed(locateElement(Locators.ID,"homemain"));
		reportStep("Succesfully Home Page is openned","pass");
		return this;
	}

	
	public SoftwareLibary selectSoftwareLibary() {
		clickWithJs(locateElement(Locators.XPATH,"//input[@src='assets/icons/Gaia.png']"));
		reportStep("Succesfully select the User login button","pass");
		return new SoftwareLibary();
	}



}
