package com.alfa3DViewer.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Navigation  extends ProjectSpecificMethods{



	public Navigation selectAutoFitZoom() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[5]"));
		reportStep("Selected Auto Fit command succesfully","pass");
		return this;
	}

	public Navigation selectZoomIn() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[6]"));
		reportStep("Selected ZoomIn command succesfully","pass");
		return this;
	}


	public Navigation deSelectZoomIn() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[6]"));
		reportStep("De-Selected ZoomIn command succesfully","pass");
		return this;
	}

	public Navigation selectZoomOut() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[7]"));
		reportStep("Selected ZoomOut command succesfully","pass");
		return this;
	}


	public Navigation deSelectZoomOut() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[7]"));
		reportStep("De-Selected ZoomOut command succesfully","pass");
		return this;
	}


	public Navigation selectRotate() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[8]"));
		reportStep("Selected Rotate command succesfully","pass");
		return this;
	}


	public Navigation deSelectRotate() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[8]"));
		reportStep("De-Selected Rotate command succesfully","pass");
		return this;
	}

	public Navigation selectPan() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[9]"));
		reportStep("Selected Pan command succesfully","pass");
		return this;
	}


	public Navigation deSelectPan() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[9]"));
		reportStep("De-Selected ZoomIn command succesfully","pass");
		return this;
	}

	public Navigation clickSelect() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[12]"));
		reportStep("Selected select command succesfully","pass");
		return this;
	}

	public Navigation reclickSelect() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[12]"));
		reportStep("De-Selected reselect command succesfully","pass");
		return this;
	}



}
