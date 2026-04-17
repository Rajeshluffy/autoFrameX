package com.alfa3DViewer.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Layers  extends ProjectSpecificMethods{


	public Layers selectLayers() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[2]"));
		reportStep("Successfully selected the layer command","pass");
		return this;
	}


	public Layers selectModelLayer() {
		click(locateElement(Locators.XPATH,"(//app-layers-panel//div[@class='ng-star-inserted']//div)[1]"));
		reportStep("Successfully selected the Model layer command","pass");
		return this;
	}

	public Layers isModelLayerEnabled() {

		boolean checkBoxState = verifyEnabled(locateElement(Locators.XPATH,"//app-layers-panel//div[@class='ng-star-inserted']//input[1]"));
		if (checkBoxState) {
			reportStep("Model checkbox current status: " + checkBoxState,"pass");
	
		}else {
			reportStep("Model checkbox current status: " + checkBoxState,"fail");

		}
		return this;
	}

	public Layers selectDimensionlLayer() {
		click(locateElement(Locators.XPATH,"(//app-layers-panel//div[@class='ng-star-inserted']//input)[2]"));
		reportStep("Successfully selected the Dimension command","pass");
		return this;
	}

	public boolean isDimensionlLayerEnabled() {
		boolean checkBoxState = verifyEnabled(locateElement(Locators.XPATH,"//app-layers-panel//div[@class='ng-star-inserted']//input[2]"));
		reportStep("Dimension checkbox current status: " + checkBoxState,"pass");
		return checkBoxState;
	}

	public Layers selectCommentLayer() {
		click(locateElement(Locators.XPATH,"(//app-layers-panel//div[@class='ng-star-inserted']//input)[3]"));
		reportStep("Successfully selected the Comment command","pass");
		return this;
	}

	public boolean isCommentLayerEnabled() {
		boolean checkBoxState = verifyEnabled(locateElement(Locators.XPATH,"//app-layers-panel//div[@class='ng-star-inserted']//input[3]"));
		reportStep("Comment checkbox current status: " + checkBoxState,"pass");
		return checkBoxState;
	}
	
	public Layers selectWeldLayer() {
		click(locateElement(Locators.XPATH,"(//app-layers-panel//div[@class='ng-star-inserted']//input)[4]"));
		reportStep("Successfully selected the Weld command","pass");
		return this;
	}

	public boolean isWeldLayerEnabled() {
		boolean checkBoxState = verifyEnabled(locateElement(Locators.XPATH,"//app-layers-panel//div[@class='ng-star-inserted']//input[4]"));
		reportStep("Weld checkbox current status: " + checkBoxState,"pass");
		return checkBoxState;
	}
	
	public Layers selectTempWeldLayer() {
		click(locateElement(Locators.XPATH,"(//app-layers-panel//div[@class='ng-star-inserted']//input)[5]"));
		reportStep("Successfully selected the Temp weld command","pass");
		return this;
	}

	public boolean isTempWeldLayerEnabled() {
		boolean checkBoxState = verifyEnabled(locateElement(Locators.XPATH,"//app-layers-panel//div[@class='ng-star-inserted']//input[5]"));
		reportStep("Temp weld checkbox current status: " + checkBoxState,"pass");
		return checkBoxState;
	}
	
	
	public Layers selectlaserLayer() {
		click(locateElement(Locators.XPATH,"(//app-layers-panel//div[@class='ng-star-inserted']//input)[6]"));
		reportStep("Successfully selected the laser command","pass");
		return this;
	}

	public boolean islaserLayerEnabled() {
		boolean checkBoxState = verifyEnabled(locateElement(Locators.XPATH,"//app-layers-panel//div[@class='ng-star-inserted']//input[6]"));
		reportStep("Laser checkbox current status: " + checkBoxState,"pass");
		return checkBoxState;
	}

	public Layers selectMaskLayer() {
		click(locateElement(Locators.XPATH,"(//app-layers-panel//div[@class='ng-star-inserted']//input)[7]"));
		reportStep("Successfully selected the Mask command","pass");
		return this;
	}

	public boolean isMaskLayerEnabled() {
		boolean checkBoxState = verifyEnabled(locateElement(Locators.XPATH,"//app-layers-panel//div[@class='ng-star-inserted']//input[7]"));
		reportStep("Mask checkbox current status: " + checkBoxState,"pass");
		return checkBoxState;
	}
	
	public Layers selectAutoDimensionLayer() {
		click(locateElement(Locators.XPATH,"(//app-layers-panel//div[@class='ng-star-inserted']//input)[8]"));
		reportStep("Successfully selected the Auto Dimension command","pass");
		return this;
	}

	public boolean isAutoDimensionLayerEnabled() {
		boolean checkBoxState = verifyEnabled(locateElement(Locators.XPATH,"//app-layers-panel//div[@class='ng-star-inserted']//input[8]"));
		reportStep("Auto Dimension checkbox current status: " + checkBoxState,"pass");
		return checkBoxState;
	}




}
