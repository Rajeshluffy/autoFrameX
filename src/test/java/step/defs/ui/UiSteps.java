package step.defs.ui;

import com.framework.utils.LogUtils;
import com.framework.utils.ValidationUtils;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class UiSteps {

    private final UiScenarioContext ctx;

    public UiSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("I open the browser")
    public void iOpenTheBrowser() {
        LogUtils.step("Browser ready: " + ctx.driver.getClass().getSimpleName());
    }

    @When("I navigate to {string}")
    public void iNavigateTo(String url) {
        ctx.driver.get(url);
        ctx.currentPageTitle = ctx.driver.getTitle();
        LogUtils.step("Navigated to: " + url + " | title: " + ctx.currentPageTitle);
    }

    @Then("the page title should contain {string}")
    public void thePageTitleShouldContain(String expected) {
        ValidationUtils.assertTextContains(ctx.driver.getTitle(), expected, "Page title check");
    }
}
