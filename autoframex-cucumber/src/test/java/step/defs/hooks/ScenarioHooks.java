package step.defs.hooks;

import org.openqa.selenium.remote.RemoteWebDriver;

import com.framework.utils.ExtentReportManager;
import com.framework.utils.LogUtils;
import com.framework.utils.ScreenshotUtils;
import com.framework.utils.VideoRecorder;

import design.patterns.object.pool.DriverPoolManager;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/**
 * An example of project hooks that sit on top of the framework's: video recording and failure evidence for a UI
 * scenario. They do not acquire or return the browser; {@code CucumberHooks} (order 100) does, so these use
 * order 200: Cucumber runs {@code @Before} hooks in ascending order and {@code @After} hooks in descending
 * order, which puts them inside the browser's lifetime on both sides. Like the framework's hooks they skip
 * scenarios tagged {@code @api}.
 */
public class ScenarioHooks {

    private static final ThreadLocal<VideoRecorder> videoRecorder = new ThreadLocal<>();

    @Before(value = "not @api", order = 200)
    public void startVideo(Scenario scenario) {
        LogUtils.step("Scenario started: " + scenario.getName());
        try {
            VideoRecorder vr = new VideoRecorder();
            vr.start(scenario.getName(), driver());
            videoRecorder.set(vr);
        } catch (Exception e) {
            LogUtils.error("Video recording could not start for " + scenario.getName() + ": " + e.getMessage(), e);
        }
    }

    @After(value = "not @api", order = 200)
    public void stopVideoAndKeepEvidence(Scenario scenario) {
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

        RemoteWebDriver driver = driver();
        if (scenario.isFailed() && driver != null) {
            ScreenshotUtils.captureFailureEvidence(driver, "./" + ExtentReportManager.getFolderName() + "/images");
        }
        LogUtils.step("Scenario ended: " + scenario.getStatus());
    }

    private static RemoteWebDriver driver() {
        return DriverPoolManager.getInstance().getDriver();
    }
}
