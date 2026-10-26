package com.framework.selenium.api.actions;

import com.framework.utils.StepReporter;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

import org.openqa.selenium.WebElement;


/**
 * File-upload operations — extracted from {@code SeleniumBase} as part of the
 * TD-07 composition refactor. Depends on {@link ClickActions} for the
 * OS-dialog fallback's trigger click ({@code hoverAndClick}/{@code clickWithJs}).
 */
public class FileUploadActions {

	/** Serializes the OS-dialog file-upload fallback — see {@link #fileUploadViaOsDialog}. */
	private static final Object FILE_UPLOAD_OS_DIALOG_LOCK = new Object();

	private final StepReporter reporter;
	private final ElementClicker clickActions;
	private final java.util.function.Predicate<WebElement> remoteSession;

	public FileUploadActions(StepReporter reporter, ElementClicker clickActions) {
		this(reporter, clickActions, FileUploadActions::isRemoteSession);
	}

	/** For tests: decide for yourself whether the element belongs to a remote (Grid) browser. */
	FileUploadActions(StepReporter reporter, ElementClicker clickActions, java.util.function.Predicate<WebElement> remoteSession) {
		this.reporter = reporter;
		this.clickActions = clickActions;
		this.remoteSession = remoteSession;
	}

	/**
	 * Whether the element belongs to a plain {@link org.openqa.selenium.remote.RemoteWebDriver}, which is what a
	 * Selenium Grid session is (a local Chrome or Firefox is a subclass of it).
	 */
	static boolean isRemoteSession(WebElement ele) {
		if (!(ele instanceof org.openqa.selenium.WrapsDriver)) {
			return false;
		}
		org.openqa.selenium.WebDriver driver = ((org.openqa.selenium.WrapsDriver) ele).getWrappedDriver();
		return driver != null && driver.getClass().equals(org.openqa.selenium.remote.RemoteWebDriver.class);
	}

	/**
	 * Last resort when {@code sendKeys} was refused. On a local browser this drives the OS file dialog; on a
	 * remote browser it cannot work (the dialog is on the node's machine, the keystrokes would go to whatever
	 * window has focus on the test runner), so the step fails with the reason instead.
	 */
	private void fallbackToOsDialog(WebElement ele, Runnable triggerDialog, String filePath) {
		if (remoteSession.test(ele)) {
			reporter.reportStep("File upload failed: sendKeys was refused and the OS-dialog fallback cannot work on a "
					+ "remote (Grid) browser. Check that the element is a real <input type=\"file\"> and that the file "
					+ "exists on this machine: " + filePath, "fail", true);
			return;
		}
		fileUploadViaOsDialog(triggerDialog, filePath);
	}

	public void fileUpload(WebElement ele, String filePath) {
		// Prefer sendKeys directly on the element — works for a real
		// <input type="file">, needs no OS dialog/clipboard, and is safe under
		// parallel execution and against a remote Selenium Grid node (Robot
		// below only controls the local machine, not a remote browser host).
		// Only elements that aren't a genuine file input (custom-styled upload
		// widgets that pop a native OS dialog via JS) fall through to Robot.
		try {
			ele.sendKeys(filePath);
			reporter.reportStep("File uploaded via sendKeys: " + filePath, "pass", false);
			return;
		} catch (Exception e) {
			reporter.reportStep("sendKeys upload not supported on this element, falling back to OS dialog: "
					+ e.getMessage(), "info", false);
		}

		fallbackToOsDialog(ele, () -> clickActions.hoverAndClick(ele), filePath);
	}

	public void fileUploadWithJs(WebElement ele, String filePath) {
		// See fileUpload() above — same sendKeys-first, OS-dialog-fallback design.
		try {
			ele.sendKeys(filePath);
			reporter.reportStep("File uploaded via sendKeys: " + filePath, "pass", false);
			return;
		} catch (Exception e) {
			reporter.reportStep("sendKeys upload not supported on this element, falling back to OS dialog: "
					+ e.getMessage(), "info", false);
		}

		fallbackToOsDialog(ele, () -> clickActions.clickWithJs(ele), filePath);
	}

	/**
	 * OS-level fallback for file inputs that require driving a native file
	 * dialog (custom-styled upload widgets, not a genuine {@code <input
	 * type="file">}). The system clipboard and OS keyboard focus are
	 * process-wide resources shared by every thread on the machine, so this is
	 * {@code synchronized} on a class-wide lock — concurrent parallel uploads
	 * queue up and run one at a time instead of racing (one thread's paste
	 * landing in a different thread's dialog). Only works against a local
	 * display; does not work against a remote Selenium Grid node.
	 *
	 * @param triggerDialog opens the native file dialog (e.g. a click on the upload control)
	 * @param filePath      absolute path to paste into the dialog
	 */
	private void fileUploadViaOsDialog(Runnable triggerDialog, String filePath) {
		synchronized (FILE_UPLOAD_OS_DIALOG_LOCK) {
			try {
				triggerDialog.run();
				ElementSupport.pause(reporter, 1000);

				StringSelection stringSelection = new StringSelection(filePath);
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);

				Robot robot = new Robot();
				robot.keyPress(KeyEvent.VK_CONTROL);
				robot.keyPress(KeyEvent.VK_V);
				robot.keyRelease(KeyEvent.VK_V);
				robot.keyRelease(KeyEvent.VK_CONTROL);

				ElementSupport.pause(reporter, 1000);

				robot.keyPress(KeyEvent.VK_ENTER);
				robot.keyRelease(KeyEvent.VK_ENTER);

				reporter.reportStep("File uploaded via OS dialog: " + filePath, "pass", false);
			} catch (Exception e) {
				reporter.reportStep("File upload failed: " + e.getMessage(), "fail", true);
			}
		}
	}
}
