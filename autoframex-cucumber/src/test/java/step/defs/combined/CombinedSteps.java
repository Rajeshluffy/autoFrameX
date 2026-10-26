package step.defs.combined;

import com.api.rest.assured.base.RequestSpecBuilder;
import com.framework.cucumber.api.base.CucumberProjectBase;
import com.framework.utils.LogUtils;
import com.framework.utils.ValidationUtils;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import step.defs.api.ApiScenarioContext;
import step.defs.ui.UiScenarioContext;

/**
 * Demonstrates multi-context PicoContainer injection.
 * Both contexts are the same instances shared with ApiSteps and UiSteps within the same scenario;
 * the browser comes from CucumberProjectBase.getDriver() (acquired by CucumberHooks).
 */
public class CombinedSteps extends CucumberProjectBase {

    private final ApiScenarioContext apiCtx;
    private final UiScenarioContext uiCtx;

    public CombinedSteps(ApiScenarioContext apiCtx, UiScenarioContext uiCtx) {
        this.apiCtx = apiCtx;
        this.uiCtx = uiCtx;
    }

    @Given("I have a valid API response and an open browser")
    public void iHaveAValidApiResponseAndAnOpenBrowser() {
        LogUtils.step("Contexts ready — API base: " + apiCtx.baseUrl
                + " | browser: " + getDriver().getClass().getSimpleName());
    }

    @When("I fetch post {int} from the API and navigate to its source")
    public void iFetchPostFromApiAndNavigate(int id) {
        RequestSpecification spec = new RequestSpecBuilder()
                .setBaseUri(apiCtx.baseUrl)
                .setContentType(ContentType.JSON)
                .build();
        apiCtx.lastResponse = apiCtx.apiClient.get(spec, "/posts/" + id);
        LogUtils.step("API GET /posts/" + id + " → HTTP " + apiCtx.lastResponse.getStatusCode());

        getDriver().get(apiCtx.baseUrl);
        uiCtx.currentPageTitle = getDriver().getTitle();
        LogUtils.step("Browser navigated to: " + apiCtx.baseUrl
                + " | title: " + uiCtx.currentPageTitle);
    }

    @Then("both the API status and page title should be valid")
    public void bothApiStatusAndPageTitleShouldBeValid() {
        ValidationUtils.assertResponseCode(apiCtx.lastResponse.getStatusCode(), 200,
                "Combined: API status");
        ValidationUtils.assertNotNull(uiCtx.currentPageTitle,
                "Combined: page title should not be null");
    }
}
