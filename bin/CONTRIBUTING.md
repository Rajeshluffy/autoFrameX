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
   `autoframex-selenium/src/test/java/com/framework/testng/examples/DataProviderExampleTest.java`
   and `TargetedExampleTest.java` for two complete, runnable references (one per
   execution mode). (Framework code is split into multiple Maven modules —
   `autoframex-core`, `autoframex-selenium`, `autoframex-api`, etc. — see
   [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full module map.)

6. **Pick an execution mode and copy the matching suite XML template:**
   - `autoframex-selenium/src/test/resources/testng-data-provider.xml` — every
     test runs once per row in an Excel file
     (`autoframex-selenium/data/accounts.xlsx` is a working example fixture).
   - `autoframex-selenium/src/test/resources/testng-targeted.xml` — every test
     runs once against one explicitly configured account.

7. **Point the suite at your config class and your test classes:**
   ```xml
   <parameter name="configClass" value="com.myapp.config.MyProjectConfig"/>
   <classes>
       <class name="com.myapp.tests.MyLoginTest"/>
   </classes>
   ```

8. **Run it** (suite files resolve relative to the module you place them in —
   e.g. `autoframex-selenium` for a UI suite):
   ```bash
   mvn test -pl autoframex-selenium -Dtestng.suite.file=path/to/your-suite.xml
   ```

No framework code changes are needed for any of the above — this is the framework's
one genuinely open extension point (see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#plugin-architecture--closed-vs-open-extension-points)
for how the others compare).

**Need a custom browser?** `BrowserRegistry` (in `autoframex-selenium`'s
`design.patterns.factory.browser`) is an open, String-keyed registry — register
your own id/provider pair without forking any enum:
```java
BrowserRegistry.register("MY_CUSTOM_BROWSER", config -> new MyBrowser(...));
// then: <parameter name="browser" value="MY_CUSTOM_BROWSER"/> in your TestNG XML
```

## Framework-core changes

If you're changing framework source itself rather than adding a consuming project
(source now lives under per-concern modules — `autoframex-core`,
`autoframex-selenium`, `autoframex-api`, etc. — see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full map):

- Read [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) first — several rules
  there exist specifically to prevent debt that was fixed once from creeping back
  (the config/pool multi-context binding rule in particular has already caused one
  real regression, documented there).
- Check [docs/TECHNICAL_DEBT_REGISTER.md](docs/TECHNICAL_DEBT_REGISTER.md) before
  touching `design.patterns.*`, `ConfigManager`, or `DriverPoolManager` — several
  known issues there are deliberately not yet fixed and documented as to why.
- Build with `mvn install -DskipTests -Djacoco.skip=true` first if you've only
  touched one module — downstream modules resolve upstream ones (e.g.
  `autoframex-selenium` needs `autoframex-core`) from the local repo, not the
  reactor in-memory. Then verify against a real suite, not just `mvn compile` —
  `mvn test -pl autoframex-selenium -Dtestng.suite.file=testng-ci.xml` (fast,
  browser-free) and `...testng-parallel-smoke.xml` (real Chrome, real parallel
  execution) both exist specifically so a change can be checked end-to-end before
  it's considered done.
- Checkstyle/PMD/SpotBugs run on every `mvn verify` in report-only mode (the
  codebase was never checked against them before they were added) — don't ignore
  new findings in files you're already touching, even though they won't fail the
  build yet.
