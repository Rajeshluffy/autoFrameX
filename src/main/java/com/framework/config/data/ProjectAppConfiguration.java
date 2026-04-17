package com.framework.config.data;

/**
 * Complete configuration contract for any project that runs on this framework.
 *
 * <p><b>Design Patterns applied:</b>
 * <ul>
 *   <li><b>Composite (Structural)</b> — extends {@link FrameworkConfiguration},
 *       so one Owner proxy covers framework settings (browser, wait, pool) AND
 *       project settings (URL, credentials, DB) in a single interface.</li>
 *   <li><b>Bridge (Structural)</b> — the abstraction hierarchy
 *       ({@code FrameworkConfiguration → ProjectAppConfiguration → ConcreteConfig})
 *       is decoupled from the implementation (which {@code .properties} files are
 *       loaded). Teams swap projects by changing {@code @Config.Sources}, not this
 *       interface.</li>
 *   <li><b>Strategy (Behavioral)</b> — each concrete implementation is an
 *       interchangeable strategy. {@link com.framework.config.data.ProjectDirector}
 *       works against this interface only, never against a concrete class.</li>
 * </ul>
 *
 * <p><b>How to add a new project:</b>
 * <ol>
 *   <li>Create an interface:
 *       {@code public interface MyProjectConfig extends ProjectAppConfiguration}</li>
 *   <li>Annotate with both property files so Owner's MERGE policy picks up
 *       framework defaults automatically:
 *       <pre>
 *       {@literal @}LoadPolicy(LoadType.MERGE)
 *       {@literal @}Config.Sources({
 *           "classpath:myProjectConfig.properties",
 *           "classpath:frameworkConfig.properties"   // framework defaults
 *       })
 *       </pre></li>
 *   <li>Override URL and credential methods with project-specific {@code @Key}
 *       annotations.</li>
 *   <li>Provide {@code default} implementations for {@link #primaryUserName()}
 *       and {@link #primaryPassword()} that delegate to your credential keys.</li>
 *   <li>Add one parameter to your TestNG XML:
 *       <pre>
 *       &lt;parameter name="configClass"
 *                  value="com.myproject.config.MyProjectConfig"/&gt;
 *       </pre></li>
 * </ol>
 * No framework code changes are needed.
 */
public interface ProjectAppConfiguration extends FrameworkConfiguration, DatabaseConfiguration {

    // ------------------------------------------------------------------
    // Environment URLs  (project-specific — override with @Key)
    // ------------------------------------------------------------------

    /** Development / local environment base URL. */
    String devUrl();

    /** QA / staging environment base URL. */
    String qaUrl();

    /**
     * Production environment base URL.
     * Defaults to empty so projects without a distinct prod URL compile without error.
     */
    @DefaultValue("")
    String prodUrl();

    // ------------------------------------------------------------------
    // Primary login credentials  (project-specific — implement as default)
    // ------------------------------------------------------------------

    /**
     * The main username for logging in during test execution.
     *
     * <p>Implement as a {@code default} method that delegates to your
     * project's credential key:
     * <pre>
     * default String primaryUserName() { return loginUserName(); }
     * </pre>
     */
    String primaryUserName();

    /**
     * The main password for logging in during test execution.
     *
     * <p>Implement as a {@code default} method that delegates to your
     * project's credential key:
     * <pre>
     * default String primaryPassword() { return loginPassword(); }
     * </pre>
     */
    String primaryPassword();

    // ------------------------------------------------------------------
    // Database  (optional — defaults to empty for non-DB projects)
    // ------------------------------------------------------------------

    @DefaultValue("")
    String dbUrl();

    @DefaultValue("")
    String dbUserName();

    @DefaultValue("")
    String dbPassword();

    /** Optional SQL query used by data-driven DB tests. Defaults to empty. */
    @DefaultValue("")
    String dbQuery();
}
