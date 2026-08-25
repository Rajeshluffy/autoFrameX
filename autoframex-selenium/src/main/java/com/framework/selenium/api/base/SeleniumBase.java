package com.framework.selenium.api.base;

import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.openqa.selenium.Alert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestContext;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeTest;

import com.framework.config.data.ConfigManager;
import com.framework.selenium.api.actions.AlertActions;
import com.framework.selenium.api.actions.ClickActions;
import com.framework.selenium.api.actions.ElementInspectionActions;
import com.framework.selenium.api.actions.FileUploadActions;
import com.framework.selenium.api.actions.JsActions;
import com.framework.selenium.api.actions.LocatorActions;
import com.framework.selenium.api.actions.NavigationActions;
import com.framework.selenium.api.actions.ScreenshotActions;
import com.framework.selenium.api.actions.TypeActions;
import com.framework.selenium.api.actions.WaitActions;
import com.framework.selenium.api.actions.WindowFrameActions;
import com.framework.selenium.api.design.Browser;
import com.framework.selenium.api.design.Element;
import com.framework.selenium.api.design.Locators;
import com.framework.utils.Reporter;

import design.patterns.object.pool.DriverPoolManager;

/**
 * <b>Class Name:</b> SeleniumBase <br>
 * <b>Purpose:</b> Implements the Browser and Element interfaces to provide a
 * robust, thread-safe base for Selenium-based automation. <br>
 * <b>Responsibilities:</b> Thin delegating facade over a set of focused,
 * single-responsibility action classes (see {@code com.framework.selenium.api.actions})
 * — every method here keeps its original signature and behavior for backward
 * compatibility with existing subclasses ({@code BasePage},
 * {@code ProjectSpecificMethods}, {@code CucumberProjectBase}, and any
 * external consumer), while the actual logic lives in the composed action
 * objects below (TD-07 composition refactor).
 *
 * @author Framework Architect
 * @version 3.2 - Decomposed into composable action classes (TD-07)
 */
public class SeleniumBase extends Reporter implements Browser, Element {

	/**
	 * Retained for backward compatibility with any external subclass that
	 * reads this field directly after calling a pointer-interaction method
	 * ({@link #moveToElement}, {@link #dragAndDrop}, {@link #contextClick},
	 * {@link #doubleClick}, {@link #hoverAndClick}) — it carries no state
	 * across calls internally; each of those methods reassigns it fresh.
	 */
	protected Actions act;

	private final WaitActions waitActions = new WaitActions(this::getDriver, this);
	private final LocatorActions locatorActions = new LocatorActions(this::getDriver, this);
	private final ClickActions clickActions = new ClickActions(this::getDriver, this, waitActions, locatorActions);
	private final TypeActions typeActions = new TypeActions(this, waitActions);
	private final AlertActions alertActions = new AlertActions(this::getDriver, this, waitActions);
	private final ScreenshotActions screenshotActions = new ScreenshotActions(this::getDriver, this);
	private final WindowFrameActions windowFrameActions =
			new WindowFrameActions(this::getDriver, this, waitActions, locatorActions);
	private final NavigationActions navigationActions = new NavigationActions(this::getDriver, this, waitActions);
	private final JsActions jsActions = new JsActions(this::getDriver, this);
	private final FileUploadActions fileUploadActions = new FileUploadActions(this, clickActions);
	private final ElementInspectionActions elementInspectionActions = new ElementInspectionActions(this, waitActions);

	/**
	 * Fetches the WebDriver instance for the current thread.
	 *
	 * @return RemoteWebDriver instance from the pool.
	 * @throws IllegalStateException if driver not available
	 */
	private RemoteWebDriver getDriver() {
		try {
			return getDriverManager().getDriver();
		} catch (Exception e) {
			reportStep("Driver not available: " + e.getMessage(), "fail", false);
			throw new IllegalStateException("No WebDriver available. Ensure @BeforeMethod executed.", e);
		}
	}

