package com.framework.selenium.api.actions;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.utils.ExtentReportManager;
import com.framework.utils.Reporter;

/**
 * Screenshot capture — extracted from {@code SeleniumBase} as part of the
 * TD-07 composition refactor. Reads {@link ExtentReportManager#folderName}
 * (public static, resolved at call time) rather than caching it, since the
 * report folder is only known once {@code ExtentReportManager.initReportInfrastructure} runs.
 */
public class ScreenshotActions {

	private final Supplier<RemoteWebDriver> driverSupplier;
	private final Reporter reporter;

	public ScreenshotActions(Supplier<RemoteWebDriver> driverSupplier, Reporter reporter) {
		this.driverSupplier = driverSupplier;
		this.reporter = reporter;
	}

	private RemoteWebDriver driver() {
		return driverSupplier.get();
	}

	public long takeSnap() {
		long number = (long) Math.floor(Math.random() * 900000000L) + 10000000L;
		try {
			File screenshot = driver().getScreenshotAs(OutputType.FILE);
			File destination = new File("./" + ExtentReportManager.folderName + "/images/" + number + ".jpg");
			FileUtils.copyFile(screenshot, destination);
		} catch (WebDriverException e) {
			reporter.reportStep("Screenshot failed - browser closed: " + e.getMessage(), "warning", false);
		} catch (IOException e) {
			reporter.reportStep("Screenshot save failed: " + e.getMessage(), "warning", false);
		}
		return number;
	}

	public long takeSnap(String name) {
		long number = (long) Math.floor(Math.random() * 900000000L) + 10000000L;
		try {
			File screenshot = driver().getScreenshotAs(OutputType.FILE);
			File destination = new File("./" + ExtentReportManager.folderName + "/images/" + name + "_" + number + ".jpg");
			FileUtils.copyFile(screenshot, destination);
		} catch (WebDriverException e) {
			reporter.reportStep("Screenshot failed: " + e.getMessage(), "warning", false);
		} catch (IOException e) {
			reporter.reportStep("Screenshot save failed: " + e.getMessage(), "warning", false);
		}
		return number;
	}

	public void screenShotByELement(String name) {
		try {
			WebElement canvas = driver().findElement(By.cssSelector("canvas"));
			File sourceFile = canvas.getScreenshotAs(OutputType.FILE);
			File destination = new File("./Temp/" + name + ".jpg");
			FileUtils.copyFile(sourceFile, destination);
		} catch (WebDriverException e) {
			reporter.reportStep("Screenshot failed: " + e.getMessage(), "warning", false);
		} catch (IOException e) {
			reporter.reportStep("Screenshot save failed: " + e.getMessage(), "warning", false);
		}
	}
}
