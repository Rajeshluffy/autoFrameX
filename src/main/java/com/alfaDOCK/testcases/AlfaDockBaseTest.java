package com.alfaDOCK.testcases;

import org.testng.annotations.BeforeMethod;

import com.alfa3DViewer.pages.Loading;
import com.alfaDOCK.config.data.AlfaDockConfiguration;
import com.alfaDOCK.pages.AlfaDockHomePage;
import com.alfaDOCK.pages.CompanyLogin;
import com.framework.config.data.ConfigurationManager;
import com.framework.testng.api.base.ProjectSpecificMethods;

/**
 * Abstract base for all AlfaDOCK test classes.
 *
 * <p><b>Lifecycle (TestNG runs parent @BeforeMethod before child):</b>
 * <pre>
 *   ProjectSpecificMethods.preCondition()  →  acquires driver, navigates to app URL
 *   AlfaDockBaseTest.login()               →  company login → user login → home page
 *   @Test method                           →  test-specific navigation + assertions
 * </pre>
 *
 * <p>Subclasses call {@link #openFileInViewer(String)} to navigate from the
 * home page through Drawing Manager to the 3D viewer in one reusable step.
 */
public abstract class AlfaDockBaseTest extends ProjectSpecificMethods {

    protected final AlfaDockConfiguration alfaConfig =
            ConfigurationManager.getConfiguration(AlfaDockConfiguration.class);

    /**
     * Performs company + user login, landing on the AlfaDOCK home page.
     * Runs after {@link ProjectSpecificMethods#preCondition()} (driver is ready).
     */
    @BeforeMethod(alwaysRun = true)
    public void login() {
        new CompanyLogin()
                .enterCompanyUserName(alfaConfig.loginCompanyUserName())
                .enterCompanyPassword(alfaConfig.loginCompanyPassword())
                .selectCompanyUserLogin()
                .verifyUserLoginPage()
                .enterUserUserName(alfaConfig.loginUserUserName())
                .enterUserPassword(alfaConfig.loginUserPassword())
                .selectUserLogin()
                .verifyHomePage();
    }
    
    

    /**
     * Navigates from the home page to the 3D viewer for the given file type
     * and waits for the loading overlay to clear.
     *
     * <p>Call this at the start of any {@code @Test} that needs to work inside
     * the viewer (e.g. Layers, AssemblyTree, Dimensions).
     *
     * @param fileType search term and type filter value (e.g. {@code "a3dasm"})
     */
    protected void openFileInViewer(String fileType) {
        new AlfaDockHomePage()
                .selectDrawingManager()
                .verifyDrawingManager()
                .select5S()
                .selectSearch()
                .enterSearchValue(fileType)
                .selectTheFilter()
                .selectTheFileType()
                .selectTheType(fileType)
                .selectTheSearchbutton()
                .selectTheFirstElement()
                .switchtoViewerPage(1);
        new Loading().loadingDisappear();
    }
}
