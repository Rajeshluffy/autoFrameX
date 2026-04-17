package com.alfa3DViewer.pages;



import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Explode  extends ProjectSpecificMethods{


	public Explode selectExplode() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[3]"));
		reportStep("Selected explode command succesfully","pass");
		return this;
	}

	public Explode selectAsselmble() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[3]"));
		reportStep("Selected assemble command succesfully","pass");
		return this;
	}

	public Explode isExplode() {
		boolean explode = verifyExactAttribute(locateElement(Locators.XPATH,"//div[@class='Right']//button[3]//img"), "alt", "Explode");
		reportStep("explode is enabled","pass");
		return this;
	}

	public Explode isAssemble() {
		verifyExactAttribute(locateElement(Locators.XPATH,"//div[@class='Right']//button[3]//img"), "alt", "Implode");
		reportStep("Assemble is enabled","pass");
		return this;
	}

	




}
