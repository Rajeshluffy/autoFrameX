package com.alfaDOCK.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class DrawingManager extends ProjectSpecificMethods {


	public DrawingManager selectSearch() {
		waitForApperance(locateElement(Locators.XPATH,"//div[@id='filtergroup']/input"));
		clickWithJs(locateElement(Locators.XPATH,"//div[@id='filtergroup']//input"));
		reportStep("Succesfully select the Drawing 5s search button","pass");
		return this;
	}

	public DrawingManager select5S() {
		clickWithJs(locateElement(Locators.XPATH,"//img[@src='assets/digital5S.png']"));
		reportStep("Succesfully select the Drawing 5s search button","pass");
		return this;
	}

	public DrawingManager enterSearchValue(String val) {
		append(locateElement(Locators.XPATH,"//div[@id='filtergroup']/input"), val);
		reportStep("Succesfully Enter the value in the search textbox","pass");
		return this;
	}

	public DrawingManager selectTheFilter() {
		//filter DropDown
		clickWithJs(locateElement(Locators.XPATH,"//div[@id='filtergroup']/button"));
		reportStep("Succesfully select the filter dropdown button","pass");
		return this;
	}


	public DrawingManager selectTheType() {
		//type dropDown
		clickWithJs(locateElement(Locators.XPATH,"(//label[text()='Not Specified'])[2]"));
		reportStep("Succesfully Enter the value in the search","pass");
		return this;
	}

	public DrawingManager selectTheFileType() {
		clickWithJs(locateElement(Locators.XPATH,"(//p-dropdown[@filter='true']//label)[2]"));
		reportStep("Succesfully select the dropdown","pass");
		return this;
	} 
	public DrawingManager selectTheType(String val) {
		clickWithJs(locateElement(Locators.XPATH,"//span[text()='"+val.toUpperCase()+"']"));
		reportStep("Succesfully select the type","pass");
		return this;
	}

	public DrawingManager selectTheSearchbutton() {
		clickWithJs(locateElement(Locators.XPATH,"//img[@src='assets/ai-search.png']"));
		reportStep("Succesfully select The Search button","pass");
		return this;
	}


	public DrawingManager  selectTheFirstElement() {
		doubleClick(locateElement(Locators.XPATH,"//div[@class='imageDiv']"));
		reportStep("Succesfully select The Search button","pass");
		return this;
	}


	public DrawingManager  switchtoViewerPage(int windowIndex) {
		switchToWindow(windowIndex);
		reportStep("Succesfully select The Search button","pass");
		return this;
	}

	public DrawingManager verifyDrawingManager() {

		verifyDisplayed(locateElement(Locators.ID,"drive"));
		reportStep("Succesfully entered into Drawing Manager Page","pass");
		return this;
	}


}