	/**
	 * Resolves the {@link DriverPoolManager} bound to the calling thread's
	 * context. Deliberately not cached on an instance field — under
	 * {@code parallel="methods"}/{@code "classes"}, TestNG runs multiple
	 * threads against one shared instance of the test class, and an
	 * unsynchronized "cache on first call" field lets whichever thread wins
	 * the race pin every other thread to its context, silently defeating
	 * {@link DriverPoolManager}'s per-thread multi-context resolution
	 * (framework-3.1 architecture review, finding F5). {@code getInstance()}
	 * is already a lock-free {@code ConcurrentHashMap.computeIfAbsent}, so
	 * caching bought negligible performance for a correctness risk.
	 */
	protected DriverPoolManager getDriverManager() {
		return DriverPoolManager.getInstance();
	}

	/**
	 * Binds this (suite-initiating) thread to its pool context and initializes
	 * the WebDriver pool. Runs after {@code Reporter.initFromContext} (this
	 * class extends {@code Reporter}, and TestNG runs {@code @BeforeTest}
	 * methods superclass-first) — moved here from {@code Reporter} itself so
	 * the core module never depends on the selenium module (TD-20).
	 *
	 * @param context TestNG test context (auto-injected)
	 */
	@BeforeTest(alwaysRun = true)
	public synchronized void initDriverPool(ITestContext context) {
		ConcurrentMap<String, String> suiteParams = extractSuiteParameters(context);
		String contextId = ConfigManager.resolveContextId(suiteParams);
		DriverPoolManager.bindContext(contextId);
		DriverPoolManager.getInstance().initializePool(suiteParams);
	}

	/**
	 * Shuts down every registered pool context. Runs before {@code Reporter.endReport}
	 * (TestNG runs {@code @AfterSuite} methods subclass-first, reverse of
	 * {@code @BeforeTest} order) — moved here from {@code Reporter} itself, same
	 * reason as {@link #initDriverPool}.
	 */
	@AfterSuite(alwaysRun = true)
	public synchronized void shutdownDriverPool() {
		DriverPoolManager.shutdownAll();
	}

	// ========================================================================
	// WAIT OPERATIONS
	// ========================================================================

	public WebDriverWait getWait(int timeoutInSeconds) {
		return waitActions.getWait(timeoutInSeconds);
	}

	public <T> T waitFor(ExpectedCondition<T> condition, String errorMessage) {
		return waitActions.waitFor(condition, errorMessage);
	}

	public <T> T waitFor(ExpectedCondition<T> condition, int timeoutInSeconds, String errorMessage) {
		return waitActions.waitFor(condition, timeoutInSeconds, errorMessage);
	}

	public <T> T fluentWaitFor(Function<RemoteWebDriver, T> condition) {
		return waitActions.fluentWaitFor(condition);
	}

	public <T> T fluentWaitFor(Function<RemoteWebDriver, T> condition, int timeoutInSeconds, int pollingIntervalMs) {
		return waitActions.fluentWaitFor(condition, timeoutInSeconds, pollingIntervalMs);
	}

	@Override
	public void setImplicitWait(int seconds) {
		waitActions.setImplicitWait(seconds);
	}

	@Override
	public void resetImplicitWait() {
		waitActions.resetImplicitWait();
	}

	@Override
	public WebElement waitForClickable(WebElement ele) {
		return waitActions.waitForClickable(ele);
	}

	@Override
	public WebElement waitForVisibility(WebElement element) {
		return waitActions.waitForVisibility(element);
	}

	@Override
	public void waitForApperance(WebElement element) {
		waitActions.waitForApperance(element);
	}

	@Override
	public void waitForDisapperance(WebElement element) {
		waitActions.waitForDisapperance(element);
	}

	public void waitForPageToLoad() {
		waitActions.waitForPageToLoad();
	}

	public void waitForSpinnerDisappear() {
		waitActions.waitForSpinnerDisappear();
	}

	public void waitForPageAndApiReady() {
		waitActions.waitForPageAndApiReady();
	}

	public void waitForPageAndApiReady(int timeoutSeconds) {
		waitActions.waitForPageAndApiReady(timeoutSeconds);
	}

	public void waitApiToLoad() {
		waitActions.waitApiToLoad();
	}

	public void refresh() {
		navigationActions.refresh();
	}

	/** Minimal pause - use only when absolutely necessary. */
	public void pause(int timeoutMs) {
		waitActions.pause(timeoutMs);
	}

	// ========================================================================
	// CLICK OPERATIONS
	// ========================================================================

	@Override
	public void click(WebElement ele) {
		clickActions.click(ele);
	}

	@Override
	public void clickWithJs(WebElement ele) {
		clickActions.clickWithJs(ele);
	}

