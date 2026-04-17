package com.alfa3DViewer.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class ImportPage  extends ProjectSpecificMethods{
	
	
	public ImportPage selectFile() {
		click(locateElement(Locators.XPATH,"(//span[@class='mat-button-wrapper'])[1]"));
		reportStep("Selected File command succesfully","pass");
		return this;
	}
	
	
	
	public ImportPage selectOpenButton() {
		selectFile();
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-2']//button)[1]"));
		reportStep("Selected Open command succesfully","pass");
		return this;
	}
	
	public ImportPage selectSaveButton() {
		selectFile();
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-2']//button)[2]"));
		reportStep("Selected Save command succesfully","pass");
		return this;
	}
	
	
	

}
