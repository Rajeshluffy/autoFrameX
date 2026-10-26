package com.api.rest.assured.base;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.api.design.ApiRequest;
import com.api.design.ResponseAPI;
import com.api.testdata.ApiCleanup;
import com.api.testng.ApiTestBase;
import com.framework.utils.TestMetadata;

/**
 * The first test class to run an API test inside the framework's TestNG lifecycle with only {@code autoframex-core}
 * and {@code autoframex-api} on the classpath (an API-only project has no Selenium, so no
 * {@code ProjectSpecificMethods}). It checks that {@code ApiTestBase} gives such a class the report and the cleanup
 * the README promises without the project writing its own hooks.
 */
@TestMetadata(name = "ApiTestBase lifecycle", authors = "framework", category = "api")
public class ApiTestBaseLifecycleTest extends ApiTestBase {

    private static final AtomicInteger CLEANED = new AtomicInteger();

    private FakeHttpServer server;
    private RequestSpecBuilder base;

    @BeforeClass
    public void startServer() throws IOException {
        server = FakeHttpServer.start().on("/api/things", received -> FakeHttpServer.Reply.json(201, "{\"id\":\"42\"}"));
        base = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath("/api");
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() {
        server.close();
    }

    @Test
    public void aCallAndItsCleanupRegistrationRunInsideTheLifecycle() {
        ResponseAPI created = new RestAssuredBase().send(base, ApiRequest.post("things").contentType("application/json").body("{}"));
        Assert.assertEquals(created.getStatusCode(), 201);

        ApiCleanup.register("delete thing " + created.path("id", String.class), CLEANED::incrementAndGet);
        Assert.assertEquals(ApiCleanup.pending(), 1);
    }

    @Test(dependsOnMethods = "aCallAndItsCleanupRegistrationRunInsideTheLifecycle")
    public void theUndoRanAfterTheTestWithoutTheTestAskingForIt() {
        Assert.assertEquals(CLEANED.get(), 1, "ApiTestBase runs ApiCleanup after every test");
        Assert.assertEquals(ApiCleanup.pending(), 0);
    }

    @Test(dependsOnMethods = "theUndoRanAfterTheTestWithoutTheTestAskingForIt")
    public void aFailingUndoDoesNotStopLaterTestsFromRunning() {
        ApiCleanup.register("an undo that fails", () -> {
            throw new IllegalStateException("cannot delete");
        });
        ApiCleanup.register("an undo after it", CLEANED::incrementAndGet);
    }

    @Test(dependsOnMethods = "aFailingUndoDoesNotStopLaterTestsFromRunning")
    public void theTestAfterAFailedUndoRanAndTheOtherUndoStillDid() {
        Assert.assertEquals(CLEANED.get(), 2, "the undo registered after the failing one still ran");
        Assert.assertEquals(ApiCleanup.pending(), 0, "and nothing is left to leak into the next test");
    }

    @Test
    public void thereIsNoBrowserToPhotograph() {
        Assert.assertEquals(takeSnap(), 0L);
    }
}
