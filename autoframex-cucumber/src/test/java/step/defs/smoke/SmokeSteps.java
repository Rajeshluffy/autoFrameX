package step.defs.smoke;

import com.framework.cucumber.api.base.CucumberProjectBase;
import com.framework.utils.ValidationUtils;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Steps of the lifecycle smoke scenario. They hold no browser code of their own beyond {@code getDriver()}. */
public class SmokeSteps extends CucumberProjectBase {

    private String title;

    @When("the page title is read")
    public void thePageTitleIsRead() {
        title = getDriver().getTitle();
    }

    @Then("the page title is {string}")
    public void thePageTitleIs(String expected) {
        ValidationUtils.assertTextContains(title, expected, "Page title on the app URL");
    }
}
