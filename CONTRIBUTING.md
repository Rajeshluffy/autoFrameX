# Contributing to autoFrameX

Two audiences, two paths:
- **Adding your project** (you're a team consuming this framework) — see below.
- **Changing the framework itself** — see [Framework-core changes](#framework-core-changes).

## Add your project

Your test classes never subclass anything project-specific — they extend the
framework's own `ProjectSpecificMethods`/`CucumberProjectBase`, and one config
class tells the framework which URLs/credentials/DB to use.

1. **Create your config interface**, extending `ProjectAppConfiguration`:
   ```java
   public interface MyProjectConfig extends ProjectAppConfiguration { }
   ```

2. **Point it at two property files** so Owner's `MERGE` policy picks up framework
   defaults (pool size, waits, retry) automatically alongside your own settings:
   ```java
   @LoadPolicy(LoadType.MERGE)
   @Config.Sources({
       "classpath:myProjectConfig.properties",
       "classpath:frameworkConfig.properties"
   })
   public interface MyProjectConfig extends ProjectAppConfiguration { }
   ```

3. **Create `myProjectConfig.properties`** (anywhere on the classpath, typically
   `src/main/resources` or `src/test/resources`) with your URLs and credentials:
   ```properties
   devUrl=https://dev.myapp.com/
   qaUrl=https://qa.myapp.com/
   loginUserName=${MY_APP_USER}
   loginPassword=${MY_APP_PASSWORD}
   ```

4. **Implement the credential delegate methods** as `default` methods on your
   interface:
   ```java
   default String primaryUserName() { return loginUserName(); }
   default String primaryPassword() { return loginPassword(); }
   ```

5. **Write your test class**, extending `ProjectSpecificMethods` (TestNG) or
   `CucumberProjectBase` (Cucumber step classes) — see
   `src/test/java/com/framework/testng/examples/DataProviderExampleTest.java` and
   `TargetedExampleTest.java` for two complete, runnable references (one per
   execution mode).

6. **Pick an execution mode and copy the matching suite XML template:**
   - `src/test/resources/testng-data-provider.xml` — every test runs once per row
     in an Excel file (`data/accounts.xlsx` is a working example fixture).
   - `src/test/resources/testng-targeted.xml` — every test runs once against one
     explicitly configured account.

7. **Point the suite at your config class and your test classes:**
   ```xml
   <parameter name="configClass" value="com.myapp.config.MyProjectConfig"/>
   <classes>
       <class name="com.myapp.tests.MyLoginTest"/>
   </classes>
   ```

8. **Run it:**
   ```bash
   mvn test -Dtestng.suite.file=path/to/your-suite.xml
   ```

No framework code changes are needed for any of the above — this is the framework's
one genuinely open extension point (see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#plugin-architecture--closed-vs-open-extension-points)
for how the others compare).

**Need a custom browser?** See `BrowserRegistry.registerCustom(...)` +
`BrowserType.CUSTOM` in `design.patterns.factory.browser` — one custom browser
without forking the enum.

## Framework-core changes

If you're changing `src/main/java` itself rather than adding a consuming project:

- Read [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) first — several rules
  there exist specifically to prevent debt that was fixed once from creeping back
  (the config/pool multi-context binding rule in particular has already caused one
  real regression, documented there).
- Check [docs/TECHNICAL_DEBT_REGISTER.md](docs/TECHNICAL_DEBT_REGISTER.md) before
  touching `design.patterns.*`, `ConfigManager`, or `DriverPoolManager` — several
  known issues there are deliberately not yet fixed and documented as to why.
- Verify against a real suite, not just `mvn compile` — `testng-ci.xml` (fast,
  browser-free) and `testng-parallel-smoke.xml` (real Chrome, real parallel
  execution) both exist specifically so a change can be checked end-to-end before
  it's considered done.
- Checkstyle/PMD/SpotBugs run on every `mvn verify` in report-only mode (the
  codebase was never checked against them before they were added) — don't ignore
  new findings in files you're already touching, even though they won't fail the
  build yet.
