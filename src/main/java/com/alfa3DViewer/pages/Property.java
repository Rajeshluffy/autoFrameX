package com.alfa3DViewer.pages;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.openqa.selenium.WebElement;

import com.framework.selenium.api.base.SeleniumBase;
import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Property  extends ProjectSpecificMethods{


	public Property selectProperty() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[1]"));
		reportStep("Selected Property Pannel command succesfully","pass");
		return this;
	}

	public List<WebElement> collectTheProperty() {

		List<WebElement> propertyPannelElement = locateElements(
				Locators.XPATH,
				"//tr[contains(@class, 'mat-row')]"
				);
		Map<Object, Object> propertyMap = propertyPannelElement.stream()
				.filter(row ->   ((SeleniumBase) row).locateElements(Locators.XPATH,"./td").size() >= 2)
				.collect(Collectors.toMap(
						row ->  ((SeleniumBase) row).locateElement(Locators.XPATH,"./td[1]").getText().trim(),
						row ->  ((SeleniumBase) row).locateElement(Locators.XPATH,"./td[2]").getText().trim(),
						(existing, replacement) -> existing
						));

		reportStep("Collected All the Property information succesfully","pass");
		return propertyPannelElement;
	}




}
