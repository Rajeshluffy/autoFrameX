2.11 Performance Testing Tools
Primary: JMeter 5.6

// Embed JMeter tests in CI/CD
@Test(groups = "performance")
public void loadTest_UserLogin() {
    JMeterTestPlan testPlan = new JMeterTestPlan();
    testPlan.setThreadGroup(1000, 10, 60)  // 1000 threads, 10 ramp-up, 60s duration
        .addHttpSampler("POST", "/auth/login", userCredentials)
        .addAssertion(ResponseCode.OK)
        .addListener("results.jtl");
    
    testPlan.run();
    
    // Analyze results
    JMeterResults results = JMeterResults.parse("results.jtl");
    Assert.assertTrue(results.getPercentile95() < 500, "P95 latency must be <500ms");
}
Secondary: Gatling (Scala-based, better than JMeter)

class UserSimulation extends Simulation {
  val httpConf = http
    .baseUrl("https://api.example.com")
    .acceptHeader("application/json")

  val scn = scenario("Login Performance")
    .exec(http("Login").post("/auth/login").body(StringBody(credentials)))
    .pause(1)

  setUp(scn.inject(constantUsersPerSec(100) during 60.seconds))
    .protocols(httpConf)
    .assertions(
      global.responseTime.percentile(95).lt(500),
      global.successfulRequests.percent.gt(99.5)
    )
}
2.12 Security Testing Integration
OWASP Integration via ZAP:

@Test(groups = "security")
public void securityScan_OWASPTop10() {
    // 1. Capture traffic via ZAP proxy
    ClientApi zapApi = new ClientApi("127.0.0.1", 8080);
    
    // 2. Run tests normally (requests flow through ZAP)
    automationTest_normalFlow();
    
    // 3. Passive scanning (automatic)
    zapApi.pscan.enableAllScanners();
    
    // 4. Active scanning (targeted)
    zapApi.ascan.scan(baseUrl, "", "", "", "", "");
    
    // 5. Generate report
    zapApi.report.generateReport("/zap-report.html", "html");
    
    // 6. Parse and assert
    ZAPReport report = parseZAPReport("/zap-report.html");
    Assert.assertTrue(report.getCriticalIssues().isEmpty(), 
        "No critical security issues allowed");
}
SAST Integration via SonarQube:

mvn sonar:sonar \
    -Dsonar.projectKey=automation-framework \
    -Dsonar.host.url=https://sonarqube.company.com \
    -Dsonar.login=<token>
Rules enforced:
- No hardcoded credentials (secrets detection)
- No SQL injection vulnerabilities
- Proper exception handling
- No unvalidated input
