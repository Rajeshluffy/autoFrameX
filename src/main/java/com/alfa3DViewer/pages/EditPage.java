package com.alfa3DViewer.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class EditPage  extends ProjectSpecificMethods{
	
	
	public EditPage selectEdit() {
		click(locateElement(Locators.XPATH,"(//span[@class='mat-button-wrapper'])[1]"));
		reportStep("Successfully selected edit command","pass");
		return this;
	}
	
	public EditPage selectSelectButton() {
		selectEdit();
		click(locateElement(Locators.XPATH,"(//div[@id='cdk-overlay-12']//button)[1]"));
		reportStep("Successfully selected select command","pass");
		return this;
	}
	
	
	public EditPage selectDimension() {
		selectEdit();
		click(locateElement(Locators.XPATH,"(//div[@id='cdk-overlay-12']//button)[2]"));
		reportStep("Successfully selected dimension command","pass");
		return this;
	}
	
	public EditPage selectComment() {
		selectEdit();
		click(locateElement(Locators.XPATH,"(//div[@id='cdk-overlay-12']//button)[3]"));
		reportStep("Successfully selected comment command","pass");
		return this;
	}
	
	
	public EditPage ScreenShot() {
		selectEdit();
		click(locateElement(Locators.XPATH,"(//div[@id='cdk-overlay-12']//button)[4]"));
		reportStep("Successfully selected screenshot command","pass");
		return this;
	}



	
	

}
