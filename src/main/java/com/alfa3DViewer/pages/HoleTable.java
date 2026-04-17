package com.alfa3DViewer.pages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.WebElement;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class HoleTable extends ProjectSpecificMethods{

	
	public HoleTable selectHoleTable() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[8]"));
		reportStep("Successfully selected the HoleTable command","pass");
		return this;
	}
	
	public HoleTable deSelectHoleTable() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[8]"));
		reportStep("Successfully de-selected the HoleTable command","pass");
		return this;
	}
	
	
	public boolean isBendTableAttach() {
		return isElementVisuallyVisible(locateElement(Locators.ID,"holeTableDiv"));
	}

	public boolean isBendTableDetach() {
		return isElementVisuallyVisible(locateElement(Locators.ID,"FloatHoleTableDiv"));
	}	
	
	
	public List<Map<String, String>> getBendTableData() {
		List<Map<String, String>> tableData = new ArrayList<>();
		List<WebElement> rows = null;
		if (isBendTableDetach()) {
			rows =  locateElements(Locators.XPATH, "//div[@id='FloatHoleTableDiv']//tbody/tr");
		}else if (isBendTableAttach()) {
			rows =  locateElements(Locators.XPATH, "//div[@id='holeTableDiv']//tbody/tr");
		} 


		for (WebElement row : rows) {
			Map<String, String> rowData = new LinkedHashMap<>();
			List<WebElement> cells =  locateElements(Locators.TAGNAME, "td");      
			rowData.put("Type", cells.get(0).getText().trim());
			rowData.put("D/X", cells.get(1).getText().trim());
			rowData.put("Y", cells.get(2).getText().trim());
			rowData.put("R", cells.get(3).getText().trim());
			rowData.put("Quantity", cells.get(4).getText().trim());
			tableData.add(rowData);
		}
		return tableData;
	}
	
	
	
	public HoleTable detach() {
		click(locateElement(Locators.XPATH,"(//div[@id='holeTableDiv']//button)[2]"));
		return this;
	}
	
	public HoleTable attach() {
		click(locateElement(Locators.XPATH,"(//div[@id='FloatHoleTableDiv']//button)[2]"));
		return this;
	}
	
	public int getTotalHoleInformation() {
		int totalHole = 0;
		if (isBendTableDetach()) {
			totalHole = locateElements(Locators.XPATH, "//div[@id='holeTableDiv']//tr").size();
		}else if (isBendTableAttach()) {
			totalHole = locateElements(Locators.XPATH,"//div[@id='FloatHoleTableDiv']//tr").size();
		}
		return totalHole; 
	}
	
	
	public void selectIndividualHole(int bendNo) {
		if (isBendTableDetach()) {
			WebElement holeElement =locateElement(Locators.XPATH,"(//div[@id='holeTableDiv']//tr/td[contains(@class, 'mat-column-type')])["+bendNo+"]");
			scroll(holeElement);
			click(holeElement);
		}else if (isBendTableAttach()) {
			WebElement holeElement =locateElement(Locators.XPATH,"(//div[@id='FloatHoleTableDiv']//tr/td[contains(@class, 'mat-column-type')])["+bendNo+"]");
			scroll(holeElement);
			click(holeElement);
		} 
	}
	
}
