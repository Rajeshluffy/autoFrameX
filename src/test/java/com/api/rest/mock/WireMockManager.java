package com.api.rest.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class WireMockManager {

    private static WireMockServer wireMockServer;

    private WireMockManager() {}

    public static void startServer(int port) {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            wireMockServer = new WireMockServer(options().port(port));
            wireMockServer.start();
            WireMock.configureFor("localhost", port);
            System.out.println("WireMock server started on port " + port);
        } else {
            System.out.println("WireMock server already running on port " + wireMockServer.port());
        }
    }

    public static void stopServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            System.out.println("WireMock server stopped.");
        }
    }

    public static void stubGet(String url, int statusCode, String responseBody) {
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    public static void stubPost(String url, int statusCode, String responseBody) {
        stubFor(post(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    public static void resetStubs() {
        if (wireMockServer != null) {
            WireMock.reset();
        }
    }
}
