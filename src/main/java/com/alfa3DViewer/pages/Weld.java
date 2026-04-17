package com.alfa3DViewer.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Weld  extends ProjectSpecificMethods{


	public Weld selectWeld() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[3]"));
		reportStep("Selected Weld  command succesfully","pass");
		return this;
	}


	public Weld enterWeldRadius(String rad) {
		clearAndType(locateElement(Locators.ID,"Radiusid"), rad);
		reportStep("Entered the weld radius succesfully","pass");
		return this;
	}

	public Weld enterWeldPitch(String pitch) {
		clearAndType(locateElement(Locators.ID,"Pitchid"), pitch);
		reportStep("Entered the weld pitch succesfully","pass");
		return this;
	}



	public Weld enterOffsetWeldLength(String offsetLength) {
		clearAndType(locateElement(Locators.ID,"Offsetid"), offsetLength);
		reportStep("Entered the Offset Weld Length succesfully","pass");
		return this;
	}


	public Weld enterWeldLength(String length) {
		clearAndType(locateElement(Locators.ID,"Lengthid"), length);
		reportStep("Entered the weld length succesfully","pass");
		return this;
	}



	public Weld selectDatumRadio() {
		click(locateElement(Locators.XPATH,"//mat-radio-button[@value='1']//label"));
		reportStep("Selected datum radio button succesfully","pass");
		return this;
	}


	public Weld selectBothendRadio() {
		click(locateElement(Locators.XPATH,"//mat-radio-button[@value='2']//label"));
		reportStep("Selected Both End radio button succesfully","pass");
		return this;
	}

	public Weld selectYellowWeld() {
		click(locateElement(Locators.XPATH,"//mat-button-toggle[@value='Thin']//button"));
		reportStep("Selected Yellow Weld toggle button succesfully","pass");
		return this;
	}

	public Weld selectGreenWeld() {
		click(locateElement(Locators.XPATH,"//mat-button-toggle[@value='Thick']//button"));
		reportStep("Selected Yellow Weld toggle button succesfully","pass");
		return this;
	}

	public Weld selectDashedWeld() {
		click(locateElement(Locators.XPATH,"//mat-button-toggle[@value='Dashed']//button"));
		reportStep("Selected Dashed Weld button succesfully","pass");
		return this;
	}

	public Weld selectContinuousdWeld() {
		click(locateElement(Locators.XPATH,"//mat-button-toggle[@value='Continuous']//button"));
		reportStep("Selected Continuous Weld succesfully","pass");
		return this;
	}

	public Weld selectCreateWeld() {
		click(locateElement(Locators.XPATH,"//mat-button-toggle[@value='Create']//button"));
		reportStep("Selected create weld succesfully","pass");
		return this;
	}

	public Weld selectDeleteWeld() {
		click(locateElement(Locators.XPATH,"//mat-button-toggle[@value='Delete']//button"));
		reportStep("Selected Delete weld succesfully","pass");
		return this;
	}


	public Weld selectClose() {
		click(locateElement(Locators.XPATH,"(//input[@id='Lengthid']//following::span)[1]"));
		reportStep("Selected close weld command succesfully","pass");
		return this;
	}


	public Weld selectClearAll() {
		click(locateElement(Locators.XPATH,"(//input[@id='Lengthid']//following::span)[2]"));
		reportStep("Deleted all the weld command succesfully","pass");
		return this;
	}


	public Weld selectWeldTypeButton() {
		click(locateElement(Locators.XPATH,"(//input[@id='Lengthid']//following::span)[2]"));
		reportStep("Selected weld Type command succesfully","pass");
		return this;
	}

	public Weld getAllWeldType() {

		click(locateElement(Locators.XPATH,"//div[@id='mat-select-0-panel']//mat-option/span"));
		reportStep("Collect all the weld Type succesfully","pass");
		return this;
	}




}
