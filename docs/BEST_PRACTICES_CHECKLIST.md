# autoFrameX Best-Practices Checklist

Produced by the enterprise architecture review. Use this as a gate before the
milestones below — not a one-time read. See
[TECHNICAL_DEBT_REGISTER.md](TECHNICAL_DEBT_REGISTER.md) for the detailed status behind
each checked item.

## Before the next release ✅ Done (2026-07-15)

- [x] Encryption key fails fast with no fallback
- [x] CI can go red on a real test failure (`continue-on-error` removed except the one
      deliberately-expected-to-fail retry-engine step)
- [x] Java version consistent everywhere (17)
- [x] Secrets never reach logs unmasked (Logback masking wired in for both console and
      JSON file output)

## Before the next 5 teams onboard ✅ Done (2026-07-15)

- [x] One real, runnable example test exists end-to-end for each execution mode
      (`DataProviderExampleTest`, `TargetedExampleTest`, both verified passing)
- [x] Parallel execution has been proven under CI, not just documented
      (`testng-parallel-smoke.xml`, real driver reuse under contention confirmed)
- [x] CONTRIBUTING.md explaining "add your project" in under 10 steps
- [x] Docs match the actual dependency set (video tech, Java version corrected in README)

## Before claiming "enterprise-ready" 🟡 Partially done

- [x] Config/pool is no longer a JVM-wide singleton (context-keyed registry, verified
      with a real two-context parallel run)
- [ ] Multi-module split shipped, teams pull only what they need — **open**, deferred
      as its own planning pass (see roadmap)
- [x] SCA scanning exists (scheduled weekly + manual, not blocking every push — see
      the technical debt register for why it's not bound to every `verify`)
- [ ] SOLID composition replaces the inheritance chain — **open**, deferred as its own
      planning pass (see roadmap)

## Open-sourcing ⛔ Not applicable — decided 2026-07-15

The repo owner confirmed this framework is **not** going open-source. This section is
closed, not just deferred — none of the following is worth doing unless that changes:

- ~~LICENSE present, groupId reverse-DNS~~
- ~~No client-specific names in framework-core code or docs~~
- ~~Java LTS baseline~~ (already satisfied anyway — 17)
- ~~Repo curated — dead files removed, no IDE metadata, no generated artifacts checked in~~
  (the dead-file cleanup already happened as part of other fixes, independent of OSS intent)
