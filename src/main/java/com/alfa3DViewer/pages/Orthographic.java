package com.alfa3DViewer.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Orthographic  extends ProjectSpecificMethods{


	public Orthographic selectOrthographic() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[4]"));
		reportStep("Selected Orthographic command succesfully","pass");
		return this;
	}


	public Orthographic selectIsometricView() {
		click(locateElement(Locators.XPATH,"(//mat-radio-group[@role='radiogroup']//mat-radio-button)[1]"));
		reportStep("Selected Isometric command succesfully","pass");
		return this;
	}
	
	public Orthographic selectFrontView() {
		click(locateElement(Locators.XPATH,"(//mat-radio-group[@role='radiogroup']//mat-radio-button)[2]"));
		reportStep("Selected Front View command succesfully","pass");
		return this;
	}
	
	public Orthographic selectBackView() {
		click(locateElement(Locators.XPATH,"(//mat-radio-group[@role='radiogroup']//mat-radio-button)[3]"));
		reportStep("Selected Back command succesfully","pass");
		return this;
	}
	
	public Orthographic selectTopView() {
		click(locateElement(Locators.XPATH,"(//mat-radio-group[@role='radiogroup']//mat-radio-button)[4]"));
		reportStep("Selected Top View command succesfully","pass");
		return this; 
	}
	
	public Orthographic selectBottomView() {
		click(locateElement(Locators.XPATH,"(//mat-radio-group[@role='radiogroup']//mat-radio-button)[5]"));
		reportStep("Selected Bottom View command succesfully","pass");
		return this;
	}
	
	public Orthographic selectLeftView() {
		click(locateElement(Locators.XPATH,"(//mat-radio-group[@role='radiogroup']//mat-radio-button)[6]"));
		reportStep("Selected Left View command succesfully","pass");
		return this;
	}
	
	public Orthographic selectRightView() {
		click(locateElement(Locators.XPATH,"(//mat-radio-group[@role='radiogroup']//mat-radio-button)[7]"));
		reportStep("Selected RightView command succesfully","pass");
		return this;
	}
	
	



	




}
