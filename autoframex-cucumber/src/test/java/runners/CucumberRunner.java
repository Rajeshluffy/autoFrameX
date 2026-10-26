package runners;

import org.testng.ITestContext;
import org.testng.annotations.BeforeTest;

import com.framework.utils.ExtentReportManager;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
    // classpath-relative, not filesystem-relative: this class can be invoked
    // from a different module's working directory (e.g. autoframex-testkit's
    // aggregate testng.xml, which doesn't have this module's own basedir) —
    // a plain "src/test/resources/features" path only resolved correctly when
    // Surefire's cwd happened to be this module's own directory (TD-20 Stage 2,
    // 2026-07-15 — found via testng.xml failing from autoframex-testkit).
    features   = {"classpath:features"},
    // com.framework.cucumber.api.base is where CucumberHooks (the browser lifecycle) lives.
    glue       = {"step.defs", "com.framework.cucumber.api.base"},
    tags       = "not @smoke",
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
        configureReports(context);
    }

    /** Shared with {@link CucumberSmokeRunner}, which cannot extend this class (see there). */
    static void configureReports(ITestContext context) {
        String suiteName = (context != null && context.getSuite() != null)
                ? context.getSuite().getName() : null;
        ExtentReportManager.initReportInfrastructure(suiteName);

        String base = ExtentReportManager.getFolderName() + "/"
                + ExtentReportManager.getReportFileName().replaceFirst("\\.html$", "");
        System.setProperty("cucumber.plugin", "html:" + base + ".html,json:" + base + ".json");
    }
}
