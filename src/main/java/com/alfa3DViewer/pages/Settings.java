package com.alfa3DViewer.pages;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.framework.selenium.api.design.Locators;
import com.framework.testng.api.base.ProjectSpecificMethods;

public class Settings  extends ProjectSpecificMethods{


	public Settings selectSetting() {
		click(locateElement(Locators.XPATH,"//div[@class='Left']//button[13]"));
		reportStep("Selected settings command succesfully","pass");
		return this;
	}

	
	public String getCustomSliderValue(String min, String max, String step) {
	    try {
	        int minVal = Integer.parseInt(min);
	        int maxVal = Integer.parseInt(max);
	        int stepVal = Integer.parseInt(step);

	        // Prevent infinite loops or invalid ranges
	        if (stepVal <= 0 || minVal > maxVal) {
	            return String.valueOf(minVal); 
	        }

	        List<String> slideValues = new ArrayList<>();
	        
	        // Populate the list using the step
	        for (int current = minVal; current <= maxVal; current += stepVal) {
	            slideValues.add(String.valueOf(current));
	        }

	        // Pick a random value from the generated list
	        Random random = new Random();
	        return slideValues.get(random.nextInt(slideValues.size()));

	    } catch (NumberFormatException e) {
	        // Handle cases where input strings are not valid integers
	        System.err.println("Invalid input: " + e.getMessage());
	        return min; 
	    }
	}


	public void rendering() {
		String min = getAttribute(locateElement(Locators.XPATH,"(//mat-dialog-container[@id='mat-dialog-1']//mat-slider)[1]"),"min");
		String max = getAttribute(locateElement(Locators.XPATH,"(//mat-dialog-container[@id='mat-dialog-1']//mat-slider)[1]"),"max");
		String step = getAttribute(locateElement(Locators.XPATH,"(//mat-dialog-container[@id='mat-dialog-1']//mat-slider)[1]"),"step");

		setSliderValueJS(locateElement(Locators.XPATH,"(//mat-dialog-container[@id='mat-dialog-1']//mat-slider)[1]"),"1.4");

	}

	public void image() {
		setSliderValueJS(locateElement(Locators.XPATH,"(//mat-dialog-container[@id='mat-dialog-1']//mat-slider)[2]"),"1.4");
	}


	public void dimensionalAccuracy() {
		setSliderValueJS(locateElement(Locators.XPATH,"(//mat-dialog-container[@id='mat-dialog-1']//mat-slider)[3]"),"1.4");

	}


	public boolean isquickPreview() {
		String attribute = getAttribute(locateElement(Locators.XPATH,"//input[@id='mat-slide-toggle-1-input']"),"aria-checked");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void quickPreview() {
		click(locateElement(Locators.XPATH,"//input[@id='mat-slide-toggle-7-input']"));
	}


	public boolean islighterData() {
		String attribute = getAttribute(locateElement(Locators.XPATH,"//input[@id='mat-slide-toggle-1-input']"),"aria-checked");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void lighterData() {

		click(locateElement(Locators.XPATH,"//input[@id='mat-slide-toggle-1-input']"));

	}

	public void frontface() {
		click(locateElement(Locators.XPATH,"//input[@id='Frontid']"));
	}

	public void thickness() {
		click(locateElement(Locators.XPATH,"//input[@id='Thicknessid']"));
	}

	public void backFace() {
		click(locateElement(Locators.XPATH,"//input[@id='Backid']"));
	}

	public void bendFace() {
		click(locateElement(Locators.XPATH,"//input[@id='Bendid']"));
	}


	public void backgroundFirst() {
		click(locateElement(Locators.XPATH,"//input[@id='firstColorid']"));
	}

	public void backgroundSecond() {

		click(locateElement(Locators.XPATH,"//input[@id='secondColorid']"));

	}


	public void selectalfaCAD() {
		click(locateElement(Locators.XPATH,"///mat-button-toggle[@value='alfaCAD']"));
	}

	public void selectSheetMetal() {
		click(locateElement(Locators.XPATH,"///mat-button-toggle[@value='Sheetmetal']"));
	}

	public void selectRandom() {
		click(locateElement(Locators.XPATH,"///mat-button-toggle[@value='Random']"));
	}

	public void dropDown() {

		click(locateElement(Locators.XPATH,"//mat-select[@id='mat-select-0']"));

	}

	public boolean isDimension() {
		String attribute = getAttribute(locateElement(Locators.ID,"mat-option-0"),"aria-selected");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void selectDimension() {
		click(locateElement(Locators.XPATH,"(//mat-option[@id='mat-option-0']/mat-pseudo-checkbox"));

	}


	public boolean isComment() {
		String attribute = getAttribute(locateElement(Locators.ID,"mat-option-1"),"aria-selected");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void selectComment() {
		click(locateElement(Locators.XPATH,"(//mat-option[@id='mat-option-1']/mat-pseudo-checkbox"));

	}

	public boolean isWeld() {
		String attribute = getAttribute(locateElement(Locators.ID,"mat-option-2"),"aria-selected");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void selectWeld() {
		click(locateElement(Locators.XPATH,"(//mat-option[@id='mat-option-2']/mat-pseudo-checkbox"));

	}

	public boolean isLasermarking() {
		String attribute = getAttribute(locateElement(Locators.ID,"mat-option-3"),"aria-selected");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void selectlaserMarking() {
		click(locateElement(Locators.XPATH,"(//mat-option[@id='mat-option-3']/mat-pseudo-checkbox"));

	}

	public boolean isMask() {
		String attribute = getAttribute(locateElement(Locators.ID,"mat-option-4"),"aria-selected");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void selectMask() {
		click(locateElement(Locators.XPATH,"(//mat-option[@id='mat-option-4']/mat-pseudo-checkbox"));
	}

	public boolean isTempWeld() {
		String attribute = getAttribute(locateElement(Locators.ID,"mat-option-5"),"aria-selected");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void selectTempWeld() {
		click(locateElement(Locators.XPATH,"(//mat-option[@id='mat-option-5']/mat-pseudo-checkbox"));
	}

	public boolean isForming() {
		String attribute = getAttribute(locateElement(Locators.ID,"mat-option-6"),"aria-selected");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void selectForming() {
		click(locateElement(Locators.XPATH,"(//mat-option[@id='mat-option-6']/mat-pseudo-checkbox"));
	}


	public void closeSetting() {
		click(locateElement(Locators.XPATH,"(//div[@class='mat-dialog-actions']//button)[1]"));
	}


	public void refreshSetting() {
		click(locateElement(Locators.XPATH,"(//div[@class='mat-dialog-actions']//button)[2]"));
	}



	public boolean isSpotlight() {
		String attribute = getAttribute(locateElement(Locators.XPATH,"//input[@id='mat-slide-toggle-3-input']"),"aria-checked");
		if(attribute.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	public void spotlight() {

		click(locateElement(Locators.XPATH,"//input[@id='mat-slide-toggle-3-input']"));

	}

























}
