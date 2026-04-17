package com.alfa3DViewer.pages;



import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Screenshot  extends ProjectSpecificMethods{


	public Screenshot selectScreenshot() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[4]"));
		reportStep("Selected screenshot Pannel command succesfully","pass");
		return this;
	}

	
	




}
