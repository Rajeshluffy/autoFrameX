package com.api.testng;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;

import com.api.testdata.ApiCleanup;
import com.framework.utils.Reporter;

/**
 * Base class for TestNG test classes that call APIs and never open a browser: it gives them the framework's
 * report ({@link Reporter}: one report node per class and per method, every API call's timing as a step) and runs
 * their {@link ApiCleanup} undos after each test.
 *
 * <p>An API-only project depends on {@code autoframex-core} and {@code autoframex-api} only, so the Selenium-based
 * {@code ProjectSpecificMethods} is not available to it. Before this class, such a project had to subclass the
 * abstract {@code Reporter} itself, implement its screenshot method, and remember to write the cleanup hook
 * (nothing said so).
 *
 * <pre>
 * {@literal @}TestMetadata(name = "Incident API", authors = "qa", category = "api")
 * public class IncidentApiTest extends ApiTestBase {
 *
 *     {@literal @}Test
 *     public void createsAnIncident() {
 *         ResponseAPI created = client.send(base, ApiRequest.post("incident").body(payload));
 *         ApiAssertions.that(created).created();
 *         ApiCleanup.register("delete incident", () -&gt; client.send(base, ApiRequest.delete("incident/{id}").pathParam("id", id)));
 *     }   // the undo runs after the test, pass or fail
 * }
 * </pre>
 */
public abstract class ApiTestBase extends Reporter {

    private static final Logger logger = LoggerFactory.getLogger(ApiTestBase.class);

    /** There is no browser to take a picture of. */
    @Override
    public long takeSnap() {
        return 0;
    }

    /**
     * Runs the undos the test registered, newest first, whatever the test's outcome. A failing undo is reported as
     * a warning in the report and never fails this hook: a failing {@code @AfterMethod} makes TestNG skip the
     * tests that follow, which would turn one leaked record into a lost test run. Use
     * {@link ApiCleanup#runAllOrFail()} inside the test itself where leaked data should fail it.
     *
     * <p>Runs before {@code Reporter.tearDownTest} (TestNG runs a subclass's {@code @AfterMethod} first), so the
     * cleanup steps are part of the test's report node.
     */
    @AfterMethod(alwaysRun = true)
    public void runApiCleanup() {
        List<String> failures = ApiCleanup.runAll();
        for (String failure : failures) {
            logger.warn("Test data left behind: {}", failure);
            reportStep("Cleanup failed, test data left behind: " + failure, "warning", false);
        }
    }
}
