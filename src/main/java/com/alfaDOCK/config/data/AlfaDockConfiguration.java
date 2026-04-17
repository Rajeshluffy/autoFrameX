package com.alfaDOCK.config.data;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;

import com.framework.config.data.ProjectAppConfiguration;

/**
 * AlfaDOCK project configuration.
 *
 * <p><b>Property file loading (Composite pattern via Owner MERGE):</b>
 * <ol>
 *   <li>{@code alfaDOCKConfig.properties} — AlfaDOCK-specific keys
 *       (URLs, credentials). Searched first; project keys take priority.</li>
 *   <li>{@code frameworkConfig.properties} — Framework-level keys
 *       (browser, wait times, pool). Used as automatic fallback for all
 *       inherited {@link com.framework.config.data.FrameworkConfiguration} methods.</li>
 * </ol>
 * Owner's {@code LoadType.MERGE} walks both files so every key is resolved
 * without any manual combination code in {@code ProjectDirector}.
 *
 * <p><b>How to activate in TestNG XML:</b>
 * <pre>
 * &lt;parameter name="configClass"
 *            value="com.alfaDOCK.config.data.AlfaDockConfiguration"/&gt;
 * &lt;parameter name="environment" value="dev"/&gt;   &lt;!-- dev | qa | prod --&gt;
 * </pre>
 *
 * <p><b>Credential mapping:</b> AlfaDOCK has a two-step login.
 * {@link #primaryUserName()} / {@link #primaryPassword()} map to the
 * <em>company-level</em> credentials (first login screen).
 * User-level credentials ({@link #loginUserUserName()}, {@link #loginUserPassword()})
 * are read directly from this interface in AlfaDOCK page objects / test cases.
 */
@LoadPolicy(LoadType.MERGE)
@Config.Sources({
    "classpath:alfaDOCKConfig.properties",   // project-specific keys (priority)
    "classpath:frameworkConfig.properties"   // framework defaults (fallback)
})
public interface AlfaDockConfiguration extends ProjectAppConfiguration {

    // ------------------------------------------------------------------
    // URLs
    // ------------------------------------------------------------------

    @Key("alfaDOCK.dev.url")
    String devUrl();

    @Key("alfaDOCK.qa.url")
    String qaUrl();

    @Key("alfaDOCK.prod.url")
    @DefaultValue("")
    String prodUrl();

    // ------------------------------------------------------------------
    // Company-level credentials  (first login screen)
    // ------------------------------------------------------------------

    @Key("alfaDOCK.company.userName")
    String loginCompanyUserName();

    @Key("alfaDOCK.company.password")
    String loginCompanyPassword();

    /** Satisfies the framework's common credential contract. */
    default String primaryUserName() { return loginCompanyUserName(); }

    /** Satisfies the framework's common credential contract. */
    default String primaryPassword() { return loginCompanyPassword(); }

    // ------------------------------------------------------------------
    // User-level credentials  (second login screen)
    // ------------------------------------------------------------------

    @Key("alfaDOCK.user.userName")
    String loginUserUserName();

    @Key("alfaDOCK.user.password")
    String loginUserPassword();

    // ------------------------------------------------------------------
    // Database  — AlfaDOCK does not use a test DB; all default to empty
    // ------------------------------------------------------------------

    @DefaultValue("") String dbUrl();
    @DefaultValue("") String dbUserName();
    @DefaultValue("") String dbPassword();
    @DefaultValue("") String dbQuery();
}
