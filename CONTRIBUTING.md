# Contributing to autoFrameX

Two audiences, two paths:
- **Adding your project** (you are a team consuming this framework): see below.
- **Changing the framework itself**: see [Framework changes](#framework-changes).

## Add your project

Your test classes never subclass anything project-specific. They extend the framework's own
`ProjectSpecificMethods` / `CucumberProjectBase`, and one config class tells the framework which
URLs, credentials and database to use.

1. **Create your config interface**, extending `ProjectAppConfiguration`:
   ```java
   public interface MyProjectConfig extends ProjectAppConfiguration { }
   ```

2. **Point it at two property files** so Owner's `MERGE` policy picks up the framework defaults
   (pool size, waits, retry) alongside your own settings:
   ```java
   @LoadPolicy(LoadType.MERGE)
   @Config.Sources({
       "classpath:myProjectConfig.properties",
       "classpath:frameworkConfig.properties"
   })
   public interface MyProjectConfig extends ProjectAppConfiguration { }
   ```

3. **Create `myProjectConfig.properties`** (anywhere on the classpath, typically `src/main/resources`
   or `src/test/resources`). Take credentials from the environment, never write them in the file:
   ```properties
   devUrl=https://dev.myapp.com/
   qaUrl=https://qa.myapp.com/
   loginUserName=${MY_APP_USER}
   loginPassword=${MY_APP_PASSWORD}
   ```
   A value that is blank, or a `${...}` placeholder nothing has filled in, counts as *not set*: the
   framework falls through to the next source and, for something it needs (the URL of the environment you
   run against, a data file), fails with a message that names the key.

4. **Implement the credential delegate methods** as `default` methods on your interface:
   ```java
   default String primaryUserName() { return loginUserName(); }
   default String primaryPassword() { return loginPassword(); }
   ```

5. **Write your test class**, extending `ProjectSpecificMethods` (TestNG) or `CucumberProjectBase`
   (Cucumber step classes). `autoframex-selenium/src/test/java/com/framework/testng/examples/`
   (`DataProviderExampleTest`, `TargetedExampleTest`) holds two complete, runnable references, one per
   execution mode. The module map is in the [README](README.md#project-structure).

6. **Pick an execution mode and copy the matching suite XML template:**
   - `autoframex-selenium/src/test/resources/testng-data-provider.xml`: every test runs once per row of
     an Excel file (`autoframex-selenium/data/accounts.xlsx` is a working example fixture).
   - `autoframex-selenium/src/test/resources/testng-targeted.xml`: every test runs once against one
     explicitly configured account.

7. **Point the suite at your config class and your test classes:**
   ```xml
   <parameter name="configClass" value="com.myapp.config.MyProjectConfig"/>
   <classes>
       <class name="com.myapp.tests.MyLoginTest"/>
   </classes>
   ```

8. **Run it** (suite files resolve relative to the module you place them in, e.g. `autoframex-selenium`
   for a UI suite):
   ```bash
   mvn test -pl autoframex-selenium -Dtestng.suite.file=path/to/your-suite.xml
   ```

No framework code changes are needed for any of the above.

**Need a custom browser?** `BrowserRegistry` (in `autoframex-selenium`'s `design.patterns.factory.browser`)
is an open, String-keyed registry; register your own id and provider without forking any enum:
```java
BrowserRegistry.register("MY_CUSTOM_BROWSER", config -> new MyBrowser(...));
// then: <parameter name="browser" value="MY_CUSTOM_BROWSER"/> in your TestNG XML
```

## Framework changes

If you are changing framework source rather than adding a consuming project:

- **Know the module layout** ([README](README.md#project-structure)) and keep the dependency direction:
  `core` depends on nothing, every other module depends on `core`. `ArchitectureRulesTest` (in
  `autoframex-selenium`) fails the build if a package rule is broken, and its Javadoc says why each rule
  exists.
- **Build the whole reactor first** if you touched a module others depend on: downstream modules resolve
  upstream ones from the local `~/.m2`, not from the reactor in memory.
  ```bash
  mvn -B clean install
  ```
  For a deliberately partial run (`-Dtest=...`, one suite) a module covers less than its coverage floor by
  design: add `-Djacoco.skip=true`.
- **Quality gates fail the build; they are not reports.** Tests, a JaCoCo line-coverage floor per module
  (`jacoco.minimum.line` in the module's pom), PMD priority 1 and 2, SpotBugs High. The README's
  [Code Quality](README.md#code-quality) section has the details. Fix a finding in the code; do not
  suppress it. If you raise coverage, raise that module's floor to a few points under the new figure.
- **Do not make a check pass because nothing was checked.** A wait that times out, a file that is missing,
  an unknown environment, an SLA that is breached, an element that is not there: each of these must fail
  loudly with a message that says what was being looked for. Tests that pin such behaviour go in the module
  that owns the code and are registered in that module's suite XML (a test class that is in no suite never
  runs in CI).
- **Prove a fix with a test that fails without it.** Several tests here use a session-less
  `RemoteWebDriver` subclass or a `java.lang.reflect.Proxy` as a fake driver (no Mockito, no browser); copy
  that pattern (`FakeImplicitWaitDriver`, `WindowSwitchingTest`) for Selenium code that has to run in CI
  without Chrome.
- **Never commit a credential**, including a test one that looks real. See the README's
  [Secrets](README.md#secrets) section; `secret-scan.yml` runs gitleaks on every push and pull request.
- **Record the change** in [CHANGELOG.md](CHANGELOG.md) under *Unreleased*, and say if behaviour changed
  for existing users (stricter, or different default). Release steps are in [RELEASING.md](RELEASING.md).
