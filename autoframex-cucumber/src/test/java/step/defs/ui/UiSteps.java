package step.defs.ui;

import com.framework.cucumber.api.base.CucumberProjectBase;
import com.framework.utils.LogUtils;
import com.framework.utils.ValidationUtils;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class UiSteps extends CucumberProjectBase {

    private final UiScenarioContext ctx;

    public UiSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("I open the browser")
    public void iOpenTheBrowser() {
        LogUtils.step("Browser ready: " + getDriver().getClass().getSimpleName());
    }

    @When("I navigate to {string}")
    public void iNavigateTo(String url) {
        getDriver().get(url);
        ctx.currentPageTitle = getDriver().getTitle();
        LogUtils.step("Navigated to: " + url + " | title: " + ctx.currentPageTitle);
    }

    @Then("the page title should contain {string}")
    public void thePageTitleShouldContain(String expected) {
        ValidationUtils.assertTextContains(getDriver().getTitle(), expected, "Page title check");
    }
}
