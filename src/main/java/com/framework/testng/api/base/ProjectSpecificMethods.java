package com.framework.testng.api.base;

import java.io.IOException;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;

import com.framework.selenium.api.base.SeleniumBase;
import com.framework.utils.DataLibrary;

import design.patterns.factory.browser.BrowserFactory;
import design.patterns.factory.browser.BrowserType;
import design.patterns.factory.browser.WebDriverFactoryInterface;
import design.patterns.object.pool.WebDriverPoolFactory;

public class ProjectSpecificMethods extends SeleniumBase {

	@DataProvider(name = "fetchData", indices = 0)
	public Object[][] fetchData() throws IOException {
		return DataLibrary.readExcelData(excelFileName);
	}	


	@BeforeMethod
	public void preCondition() {
		//public void startApp(String browser, boolean headless, String url);


		startApp(pool,BrowserType.CHROME, false, "http://leaftaps.com/opentaps/control/main");
		setNode();
	}

	@AfterMethod
	public void postCondition() {
		releaseDriver(driver);

	}
	@AfterClass
	public void browserClose() {
		close();

	}




}
