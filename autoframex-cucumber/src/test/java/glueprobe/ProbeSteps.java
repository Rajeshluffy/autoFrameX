package glueprobe;

import com.framework.cucumber.api.base.CucumberProjectBase;

import io.cucumber.java.en.Given;

/**
 * A step class written the way the framework's documentation says to: it extends {@link CucumberProjectBase}
 * to get the Selenium actions. Used only by {@code CucumberGlueTest}, never by a real run.
 */
public class ProbeSteps extends CucumberProjectBase {

    @Given("a probe step")
    public void aProbeStep() {
        // loaded by a dry run only
    }
}
