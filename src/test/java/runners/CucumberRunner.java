package runners;

import org.testng.ITestContext;
import org.testng.annotations.BeforeTest;

import com.framework.utils.Reporter;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
    features   = {"src/test/resources/features"},
    glue       = {"step.defs"},
    plugin     = {"pretty"},
    monochrome = true
)
public class CucumberRunner extends AbstractTestNGCucumberTests {

    /**
     * Points Cucumber's html/json plugins at the same per-run report folder
     * TestNG uses (reports/&lt;date_timestamp&gt;/), named after the suite.
     *
     * <p>The destination can't be a compile-time constant in {@code @CucumberOptions}
     * since the folder name is only known at runtime, so it's supplied via the
     * {@code cucumber.plugin} system property instead. This must run in
     * {@code @BeforeTest} — before AbstractTestNGCucumberTests' own
     * {@code @BeforeClass} constructs the Cucumber runtime — for the override
     * to take effect.
     */
    @BeforeTest(alwaysRun = true)
    public void initReportFolder(ITestContext context) {
        String suiteName = (context != null && context.getSuite() != null)
                ? context.getSuite().getName() : null;
        Reporter.initReportInfrastructure(suiteName);

        String base = Reporter.folderName + "/"
                + Reporter.getReportFileName().replaceFirst("\\.html$", "");
        System.setProperty("cucumber.plugin", "html:" + base + ".html,json:" + base + ".json");
    }
}
