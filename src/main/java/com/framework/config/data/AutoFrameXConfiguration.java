package com.framework.config.data;

import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config;

/**
 * Default {@link ProjectAppConfiguration} for the autoFrameX framework sandbox.
 *
 * <p>This is the built-in fallback used when no {@code configClass} parameter
 * is declared in the TestNG XML. It points at {@code frameworkConfig.properties}
 * (already on the classpath) so the framework boots with sensible defaults even
 * when no project-specific properties file exists.
 *
 * <p><b>For a real application</b>, create your own interface following this
 * pattern (see {@link ProjectAppConfiguration} Javadoc), add your project's
 * properties file to {@code @Config.Sources}, and declare it in your TestNG XML:
 * <pre>
 * &lt;parameter name="configClass"
 *            value="com.myapp.config.data.MyAppConfiguration"/&gt;
 * </pre>
 *
 * <p><b>URL / credential overrides (highest → lowest priority):</b>
 * <ol>
 *   <li>TestNG {@code <parameter>}: {@code devUrl}, {@code qaUrl}</li>
 *   <li>Env vars: {@code APP_DEV_URL}, {@code APP_QA_URL}, {@code APP_USERNAME},
 *       {@code APP_PASSWORD}</li>
 *   <li>JVM system props: {@code -DdevUrl=...}</li>
 *   <li>Keys in {@code frameworkConfig.properties}:
 *       {@code autoFrameX.app.dev.url}, etc.</li>
 *   <li>{@code @DefaultValue} below</li>
 * </ol>
 */
@LoadPolicy(LoadType.MERGE)
@Config.Sources({
    "classpath:frameworkConfig.properties"
})
public interface AutoFrameXConfiguration extends ProjectAppConfiguration {

    // ── URLs ─────────────────────────────────────────────────────────────────

    @Key("autoFrameX.app.dev.url")
    @DefaultValue("http://localhost:8080")
    @Override
    String devUrl();

    @Key("autoFrameX.app.qa.url")
    @DefaultValue("http://localhost:8080")
    @Override
    String qaUrl();

    // ── Credentials ───────────────────────────────────────────────────────────

    @Key("autoFrameX.app.username")
    @DefaultValue("admin")
    @Override
    String primaryUserName();

    @Key("autoFrameX.app.password")
    @DefaultValue("admin")
    @Override
    String primaryPassword();
}
