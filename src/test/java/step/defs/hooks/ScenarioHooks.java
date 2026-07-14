package step.defs.hooks;

import java.lang.reflect.Method;

import com.framework.utils.LogUtils;
import com.framework.utils.Reporter;
import com.framework.utils.ScreenshotUtils;
import com.framework.utils.VideoRecorder;

import design.patterns.object.pool.DriverPoolManager;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import step.defs.api.ApiScenarioContext;
import step.defs.ui.UiScenarioContext;

/**
 * Lifecycle hooks for every scenario.
 *
 * DriverPoolManager was designed for TestNG's Method-based lifecycle.
 * SCENARIO_METHOD is a package-private no-op method on this class used as a
 * Method handle so the pool has a valid reflection object for logging and
 * browser/URL resolution — all resolution falls through to system properties
 * and frameworkConfig.properties, which is the correct behaviour for Cucumber.
 */
public class ScenarioHooks {

    private static final ThreadLocal<VideoRecorder> videoRecorder = new ThreadLocal<>();

    private static final Method SCENARIO_METHOD;

    static {
        try {
            SCENARIO_METHOD = ScenarioHooks.class.getDeclaredMethod("scenarioPlaceholder");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ApiScenarioContext apiCtx;
    private final UiScenarioContext uiCtx;

    public ScenarioHooks(ApiScenarioContext apiCtx, UiScenarioContext uiCtx) {
        this.apiCtx = apiCtx;
        this.uiCtx = uiCtx;
    }

    @Before(order = 1)
    public void setUp(Scenario scenario) {
        LogUtils.step("Scenario started: " + scenario.getName());
        DriverPoolManager pool = DriverPoolManager.getInstance();
        pool.initializePool(null);
        pool.setupDriver(SCENARIO_METHOD, null);
        uiCtx.driver = pool.getDriver();

        try {
            VideoRecorder vr = new VideoRecorder();
            vr.start(scenario.getName(), uiCtx.driver);
            videoRecorder.set(vr);
        } catch (Exception e) {
            LogUtils.error("Video recording could not start for " + scenario.getName() + ": " + e.getMessage(), e);
        }
    }

    @After(order = 1)
    public void tearDown(Scenario scenario) {
        VideoRecorder vr = videoRecorder.get();
        if (vr != null) {
            try {
                java.io.File videoFile = vr.stop(!scenario.isFailed());
                if (videoFile != null) {
                    LogUtils.step("Failure video: " + videoFile.getAbsolutePath());
                }
            } catch (Exception e) {
                LogUtils.error("Video stop failed for " + scenario.getName() + ": " + e.getMessage(), e);
            } finally {
                videoRecorder.remove();
            }
        }

        if (scenario.isFailed() && uiCtx.driver != null) {
            ScreenshotUtils.captureFailureEvidence(uiCtx.driver, "./" + Reporter.folderName + "/images");
        }
        try {
            DriverPoolManager.getInstance().teardownDriver(SCENARIO_METHOD, !scenario.isFailed());
        } catch (Exception e) {
            LogUtils.error("Driver teardown error: " + e.getMessage(), e);
        }
        LogUtils.step("Scenario ended: " + scenario.getStatus());
    }

    // Package-private — exists solely to provide a Method handle for DriverPoolManager.
    void scenarioPlaceholder() {}
}
