package design.patterns.factory.browser;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.config.data.ConfigResolver;

/**
 * Decides which browser safety features the framework may switch off.
 *
 * <p>The providers used to switch these off unconditionally, on every run including
 * production ones. Each is now a decision with an explicit override and a sensible default:
 *
 * <table>
 *   <caption>Browser safety settings</caption>
 *   <tr><th>Setting</th><th>Override (suite parameter, env var, -D)</th><th>Default</th></tr>
 *   <tr><td>{@code --no-sandbox} (Chrome, Edge)</td><td>{@code noSandbox} / {@code NO_SANDBOX}</td>
 *       <td>on only in CI or a container (Docker, Kubernetes), where Chrome often cannot start its
 *       sandbox as root; off on a developer machine</td></tr>
 *   <tr><td>accept invalid TLS certificates</td><td>{@code acceptInsecureCerts} / {@code ACCEPT_INSECURE_CERTS}</td>
 *       <td>on for dev and QA (self-signed certificates are normal there), off for production</td></tr>
 *   <tr><td>relaxed download protection (Chrome)</td><td>{@code relaxedDownloads} / {@code RELAXED_DOWNLOADS}</td>
 *       <td>on for dev and QA, off for production</td></tr>
 * </table>
 *
 * <p>"Production" is the {@code environment} parameter ({@code prod} or {@code production}),
 * the same one the framework already uses to choose the application URL.
 */
final class BrowserSecurityPolicy {

    private static final Logger logger = LoggerFactory.getLogger(BrowserSecurityPolicy.class);

    /** Docker creates this file in every container's root filesystem. */
    private static final String DOCKER_ENV_MARKER = "/.dockerenv";

    /**
     * Where the suite / test parameters come from. This class sits in the generic browser-factory layer, which
     * knows nothing of the project configuration, so the adapter ({@code DriverPoolManager}) installs a source
     * through {@link BrowserRegistry#useSuiteParameterSource}. With none installed there are no parameters and a
     * setting comes from its environment variable, system property or default.
     */
    private static volatile Function<String, String> suiteParameterSource = key -> null;

    static void useSuiteParameterSource(Function<String, String> source) {
        suiteParameterSource = source != null ? source : key -> null;
    }

    static Function<String, String> suiteParameterSource() {
        return suiteParameterSource;
    }

    private BrowserSecurityPolicy() {}

    /** Whether to pass {@code --no-sandbox}. */
    static boolean noSandbox() {
        boolean value = decide(explicit("noSandbox", "NO_SANDBOX"), inCiOrContainer());
        logger.debug("Browser --no-sandbox: {}", value);
        return value;
    }

    /** Whether the browser should accept invalid TLS certificates. */
    static boolean acceptInsecureCerts() {
        boolean value = decide(explicit("acceptInsecureCerts", "ACCEPT_INSECURE_CERTS"), !isProduction(environment()));
        logger.debug("Browser acceptInsecureCerts: {}", value);
        return value;
    }

    /** Whether Chrome's download protection (dangerous-file warnings) may be switched off. */
    static boolean relaxedDownloads() {
        boolean value = decide(explicit("relaxedDownloads", "RELAXED_DOWNLOADS"), !isProduction(environment()));
        logger.debug("Browser relaxedDownloads: {}", value);
        return value;
    }

    // ------------------------------------------------------------------
    // Pure decisions (unit-testable without touching the machine)
    // ------------------------------------------------------------------

    /** An explicit {@code true}/{@code false} wins; anything else (null, blank) falls back to {@code autoDefault}. */
    static boolean decide(String explicitValue, boolean autoDefault) {
        if (explicitValue == null || explicitValue.isBlank()) {
            return autoDefault;
        }
        return Boolean.parseBoolean(explicitValue.trim());
    }

    /** {@code prod} / {@code production}, case-insensitive. */
    static boolean isProduction(String environment) {
        return ConfigResolver.isProduction(environment);
    }

    /**
     * CI or container detection: a CI variable ({@code CI}, {@code JENKINS_URL},
     * {@code GITHUB_ACTIONS}), a Kubernetes pod ({@code KUBERNETES_SERVICE_HOST}), a container
     * runtime variable ({@code container}), or Docker's {@code /.dockerenv} marker file.
     */
    static boolean inCiOrContainer(Function<String, String> env, BooleanSupplier dockerEnvFileExists) {
        return isSet(env.apply("CI"))
                || isSet(env.apply("JENKINS_URL"))
                || isSet(env.apply("GITHUB_ACTIONS"))
                || isSet(env.apply("KUBERNETES_SERVICE_HOST"))
                || isSet(env.apply("container"))
                || dockerEnvFileExists.getAsBoolean();
    }

    private static boolean inCiOrContainer() {
        return inCiOrContainer(System::getenv, () -> java.nio.file.Files.exists(java.nio.file.Path.of(DOCKER_ENV_MARKER)));
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank() && !"false".equalsIgnoreCase(value.trim());
    }

    // ------------------------------------------------------------------
    // Setting lookup: suite <parameter> -> env var -> -D property
    // ------------------------------------------------------------------

    private static String explicit(String paramKey, String envKey) {
        return ConfigResolver.resolveString(paramKey, envKey, suiteParams(paramKey), null);
    }

    private static String environment() {
        // Both spellings: every workflow, suite XML and the Dockerfile say "env"; this used to look only for "environment".
        Map<String, String> params = suiteParams("environment");
        params.putAll(suiteParams("env"));
        return ConfigResolver.resolveEnvironment(params);
    }

    /** The suite/test parameters of the context bound to this thread (null-safe: none bound means none). */
    private static Map<String, String> suiteParams(String key) {
        Map<String, String> params = new HashMap<>();
        try {
            String value = suiteParameterSource.apply(key);
            if (value != null) {
                params.put(key, value);
            }
        } catch (RuntimeException e) {
            logger.debug("No suite parameter available for {}: {}", key, e.toString());
        }
        return params;
    }
}
