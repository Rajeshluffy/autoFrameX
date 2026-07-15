package step.defs.api;

import com.api.rest.assured.base.RequestSpecBuilder;
import com.framework.utils.LogUtils;
import com.framework.utils.ValidationUtils;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class ApiSteps {

    private final ApiScenarioContext ctx;

    public ApiSteps(ApiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("the JSONPlaceholder API is available")
    public void theApiIsAvailable() {
        LogUtils.step("API base URL: " + ctx.baseUrl);
    }

    @When("I request post with id {int}")
    public void iRequestPostWithId(int id) {
        RequestSpecification spec = new RequestSpecBuilder()
                .setBaseUri(ctx.baseUrl)
                .setContentType(ContentType.JSON)
                .build();
        ctx.lastResponse = ctx.apiClient.get(spec, "/posts/" + id);
        LogUtils.step("GET /posts/" + id + " → HTTP " + ctx.lastResponse.getStatusCode());
    }

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int expectedCode) {
        ValidationUtils.assertResponseCode(ctx.lastResponse.getStatusCode(), expectedCode,
                "API response status");
    }

    @Then("the response body should contain a title")
    public void theResponseBodyShouldContainATitle() {
        ValidationUtils.assertNotNull(ctx.lastResponse.getJsonPath("title"),
                "Post title field should be present");
    }
}
