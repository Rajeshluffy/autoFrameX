package com.alfa3DViewer.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Dimension  extends ProjectSpecificMethods{
	

	public Dimension selectDimension() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[10]"));
		reportStep("Succesfully selected dimension command","pass");
		return this;
	}
	
	
	
	

}
