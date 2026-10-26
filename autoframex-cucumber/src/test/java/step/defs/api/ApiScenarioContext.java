package step.defs.api;

import com.api.design.ResponseAPI;
import com.api.rest.assured.base.RestAssuredBase;
import com.framework.cucumber.api.base.CucumberScenarioContext;

/**
 * Scenario-scoped state for API tests.
 * PicoContainer creates one instance per scenario and injects it into every
 * step class whose constructor requests it.
 */
public class ApiScenarioContext extends CucumberScenarioContext {

    public final RestAssuredBase apiClient = new RestAssuredBase();
    public ResponseAPI lastResponse;
    public final String baseUrl =
            System.getProperty("api.base.url", "https://jsonplaceholder.typicode.com");
}
