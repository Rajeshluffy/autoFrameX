package com.framework.testng;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.testng.annotations.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural guardrails enforced automatically, not just documented.
 *
 * <p>Both rules here exist because of a real problem found (and fixed) during the
 * 2026-07 architecture review — see {@code docs/TECHNICAL_DEBT_REGISTER.md} and
 * {@code docs/CODING_STANDARDS.md}. Run as an ordinary TestNG test
 * ({@code com.tngtech.archunit:archunit} core artifact, not {@code archunit-junit5} —
 * this project uses TestNG).
 */
public class ArchitectureRulesTest {

    private static final JavaClasses IMPORTED_CLASSES =
            new ClassFileImporter().importPackages("com.framework", "design.patterns");

    /**
     * {@code com.framework.utils} imported {@code com.framework.testng.api.base.TestMetadata}
     * until 2026-07-15, creating a real cycle: {@code utils → testng.api.base →
     * selenium.api.base → utils} (via {@code Reporter} importing it while
     * {@code SeleniumBase} extended {@code Reporter} and {@code ProjectSpecificMethods}
     * extended {@code SeleniumBase}). Fixed by moving {@code TestMetadata}/
     * {@code AccountData} into {@code utils} itself. This rule keeps it fixed.
     */
    @Test
    public void utilsMustNotDependOnTestngApiBase() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.framework.utils..")
                .should().dependOnClassesThat().resideInAPackage("com.framework.testng.api.base..")
                .because("this created a real circular package dependency once "
                        + "(utils -> testng.api.base -> selenium.api.base -> utils), "
                        + "fixed 2026-07-15 by moving TestMetadata/AccountData into utils");

        rule.check(IMPORTED_CLASSES);
    }

    /**
     * {@code com.framework.utils.Reporter} imported {@code design.patterns.object.pool.DriverPoolManager}
     * directly (bind/init/shutdown calls in its TestNG lifecycle hooks), and
     * {@code com.framework.observability.FailureCategorizer} imported
     * {@code com.framework.selenium.exception.ElementNotFoundException} (a single
     * {@code instanceof} check), until 2026-07-15 — both would have made the
     * framework's core module depend on its selenium module once split (TD-20),
     * which already needs to depend on core: a hard cycle. Fixed by moving the
     * pool bind/init/shutdown calls down into {@code SeleniumBase}'s own
     * {@code @BeforeTest}/{@code @AfterSuite} hooks, and by having
     * {@code ElementNotFoundException} implement {@code com.framework.exception.Categorized}
     * instead of being matched by type. This rule keeps both fixes in place.
     */
    @Test
    public void coreMustNotDependOnSeleniumOrPool() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.framework.utils..",
                        "com.framework.observability..",
                        "com.framework.config.data..",
                        "com.framework.exception..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.framework.selenium..",
                        "com.framework.testng..",
                        "design.patterns.object.pool..",
                        "design.patterns.factory.browser..")
                .because("the framework's core module (TD-20's multi-module split) must "
                        + "never depend on the selenium module — fixed 2026-07-15 by moving "
                        + "Reporter's pool bind/init/shutdown into SeleniumBase, and by "
                        + "introducing com.framework.exception.Categorized so FailureCategorizer "
                        + "doesn't need to import ElementNotFoundException");

        rule.check(IMPORTED_CLASSES);
    }

    /**
     * Page objects are exactly the layer where a downstream team is most likely to
     * accidentally reintroduce shared mutable state (a memoized selector, a shared
     * counter) — the same class of bug the {@code ConfigManager}/{@code DriverPoolManager}
     * singleton-to-registry fix addressed at the framework level. {@code static final}
     * constants and {@code ThreadLocal} fields are fine; anything else static is not.
     */
    @Test
    public void pageObjectsMustNotDeclareMutableStaticState() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Page")
                .or().haveSimpleNameEndingWith("PageObject")
                .should(new NoMutableStaticFields())
                .because("shared mutable static state in a page object breaks under "
                        + "parallel execution the same way the framework's own "
                        + "pre-2026-07-15 config/pool singletons did");

        rule.check(IMPORTED_CLASSES);
    }

    private static final class NoMutableStaticFields extends ArchCondition<JavaClass> {

        NoMutableStaticFields() {
            super("not declare mutable static fields");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            for (Field field : javaClass.reflect().getDeclaredFields()) {
                int mods = field.getModifiers();
                if (!Modifier.isStatic(mods)) continue;
                if (Modifier.isFinal(mods)) continue;
                if (ThreadLocal.class.isAssignableFrom(field.getType())) continue;

                String message = String.format(
                        "Field <%s.%s> is a mutable static field — make it an instance "
                        + "field, a ThreadLocal, or add 'final'",
                        javaClass.getName(), field.getName());
                events.add(SimpleConditionEvent.violated(field, message));
            }
        }
    }
}