	@Override
	public void click(Locators locatorType, String value) {
		clickActions.click(locatorType, value);
	}

	@Override
	public void clickWithNoSnap(WebElement ele) {
		clickActions.clickWithNoSnap(ele);
	}

	@Override
	public void contextClick(WebElement ele) {
		act = new Actions(getDriver());
		clickActions.contextClick(ele);
	}

	@Override
	public void doubleClick(WebElement ele) {
		act = new Actions(getDriver());
		clickActions.doubleClick(ele);
	}

	@Override
	public void hoverAndClick(WebElement ele) {
		act = new Actions(getDriver());
		clickActions.hoverAndClick(ele);
	}

	@Override
	public void moveToElement(WebElement ele) {
		act = new Actions(getDriver());
		clickActions.moveToElement(ele);
	}

	@Override
	public void dragAndDrop(WebElement eleSource, WebElement eleTarget) {
		act = new Actions(getDriver());
		clickActions.dragAndDrop(eleSource, eleTarget);
	}

	// ========================================================================
	// TYPE OPERATIONS
	// ========================================================================

	@Override
	public void clearAndType(WebElement ele, String data) {
		typeActions.clearAndType(ele, data);
	}

	@Override
	public void typeAndTab(WebElement ele, String data) {
		typeActions.typeAndTab(ele, data);
	}

	@Override
	public void typeAndEnter(WebElement ele, String data) {
		typeActions.typeAndEnter(ele, data);
	}

	@Override
	public void append(WebElement ele, String data) {
		typeActions.append(ele, data);
	}

	@Override
	public void clear(WebElement ele) {
		typeActions.clear(ele);
	}

	@Override
	public void type(WebElement ele, String data) {
		typeActions.type(ele, data);
	}

	// ========================================================================
	// ELEMENT INSPECTION / VERIFICATION
	// ========================================================================

	public String safeGetText(WebElement ele) {
		return elementInspectionActions.safeGetText(ele);
	}

	@Override
	public boolean verifyDisplayed(WebElement ele) {
		return elementInspectionActions.verifyDisplayed(ele);
	}

	@Override
	public boolean verifyExactText(WebElement ele, String expectedText) {
		return elementInspectionActions.verifyExactText(ele, expectedText);
	}

	@Override
	public boolean verifyPartialText(WebElement ele, String expectedText) {
		return elementInspectionActions.verifyPartialText(ele, expectedText);
	}

	@Override
	public String getAttribute(WebElement ele, String attributeValue) {
		return elementInspectionActions.getAttribute(ele, attributeValue);
	}

	@Override
	public boolean verifyExactAttribute(WebElement ele, String attribute, String value) {
		return elementInspectionActions.verifyExactAttribute(ele, attribute, value);
	}

	@Override
	public void verifyPartialAttribute(WebElement ele, String attribute, String value) {
		elementInspectionActions.verifyPartialAttribute(ele, attribute, value);
	}

	@Override
	public boolean verifyDisappeared(WebElement ele) {
		return elementInspectionActions.verifyDisappeared(ele);
	}

	@Override
	public boolean verifyEnabled(WebElement ele) {
		return elementInspectionActions.verifyEnabled(ele);
	}

	@Override
	public boolean verifySelected(WebElement ele) {
		return elementInspectionActions.verifySelected(ele);
	}

	@Override
	public String getElementText(WebElement ele) {
		return elementInspectionActions.getElementText(ele);
	}

	@Override
	public String getBackgroundColor(WebElement ele) {
		return elementInspectionActions.getBackgroundColor(ele);
	}

	@Override
	public boolean isElementVisuallyVisible(WebElement element) {
		return elementInspectionActions.isElementVisuallyVisible(element);
	}

	@Override
	public String getTypedText(WebElement ele) {
		return elementInspectionActions.getTypedText(ele);
	}

	@Override
	public void selectDropDownUsingText(WebElement ele, String value) {
		elementInspectionActions.selectDropDownUsingText(ele, value);
	}

	@Override
	public void selectDropDownUsingIndex(WebElement ele, int index) {
		elementInspectionActions.selectDropDownUsingIndex(ele, index);
	}

	@Override
	public void selectDropDownUsingValue(WebElement ele, String value) {
		elementInspectionActions.selectDropDownUsingValue(ele, value);
	}

