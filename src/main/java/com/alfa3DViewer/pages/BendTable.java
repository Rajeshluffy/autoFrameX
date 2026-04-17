package com.alfa3DViewer.pages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.WebElement;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class BendTable  extends ProjectSpecificMethods{


	public BendTable selectBendTable() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[7]"));
		reportStep("Selected BendTable command successfully","pass");
		return this;
	}
	
	
	public BendTable deSelectBendTable() {
		click(locateElement(Locators.XPATH,"//div[@class='Right']//button[7]"));
		reportStep("deselected the bendtable command","pass");
		return this;
	}


	public boolean isBendTableAttach() {
		return isElementVisuallyVisible(locateElement(Locators.ID,"bendTableDiv"));
	}

	public boolean isBendTableDetach() {
		return isElementVisuallyVisible(locateElement(Locators.ID,"floatBendTableDiv"));
	}		 

	public List<Map<String, String>> getBendTableData() {
		List<Map<String, String>> tableData = new ArrayList<>();
		List<WebElement> rows = null;
		if (isBendTableDetach()) {
			rows =  locateElements(Locators.XPATH, "//div[@id='floatBendTableDiv']//tbody/tr");
		}else if (isBendTableAttach()) {
			rows =  locateElements(Locators.XPATH, "//div[@id='bendTableInnerDiv']//tbody/tr");
		} 


		for (WebElement row : rows) {
			Map<String, String> rowData = new LinkedHashMap<>();
			
			List<WebElement> cells =   locateElements(Locators.TAGNAME, "td");
			rowData.put("No", cells.get(0).getText().trim());
			rowData.put("BendAngle", cells.get(1).getText().trim());
			rowData.put("VWidth", cells.get(2).getText().trim());
			rowData.put("IR", cells.get(3).getText().trim());
			rowData.put("OutDim1", cells.get(4).getText().trim());
			rowData.put("OutDim2", cells.get(5).getText().trim());
			tableData.add(rowData);
		}
		return tableData;
	}

	public BendTable detach() {
		click(locateElement(Locators.XPATH,"(//div[@id='bendTableDiv']//button)[1]"));
		return this;
	}

	public BendTable attach() {
		click(locateElement(Locators.XPATH,"(//div[@id='floatBendTableDiv']//button)[1]"));
		return this;
	}


	public BendTable bendTableClose() {
		if (isBendTableDetach()) {
			click(locateElement(Locators.XPATH,"(//mat-icon[text()='close'])[2]"));
		}else if (isBendTableAttach()) {
			click(locateElement(Locators.XPATH,"(//mat-icon[text()='close'])[1]"));
		} 
		return this;
	}

	public int getTotalBendInformation() {
		int totalBend = 0;
		if (isBendTableDetach()) {
			totalBend = locateElements(Locators.XPATH, "//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-position')]").size();
		}else if (isBendTableAttach()) {
			totalBend = 	locateElements(Locators.XPATH,"//div[@id='bendTableDiv']//tr/td[contains(@class, 'mat-column-position')]").size();
		}
		return totalBend; 
	}


	/*
	 * //div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-position')]
//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-bendAngle')]
//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-vWidth')]
//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-ir')]
//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-outsideFlange1')]
//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-outsideFlange2')]
	 */


	public void selectIndividualBend(int bendNo) {
		if (isBendTableDetach()) {
			WebElement bendElement =locateElement(Locators.XPATH,"(//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-bendAngle')])["+bendNo+"]");
			scroll(bendElement);
			click(bendElement);
		}else if (isBendTableAttach()) {
			WebElement bendElement =locateElement(Locators.XPATH,"(//div[@id='bendTableDiv']//tr/td[contains(@class, 'mat-column-bendAngle')])["+bendNo+"]");
			scroll(bendElement);
			click(bendElement);
		} 
	}

	public void selectOutsideFlange1(int bendNo) {
		if (isBendTableDetach()) {
			WebElement bendElement = locateElement(Locators.XPATH,"(//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-outsideFlange1')]/button)["+bendNo+"]");
			scroll(bendElement);
			click(bendElement);
		}else if (isBendTableAttach()) {
			WebElement bendElement = locateElement(Locators.XPATH,"(//div[@id='bendTableDiv']//tr/td[contains(@class, 'mat-column-outsideFlange1')]/button)["+bendNo+"]");
			scroll(bendElement);
			click(bendElement);
		} 
	}

	public void selectOutsideFlange2(int bendNo) {
		if (isBendTableDetach()) {
			WebElement bendElement = locateElement(Locators.XPATH,"(//div[@id='floatBendTableDiv']//tr/td[contains(@class, 'mat-column-outsideFlange2')]/button)["+bendNo+"]");
			scroll(bendElement);
			click(bendElement);
		}else if (isBendTableAttach()) {
			WebElement bendElement  =locateElement(Locators.XPATH,"(//div[@id='bendTableDiv']//tr/td[contains(@class, 'mat-column-outsideFlange2')]/button)["+bendNo+"]");
			scroll(bendElement);
			click(bendElement);
		} 
	}

	public void enterTheOutsideFlange1(String outsideFlange) {
		clearAndType(locateElement(Locators.ID,"OutsideDimension1InputBox"), outsideFlange);
	}

	public void selectUpdate() {
		click(locateElement(Locators.XPATH,"//input[@id='OutsideDimension1InputBox']//following::button"));
	}


}
