# Validation Execution Capability Contract

Version: 0.3.0

This repository adopts the repo-template-sw 0.9 delivery model: **delivery stage**, **validation depth**, **execution capability** and **environment fidelity** are separate axes.

## Governing rules

> Automation executes automatable work; the user is not the fallback runner because an agent lacks Android/native tooling.

> Optimize for sufficient confidence per feedback time: run the cheapest useful evidence at ITERATION, expand by risk at INTEGRATION, and use release-grade evidence at RELEASE.

> Reuse equivalent successful evidence before dispatching another expensive run.

## Execution classes

- `AGENT_LOCAL` — current agent can execute the deterministic gate directly.
- `REMOTE_AUTOMATED` — deterministic/automatable but unavailable locally; repository automation executes it.
- `REAL_ENVIRONMENT` — genuinely depends on representative physical hardware, protected authority/environment or manual judgement.

Gradle, Kotlin compilation, Lint, unit tests, AndroidTest assembly, R8/package and native host tests never become `REAL_ENVIRONMENT` merely because the current agent lacks their toolchain.

## Delivery stages

- `ITERATION` — fast falsification; no exact-head/full-diff/docs/preflight/release E2E by default.
- `INTEGRATION` — coherent observable outcome ready for `dev`; exact head/live base, full diff, affected docs, selected risk gates and affected critical E2E.
- `RELEASE` — `main`/release candidate; FULL plus release-critical and residual environment evidence.

A draft collaboration PR may remain ITERATION. A ready PR to `dev` is INTEGRATION.

## Risk -> gates -> profile

The selector reports risk dimensions and concrete required gates. `LEAN`, `SCOPED`, `STRONG`, `FULL` are shorthand summaries, not immutable suite bundles.

Typical Harnex escalation signals include public/shared contracts, Binder/Consumer boundaries, model/runtime lifecycle, persistence, native/JNI, manifest/package/R8/dependencies and selector/global-build changes. FULL is expected for release and selector/global-build/unknown scope, not every ordinary feature.

## Evidence reuse

An automated result can be reused when its identity remains sufficient for the current exact source HEAD, live target-base relationship, required gates/profile and material E2E environment/evidence claim.

PR recreation, draft/ready changes or comments alone do not invalidate source evidence. Source edits, material base/dependency changes, changed required gates or stronger environment evidence do.

`/preflight` must search reusable evidence first and dispatch only missing/stale/insufficient work.

## Security

Remote execution remains trusted-requester, same-repository and exact-head pinned. Change-branch code must not gain production/signing/deployment secrets or unnecessary write credentials. Reporting permission should remain separate from code execution where practical.

## Failure loop

Inspect logs, classify change regression/baseline/environment/flaky/base drift/assumption, identify the owning invariant, repair it and reselect risks/gates. Never downgrade or suppress a legitimate gate to obtain green status.
