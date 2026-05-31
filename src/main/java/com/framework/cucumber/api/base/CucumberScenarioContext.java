package com.framework.cucumber.api.base;

/**
 * Base class for PicoContainer-injected scenario state objects.
 *
 * <h3>Purpose</h3>
 * <p>PicoContainer creates exactly <strong>one instance per scenario</strong>
 * and injects that same instance into every step-definition class that declares
 * it (or a subclass) as a constructor parameter. This lets {@code @When} and
 * {@code @Then} classes share state without static fields or thread-locals.
 *
 * <h3>How to use</h3>
 * <ol>
 *   <li>Create a project-specific context that extends this class and adds
 *       the fields your scenario needs:
 *   <pre>
 *   public class IncidentScenarioContext extends CucumberScenarioContext {
 *       public final IncidentService incidentService = new IncidentService();
 *       public String createdSysId;
 *       public String createdIncidentNumber;
 *   }
 *   </pre>
 *   </li>
 *   <li>Declare it as a constructor parameter in every step class that needs it:
 *   <pre>
 *   public class IncidentRequestSteps {
 *       private final IncidentScenarioContext ctx;
 *       public IncidentRequestSteps(IncidentScenarioContext ctx) { this.ctx = ctx; }
 *   }
 *   </pre>
 *   </li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>PicoContainer creates a fresh context per scenario, so parallel scenario
 * execution never shares state through this object. All fields are written by
 * one step and read by a later step in the same scenario — no cross-scenario
 * contention.
 *
 * <h3>What to put here vs in the subclass</h3>
 * <ul>
 *   <li>This base class intentionally has no fields — it is a marker/contract.</li>
 *   <li>Put scenario-specific service instances and extracted response values
 *       in your subclass.</li>
 *   <li>Do NOT put WebDriver state here — that is managed by
 *       {@link CucumberProjectBase}.</li>
 * </ul>
 *
 * @see CucumberProjectBase
 */
public abstract class CucumberScenarioContext {
    // Intentionally empty — subclasses add scenario-specific fields.
}
