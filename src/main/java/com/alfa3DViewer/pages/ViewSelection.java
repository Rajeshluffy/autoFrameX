package com.alfa3DViewer.pages;



import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class ViewSelection  extends ProjectSpecificMethods{


	public ViewSelection selectViewSelection() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[5]"));
		reportStep("Selected View Selection command succesfully","pass");
		return this;
	}
	
	public ViewSelection deSelectViewSelection() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[5]"));
		reportStep("De-Selected View Selection command succesfully","pass");
		return this;
	}

	public ViewSelection selectFrontView() {
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-1']//button)[1]"));
		reportStep("Selected Front view command succesfully","pass");
		return this;
	}
	
	public ViewSelection selectBackView() {
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-1']//button)[2]"));
		reportStep("Selected Back view command succesfully","pass");
		return this;
	}
	
	public ViewSelection selectLeftView() {
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-1']//button)[3]"));
		reportStep("Selected Left view command succesfully","pass");
		return this;
	}
	
	public ViewSelection selectRightView() {
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-1']//button)[4]"));
		reportStep("Selected Right view command succesfully","pass");
		return this;
	}
	
	public ViewSelection selectToptView() {
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-1']//button)[5]"));
		reportStep("Selected Top view command succesfully","pass");
		return this;
	}
	
	public ViewSelection selectBottomView() {
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-1']//button)[6]"));
		reportStep("Selected Left view command succesfully","pass");
		return this;
	}
	
	public ViewSelection selectIsometricView() {
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-1']//button)[7]"));
		reportStep("Selected Isometric view command succesfully","pass");
		return this;
	}
	
	public ViewSelection selectTrimetricView() {
		click(locateElement(Locators.XPATH,"(//div[@id='mat-menu-panel-1']//button)[8]"));
		reportStep("Selected Trimetric view command succesfully","pass");
		return this;
	}
	

}
