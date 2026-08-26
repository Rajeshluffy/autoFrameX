# docs/ index

Tagged by what kind of document each one is, so a new reader knows what to trust as
current vs. what's a point-in-time record. Added as part of the enterprise architecture
review (2026-07-14/15) — see [ARCHITECTURE.md](ARCHITECTURE.md) for the review itself.

## Reference — living guidance, kept up to date

| File | Covers |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Package/dependency/class diagrams, recommended module structure |
| [TECHNICAL_DEBT_REGISTER.md](TECHNICAL_DEBT_REGISTER.md) | Item-by-item debt status (fixed vs. open) |
| [ARCHITECTURE_REVIEW_ROADMAP.md](ARCHITECTURE_REVIEW_ROADMAP.md) | Phased plan for the review's remaining open items |
| [CODING_STANDARDS.md](CODING_STANDARDS.md) | Rules to keep fixed debt from creeping back in |
| [BEST_PRACTICES_CHECKLIST.md](BEST_PRACTICES_CHECKLIST.md) | Milestone gates (release / onboarding / "enterprise-ready" / OSS) |
| [DEVOPS_INTEGRATION_GUIDE.md](DEVOPS_INTEGRATION_GUIDE.md) | How downstream projects reuse the Maven/CI artifacts |
| [PAGE_OBJECT_PATTERN.md](PAGE_OBJECT_PATTERN.md) | `BasePage` usage patterns |
| [ELEMENT_LOCATION_PATTERN.md](ELEMENT_LOCATION_PATTERN.md) | Exception-based `locateElement()` contract |
| [CUCUMBER_DEPENDENCY_INJECTION_PATTERN.md](CUCUMBER_DEPENDENCY_INJECTION_PATTERN.md) | PicoContainer scenario-context pattern |
| [QUICK_START_PATTERNS.md](QUICK_START_PATTERNS.md) | Onboarding quick-start |
| [Browser.md](Browser.md) | Browser/WebDriver layer (interface, factory, pool) |
| [Utilities Layer.md](Utilities%20Layer.md) | Utility class catalogue |
| [INTERVIEW_GUIDE.md](INTERVIEW_GUIDE.md) | Interview-prep reference material |

## Historical / changelog — point-in-time record, not necessarily current

| File | What it recorded |
|---|---|
| [FRAMEWORK_IMPROVEMENT_ROADMAP.md](FRAMEWORK_IMPROVEMENT_ROADMAP.md) | Earlier phase-based pattern-documentation roadmap — different scope from `ARCHITECTURE_REVIEW_ROADMAP.md` above, don't confuse the two |
| [PHASE1_COMPLETION_SUMMARY.md](PHASE1_COMPLETION_SUMMARY.md) | Snapshot of Phase 1 completion for that earlier roadmap |
| [Frame Work.md](Frame%20Work.md) | The review prompt template that produced this review — not framework documentation itself |

## Fragment — excerpted sub-sections, unclear standalone context

These read like extracted numbered sections (`2.6`, `2.11`, ...) from a larger source
document rather than complete, self-contained docs — treat with more caution than the
Reference tier above until someone confirms whether they're current.

| File | Apparent topic |
|---|---|
| [Logging.md](Logging.md) | Logging stack (SLF4J + Logback) |
| [performance.md](performance.md) | Performance testing tools (JMeter) |
| [devops.md](devops.md) | CI/CD strategy — titled "Enterprise Test Observability & Analytics Platform (v2)" internally, possibly an earlier draft of [enterprise_test_observability_platform.md](enterprise_test_observability_platform.md) |
| [enterprise_test_observability_platform.md](enterprise_test_observability_platform.md) | Target observability platform vision (Allure/Prometheus/Grafana) — aspirational, not all of it is implemented today |

## Not documentation

- [gen_word.py](gen_word.py) — a script, not a doc; kept here only because it presumably
  generates one of the files above.
