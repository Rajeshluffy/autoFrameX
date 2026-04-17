package com.alfa3DViewer.pages;



import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Graphic  extends ProjectSpecificMethods{


	public Graphic selectWireframe() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[2]"));
		reportStep("Selected Wireframe command succesfully","pass");
	
		return this;
	}

	public Graphic selectShaded() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[2]"));
		reportStep("Selected shaded command succesfully","pass");
		return this;
	}

	public Graphic isShaded() {
		verifyExactAttribute(locateElement(Locators.XPATH,"//div[@class='Right']//button[2]//img"), "alt", "Shaded");
		reportStep("Shaded is enabled","pass");
		return this;
	}

	public Graphic isWireframe() {
		verifyExactAttribute(locateElement(Locators.XPATH,"//div[@class='Right']//button[2]//img"), "alt", "Wireframe");
		reportStep("Wireframe is enabled","pass");
		return this;
	}

	




}
