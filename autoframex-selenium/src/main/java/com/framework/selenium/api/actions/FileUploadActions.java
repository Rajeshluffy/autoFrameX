package com.framework.selenium.api.actions;

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

import org.openqa.selenium.WebElement;

import com.framework.utils.Reporter;

/**
 * File-upload operations — extracted from {@code SeleniumBase} as part of the
 * TD-07 composition refactor. Depends on {@link ClickActions} for the
 * OS-dialog fallback's trigger click ({@code hoverAndClick}/{@code clickWithJs}).
 */
public class FileUploadActions {

	/** Serializes the OS-dialog file-upload fallback — see {@link #fileUploadViaOsDialog}. */
	private static final Object FILE_UPLOAD_OS_DIALOG_LOCK = new Object();

	private final Reporter reporter;
	private final ClickActions clickActions;

	public FileUploadActions(Reporter reporter, ClickActions clickActions) {
		this.reporter = reporter;
		this.clickActions = clickActions;
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

		fileUploadViaOsDialog(() -> clickActions.hoverAndClick(ele), filePath);
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

		fileUploadViaOsDialog(() -> clickActions.clickWithJs(ele), filePath);
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
