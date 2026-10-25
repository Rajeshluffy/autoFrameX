package com.framework.cucumber.api.base;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

import io.cucumber.core.cli.Main;
import io.cucumber.java.After;
import io.cucumber.java.Before;

/**
 * Cucumber-JVM refuses to load a step class that extends a class which defines hooks ("You're not allowed to
 * extend classes that define Step Definitions or hooks"). {@code CucumberProjectBase} defined the lifecycle hooks
 * itself, so the pattern its Javadoc and the README teach, {@code class LoginSteps extends CucumberProjectBase},
 * failed as soon as Cucumber loaded the glue, and none of the setup it promised (driver, app URL, API capture,
 * screenshot on failure) could run. The hooks now live in {@link CucumberHooks}, a concrete glue class.
 *
 * <p>No browser: a dry run loads and checks the glue without executing a hook or a step.
 */
public class CucumberGlueTest {

	@Test
	public void aStepClassMayExtendCucumberProjectBase() {
		byte status = Main.run(new String[] {
				"--dry-run",
				"--glue", "glueprobe",
				"--glue", "com.framework.cucumber.api.base",
				"--plugin", "null_summary",
				"classpath:glueprobe/probe.feature" }, Thread.currentThread().getContextClassLoader());

		Assert.assertEquals(status, 0, "Cucumber loaded a step class that extends CucumberProjectBase");
	}

	@Test
	public void theBaseClassDefinesNoHooksSoItCanBeExtended() {
		for (Method method : CucumberProjectBase.class.getDeclaredMethods()) {
			Assert.assertFalse(method.isAnnotationPresent(Before.class) || method.isAnnotationPresent(After.class),
					"a hook on " + method.getName() + " would make every subclass unloadable");
		}
	}

	@Test
	public void theLifecycleHooksAreOnTheGlueClass() {
		long before = Arrays.stream(CucumberHooks.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Before.class)).count();
		long after = Arrays.stream(CucumberHooks.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(After.class)).count();

		Assert.assertEquals(before, 1L, "one @Before: acquire the driver and open the app");
		Assert.assertEquals(after, 1L, "one @After: screenshot, API capture, return the driver");
		Assert.assertFalse(java.lang.reflect.Modifier.isAbstract(CucumberHooks.class.getModifiers()),
				"glue classes must be concrete for Cucumber to instantiate them");
	}
}
