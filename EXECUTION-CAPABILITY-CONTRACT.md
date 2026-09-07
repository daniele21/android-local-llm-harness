# Validation Execution Capability Contract

Version: 0.4.0

Harnex adopts the repo-template-sw 0.10.0 model: **delivery stage**, **validation depth**, **execution capability** and **environment fidelity** are separate axes.

## Governing rules

> Automation executes automatable work; the user is not the fallback runner because an agent lacks Android/native tooling.

> Optimize for sufficient confidence per feedback time: cheap falsification at ITERATION, automated risk-based proof at INTEGRATION, residual real-environment proof at RELEASE.

> Reuse trusted equivalent evidence before starting another expensive run.

## Execution classes

- `AGENT_LOCAL` — deterministic gate executable by the current agent.
- `REMOTE_AUTOMATED` — deterministic/automatable but unavailable locally; repository automation owns it.
- `REAL_ENVIRONMENT` — genuinely depends on representative physical hardware, protected authority/environment or human judgement.

Gradle, Kotlin compilation, lint, unit tests, AndroidTest assembly, R8/package and native host tests never become `REAL_ENVIRONMENT` merely because the current agent lacks their toolchain.

## Delivery stages

- `ITERATION` — fast falsification; exact-head/full-diff/docs/preflight/release E2E are not defaults.
- `INTEGRATION` — coherent outcome ready for `dev`; exact head/base, full diff, affected docs, selected automated gates and affected automated critical E2E. Required physical/OEM evidence is non-blocking and `DEFERRED_TO_RELEASE`.
- `RELEASE` — `main`/release candidate; FULL plus release-critical evidence and every applicable required real-environment confirmation.

## Risk, evidence and identity

The native selector resolves changed owners and risks into concrete gates; `LEAN`, `SCOPED`, `STRONG`, `FULL` are shorthand. Public/shared contracts, Binder/Consumer boundaries, model/runtime lifecycle, persistence, native/JNI, manifest/package/R8/dependencies and selector/global-build changes retain stronger gates.

Reusable integration proof matches exact source HEAD/tree, material live base, required gates/profile and E2E environment/fidelity/media. Collaboration metadata alone does not invalidate proof. Post-merge tree-equivalent reuse is allowed only when the final tree and validated target base remain equivalent under repository policy. RELEASE remains exact-candidate/reference-grade.

## Failure diagnosis

Classify change regression, baseline, environment/toolchain, flaky, base drift or incorrect assumption before patching. Record evidence, hypothesis, discriminating experiment and result when useful. Each failed repair needs a new falsifiable hypothesis; after two failed repairs with the same signature, change strategy and obtain new evidence before a third repair. Never weaken a legitimate gate to obtain green.

Missing deterministic automation is `AUTOMATION_CAPABILITY_GAP`; unsafe scope classification is `VALIDATION_SCOPE_GAP`. A missing required real-environment confirmation blocks the release claim rather than being relabeled as automated proof.

## Agent-facing summary

The selector/CI surface reports bounded stage, source identity (`head`, tree, base, dirty), risks/profile, required gates with reasons/executor/status, evidence references, remaining gaps and next action. `PENDING` and `FAIL` are never omitted to fit the summary. The summary is derived; it does not verify evidence or authorize reuse by itself.

## Security

Require trusted requesters, exact-head pinning for new runs, same-repository PRs by default, least privilege, no production/signing/deployment secrets during change-branch execution and bounded privacy-safe evidence retention.
