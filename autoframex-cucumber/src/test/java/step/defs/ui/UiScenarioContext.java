package step.defs.ui;

import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Scenario-scoped state for UI tests.
 * {@code driver} is null at construction time — ScenarioHooks @Before sets it
 * after acquiring from the pool.
 */
public class UiScenarioContext {

    public RemoteWebDriver driver;
    public String currentPageTitle;
}
