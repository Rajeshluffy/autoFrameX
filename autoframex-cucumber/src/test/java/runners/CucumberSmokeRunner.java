package runners;

import org.testng.ITestContext;
import org.testng.annotations.BeforeTest;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

/**
 * Runs the one offline browser scenario that proves the Cucumber lifecycle works end to end: the hooks acquire a
 * real browser from the pool, open the app URL, a step class that extends {@code CucumberProjectBase} drives it,
 * and the hooks return it. It needs Chrome but no network and no application: point the app URL at a {@code data:}
 * page with the {@code BASE_URL} environment variable (see {@code testng-smoke.xml}).
 *
 * <p>It deliberately does not extend {@link CucumberRunner}: Cucumber merges the options of a runner's
 * superclasses, so the parent's feature path and its {@code not @smoke} tag filter would be added to this
 * runner's own and no scenario would be selected (a green run of zero tests).
 */
@CucumberOptions(
    features   = {"classpath:features/smoke"},
    glue       = {"step.defs.smoke", "step.defs.hooks", "com.framework.cucumber.api.base"},
    tags       = "@smoke",
    plugin     = {"pretty"},
    monochrome = true
)
public class CucumberSmokeRunner extends AbstractTestNGCucumberTests {

    @BeforeTest(alwaysRun = true)
    public void initReportFolder(ITestContext context) {
        CucumberRunner.configureReports(context);
    }
}
