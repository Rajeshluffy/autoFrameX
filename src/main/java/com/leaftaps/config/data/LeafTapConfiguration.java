package com.leaftaps.config.data;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;

import com.framework.config.data.ProjectAppConfiguration;

/**
 * LeafTaps project configuration.
 *
 * <p><b>Property file loading (Composite pattern via Owner MERGE):</b>
 * <ol>
 *   <li>{@code leafTapConfig.properties} — LeafTaps-specific keys
 *       (URLs, credentials, database). Searched first; project keys take priority.</li>
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
 *            value="com.leaftaps.config.data.LeafTapConfiguration"/&gt;
 * &lt;parameter name="environment" value="dev"/&gt;   &lt;!-- dev | qa | prod --&gt;
 * </pre>
 */
@LoadPolicy(LoadType.MERGE)
@Config.Sources({
    "classpath:leafTapConfig.properties",    // project-specific keys (priority)
    "classpath:frameworkConfig.properties"   // framework defaults (fallback)
})
public interface LeafTapConfiguration extends ProjectAppConfiguration {

    // ------------------------------------------------------------------
    // URLs
    // ------------------------------------------------------------------

    @Key("leaftaps.dev.url")
    String devUrl();

    @Key("leaftaps.qa.url")
    String qaUrl();

    @Key("leaftaps.prod.url")
    @DefaultValue("")
    String prodUrl();

    // ------------------------------------------------------------------
    // Login credentials
    // ------------------------------------------------------------------

    @Key("leaftaps.userName")
    String loginUserName();

    @Key("leaftaps.password")
    String loginPassword();

    /** Satisfies the framework's common credential contract. */
    default String primaryUserName() { return loginUserName(); }

    /** Satisfies the framework's common credential contract. */
    default String primaryPassword() { return loginPassword(); }

    // ------------------------------------------------------------------
    // Database
    // ------------------------------------------------------------------

    @Key("leaftaps.db.url")
    @DefaultValue("")
    String dbUrl();

    @Key("leaftaps.db.userName")
    @DefaultValue("")
    String dbUserName();

    @Key("leaftaps.db.password")
    @DefaultValue("")
    String dbPassword();

    @Key("leaftaps.db.query")
    @DefaultValue("")
    String dbQuery();
}
