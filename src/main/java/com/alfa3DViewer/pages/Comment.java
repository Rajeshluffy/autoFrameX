package com.alfa3DViewer.pages;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Comment extends ProjectSpecificMethods {

	public Comment selectComment() {
		click(locateElement(Locators.XPATH, "//div[@class='Left']//button[11]"));
		reportStep("Comment Command is selected successfully", "pass");
		return this;
	}

	public Comment deSelectComment() {
		click(locateElement(Locators.XPATH, "//div[@class='Left']//button[10]"));
		reportStep("De-Selected Command is selected successfully", "pass");
		return this;
	}

	public Comment selectedText() {
		click(locateElement(Locators.XPATH, "(//div[@id='mat-menu-panel-0']//button)[1]"));
		reportStep("Comment-Text option is selected successfully", "pass");
		return this;
	}

	public Comment selectedCommentTextHeader() {
		clearAndType(locateElement(Locators.XPATH, "(//div[@class='cdk-drag annotationContent']//input)[1s]"),
				"Simple");
		reportStep("Comment-Text header information is entered selected successfully", "pass");
		return this;
	}

	public Comment selectedCommentTextBody() {
		clearAndType(locateElement(Locators.XPATH, "(//div[@class='cdk-drag annotationContent']//textarea)"), "Simple");
		reportStep("Comment-Text body information is entered selected successfully", "pass");
		return this;
	}

	public Comment selectedCommentDelete() {
		click(locateElement(Locators.XPATH, "(//div[@id='mat-menu-panel-0']//button)[1]"));
		reportStep("Comment-Text option is selected successfully", "pass");
		return this;
	}

	public Comment selectedCommentDrag() {

		// div[@class='cdk-drag annotationContent']//div[@class='cdk-drag-handle']

		return this;
	}

	public Comment selectedAudio() {
		click(locateElement(Locators.XPATH, "(//div[@id='mat-menu-panel-0']//button)[2]"));
		reportStep("Comment-Audio option is selected successfully", "pass");
		return this;
	}

}
