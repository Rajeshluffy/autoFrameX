package step.defs.ui;

import com.framework.cucumber.api.base.CucumberScenarioContext;

/**
 * Scenario-scoped state for UI tests. The browser itself is not kept here: step classes extend
 * {@code CucumberProjectBase} and ask it for {@code getDriver()}.
 */
public class UiScenarioContext extends CucumberScenarioContext {

    public String currentPageTitle;
}