	// ========================================================================
	// LOCATORS
	// ========================================================================

	@Override
	public WebElement locateElement(Locators locatorType, String value) {
		return locatorActions.locateElement(locatorType, value);
	}

	@Override
	public WebElement locateElement(String value) {
		return locatorActions.locateElement(value);
	}

	@Override
	public WebElement locateElement(Locators locatorType1, String value1, Locators locatorType2, String value2) {
		return locatorActions.locateElement(locatorType1, value1, locatorType2, value2);
	}

	@Override
	public List<WebElement> locateElements(Locators type, String value) {
		return locatorActions.locateElements(type, value);
	}

	// ========================================================================
	// ALERT HANDLING
	// ========================================================================

	@Override
	public void acceptAlert() {
		alertActions.acceptAlert();
	}

	@Override
	public void dismissAlert() {
		alertActions.dismissAlert();
	}

	@Override
	public void switchToAlert() {
		alertActions.switchToAlert();
	}

	@Override
	public String getAlertText() {
		return alertActions.getAlertText();
	}

	@Override
	public void typeAlert(String data) {
		alertActions.typeAlert(data);
	}

	// ========================================================================
	// WINDOW / FRAME MANAGEMENT
	// ========================================================================

	public String getWindowName() {
		return windowFrameActions.getWindowName();
	}

	public List<String> getAllWindowName() {
		return windowFrameActions.getAllWindowName();
	}

	@Override
	public void switchToWindow(int index) {
		windowFrameActions.switchToWindow(index);
	}

	@Override
	public boolean switchToWindowByTitle(String title) {
		return windowFrameActions.switchToWindowByTitle(title);
	}

	@Override
	public boolean switchToWindowByUrl(String url) {
		return windowFrameActions.switchToWindowByUrl(url);
	}

	@Override
	public void switchToFrame(int index) {
		windowFrameActions.switchToFrame(index);
	}

	@Override
	public void switchToFrame(WebElement ele) {
		windowFrameActions.switchToFrame(ele);
	}

	@Override
	public void switchToFrame(String idOrName) {
		windowFrameActions.switchToFrame(idOrName);
	}

	@Override
	public void switchToFrameUsingXPath(String xpath) {
		windowFrameActions.switchToFrameUsingXPath(xpath);
	}

	@Override
	public void defaultContent() {
		windowFrameActions.defaultContent();
	}

	@Override
	public void close() {
		windowFrameActions.close();
	}

	@Override
	public void quit() {
		windowFrameActions.quit();
	}

	// ========================================================================
	// NAVIGATION / VERIFICATION
	// ========================================================================

	@Override
	public boolean verifyUrl(String url) {
		return navigationActions.verifyUrl(url);
	}

	@Override
	public boolean verifyPartialUrl(String url) {
		return navigationActions.verifyPartialUrl(url);
	}

	@Override
	public boolean verifyTitle(String title) {
		return navigationActions.verifyTitle(title);
	}

	// ========================================================================
	// SCREENSHOTS
	// ========================================================================

	@Override
	public long takeSnap() {
		return screenshotActions.takeSnap();
	}

	@Override
	public long takeSnap(String name) {
		return screenshotActions.takeSnap(name);
	}

	public void screenShotByELement(String name) {
		screenshotActions.screenShotByELement(name);
	}

	// ========================================================================
	// JAVASCRIPT
	// ========================================================================

	@Override
	public void executeTheScript(String js, WebElement ele) {
		jsActions.executeTheScript(js, ele);
	}

	@Override
	public Object executeJs(String script, Object... args) {
		return jsActions.executeJs(script, args);
	}

	@Override
	public void setSliderValueJS(WebElement slider, String value) {
		jsActions.setSliderValueJS(slider, value);
	}

	@Override
	public void chooseDate(WebElement ele, String data) {
		jsActions.chooseDate(ele, data);
	}

	@Override
	public void scroll(WebElement ele) {
		jsActions.scroll(ele);
	}

	// ========================================================================
	// FILE UPLOAD
	// ========================================================================

	@Override
	public void fileUpload(WebElement ele, String filePath) {
		fileUploadActions.fileUpload(ele, filePath);
	}

	@Override
	public void fileUploadWithJs(WebElement ele, String filePath) {
		fileUploadActions.fileUploadWithJs(ele, filePath);
	}
}
