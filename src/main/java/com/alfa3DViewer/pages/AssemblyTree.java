package com.alfa3DViewer.pages;

import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.openqa.selenium.WebElement;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class AssemblyTree  extends ProjectSpecificMethods{

	public AssemblyTree selectAssemblyTree() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[1]"));
		reportStep("Assembly tree is selected successfully","pass");
		return this;
	}

	public AssemblyTree selectParentComponent() {
		click(locateElement(Locators.XPATH,"(//mat-tree[@class='mat-tree cdk-tree']//button)[2]"));
		reportStep("Parent Assembly is selected successfully","pass");
		return this;
	}

	public int getTotalChildCount() {
		List<WebElement> totalChildCount = locateElements(Locators.XPATH, "//mat-tree-node");
		reportStep("Total Child Count is reflected successfully " + totalChildCount.size(),"pass");
		return totalChildCount.size()>1 ? totalChildCount.size()-1:totalChildCount.size();
	}


	public List<String> getTotalChildName() {

		List<@NonNull String> collect = locateElements(Locators.XPATH, "//button[@class='mat-focus-indicator mat-button mat-button-base']//span")
				.stream()
				.map(WebElement::getText)
				.collect(Collectors.toList());
		reportStep("Collected  all the Children Name is selected successfully","pass");
		return collect;
	}


	public int getTotalSubParentCount() {
		List<WebElement> totalChildCount = locateElements(Locators.XPATH, "//mat-tree-node[@role='group']");
		reportStep("Sub-parent count collected successfully" + totalChildCount.size(),"pass");
		return totalChildCount.size()>1 ? totalChildCount.size()-1:totalChildCount.size();
	}


	public List<String> getSubParentName() {

		List<@NonNull String> collect = locateElements(Locators.XPATH, "//mat-tree-node[@role='group']//button[@class='mat-focus-indicator mat-button mat-button-base']//span")
				.stream()
				.map(WebElement::getText)
				.collect(Collectors.toList());
		reportStep("Collected  all the sub parent name is selected successfully","pass");
		return collect;
	}


	public int getTotalSingleChildCount() {
		List<WebElement> getTotalSingleChildCount = locateElements(Locators.XPATH, "//mat-tree-node[@role='treeitem']");
		reportStep("Total Single Child Count is reflected successfully " + getTotalSingleChildCount.size(),"pass");
		return getTotalSingleChildCount.size();
	}


	public List<String> getTotalSingleChildName() {

		List<@NonNull String> collect = locateElements(Locators.XPATH, "//mat-tree-node[@role='treeitem']//button[@class='mat-focus-indicator mat-button mat-button-base']//span")
				.stream()
				.map(WebElement::getText)
				.collect(Collectors.toList());
		reportStep("Collected  all the only child name successfully","pass");
		return collect;
	}


	public AssemblyTree collapseParentAssembly() {
		click(Locators.XPATH,"(//button[@id='dropButton']/span)[1]");
		reportStep("Collapse the Parent assembly","pass");
		return this;
	}

	public AssemblyTree collapseSubParentAssembly(int ele) {
		click(Locators.XPATH,"(//button[@id='dropButton']/span)["+ele+"]");
		reportStep("Collapse the selected Sub Parent assembly","pass");
		return this;
	}


	public void collapseSubParentAssembly(List<Integer> ele) {
		for(Integer e : ele) {
			collapseSubParentAssembly(e);
		}
		reportStep("Collapse the selected Sub Parent assembly","pass");
	}



	public AssemblyTree parentAssemblyCheckBox() {
		click(Locators.ID,"mat-checkbox-55-input");
		reportStep("Select Parent assembly checkbox succesfully","pass");
		return this;
	}

	public AssemblyTree subParentAssemblyCheckBox(int ele) {
		click(Locators.XPATH,"(//mat-tree-node[@role='group']//mat-checkbox)["+ele+"]");
		reportStep("Successfully selected the specific sub-parent assembly checkbox","pass");
		return this;
	}

	public void subParentAssemblyCheckBox(List<Integer> ele) {
		for(Integer e : ele) {
			subParentAssemblyCheckBox(e);
		}
		reportStep("Successfully selected sub-parent assembly checkbox","pass");
	}


	public AssemblyTree singleChildCheckBox(int ele) {
		click(Locators.XPATH,"(//mat-tree-node[@role='treeitem']//mat-checkbox)["+ele+"]");
		reportStep("Successfully selected the specific single child assembly checkbox","pass");

		return this;
	}

	public void singleChildCheckBox(List<Integer> ele) {
		for(Integer e : ele) {
			subParentAssemblyCheckBox(e);
		}
		reportStep("Successfully selected single child assembly checkbox","pass");
	}

	public AssemblyTree showAll() {
		click(Locators.XPATH,"(//app-context-menu/div)[1]");
		reportStep("Successfully selected show all","pass");
		return this;
	}

	public AssemblyTree hideOthers() {
		click(Locators.XPATH,"(//app-context-menu/div)[2]");
		reportStep("Successfully selected hide Others","pass");
		return this;
	}

	public AssemblyTree inverseVisibilty() {
		click(Locators.XPATH,"(//app-context-menu/div)[3]");
		reportStep("Successfully selected inverse Visibilty ","pass");
		return this;
	}

}






