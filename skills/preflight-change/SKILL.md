---
name: preflight-change
description: Establish exact-head automated-validation readiness by resolving material ambiguity, verifying target-base freshness, reviewing the complete diff, proving durable documentation is current, selecting validation depth and E2E environment fidelity from blast radius, classifying execution capability and routing deterministic gates without turning the user into a test runner.
---

# Preflight Change

Use immediately before publishing/updating a PR. `validate-change` owns iterative validation; this Skill owns final documentation freshness, blast-radius selection, E2E fidelity selection, execution routing and readiness.

Read `EXECUTION-CAPABILITY-CONTRACT.md` when execution capability matters. Read `.engineering/e2e.json` for complete workflow/device/runtime/environment claims. Read `docs/README.md` when documentation ownership or README impact is unclear.

Governing rules:

> Validation depth follows blast radius: use the narrowest profile that proves the changed invariants.

> Code and durable documentation ship together: every affected canonical owner must describe the exact-head behavior being published.

> README identity and usage are separate owners: do not rewrite stable mission/positioning for a usage-only change and do not leave stale setup/run/configuration/public examples.

> E2E environment fidelity follows the claim: use the cheapest declared automated environment that represents the material target dimensions, then leave only irreducible physical/device gaps for real-environment confirmation.

> An automatable deterministic gate must not be delegated to the user merely because the current agent cannot run it locally.

## 1. Resolve ambiguity, base and diff

- Resolve material ambiguity from canonical contracts/code/docs/ADRs/consumers/tests; ask only when alternatives materially change behavior, contracts, persistence, security/privacy, lifecycle, compatibility, acceptance criteria or meaningful UX.
- Record exact feature HEAD and intended `dev` base. Refresh affected evidence after any edit/rebase/dependency/base movement.
- Review the complete diff for unrelated/generated/private files, weakened tests, duplicated ownership, stale docs/contracts, missed consumers, resource/security/UX drift and stale E2E target/environment assumptions.

## 2. Assess documentation impact

Assess observable behavior, not filenames alone. Classify each plausible owner as `UPDATED` or `N/A`, with a short reason where impact was plausible but `N/A`:

- `README_IDENTITY` — purpose, audience/outcome, stable positioning;
- `README_USAGE` — prerequisites, setup/run, public configuration, API/UI usage and examples;
- `FEATURE_DOCS` — durable non-obvious feature behavior/constraints/evidence;
- `ARCHITECTURE` — boundaries/ownership;
- `ADR` — durable decision/rationale;
- `SECURITY_DATA` — trust/privacy/security/data lifecycle;
- `OPERATIONS` — canonical command/operational semantics;
- `PRODUCT_EXPERIENCE` — `design/*` contracts;
- `CURRENT_STATE` — integrated/blocker/next repository truth.

README identity is not rewritten merely because an implementation, feature, command or default changes. README usage updates in the same change whenever the existing prerequisites/setup/run/configuration/API/UI/examples become incomplete, wrong, removed, newly mandatory or misleading.

Existing feature docs update in the same change as the durable behavior they describe. Create a new feature doc only when non-obvious behavior is not sufficiently discoverable from public contracts, tests, code, architecture or an existing focused owner.

If an affected owner is stale, `DOCS_CURRENT_WITH_IMPLEMENTATION: FAIL` and publication readiness is blocked.

## 3. Select validation depth

Run the project selector from `.engineering/commands.json` using `auto` and record `LEAN | SCOPED | STRONG | FULL`, reason and affected modules/jobs.

Harness guidance:

- `LEAN` — docs/governance/metadata-only and cheap repository guards.
- `SCOPED` — contained module implementation plus affected compile/unit/lint/direct-consumer evidence.
- `STRONG` — shared/public contracts, Binder/control-plane boundaries, persistence, native/JNI, manifest, dependency, R8/ProGuard, packaging/variant or other cross-boundary/release-sensitive behavior.
- `FULL` — promotion/release, selector/CI/global Gradle/module inventory/toolchain changes, unknown executable paths or explicit full request.

Unknown executable scope fails safe stronger. Selector/build-inventory changes force `FULL`. Do not silently downgrade below `auto`; stronger validation is allowed.

## 4. Select E2E journey and fidelity

When the profile/claim requires E2E, read `.engineering/e2e.json` and choose the smallest affected journey plus cheapest sufficient automated environment.

Harness mappings:

- `binder-contract-serialization` -> `binder-api35-emulator` / `simulated_or_emulated`; proves Android Binder Parcelable contract behavior only.
- `local-inference-device-lifecycle` -> current automated environment gap; production ARM64 JNI/llama.cpp, real GGUF loading, memory reclamation and physical thermal/OEM behavior remain `REAL_ENVIRONMENT` evidence on `physical-arm64-device`.

Do not promote API35 x86_64 emulator evidence into ARM64/native/model/resource claims. Built/installable artifact evidence may prove package/install behavior while still remaining emulator fidelity for hardware-dependent claims.

If a final physical run repeatedly finds ordinary workflow failures that a practical automated environment could reproduce, strengthen the automated journey instead of accepting device validation as the first complete-system test.

## 5. Classify execution capability

Assign every selected gate to:

- `AGENT_LOCAL` — executable by the current agent on exact HEAD;
- `REMOTE_AUTOMATED` — deterministic/automatable but unavailable here;
- `REAL_ENVIRONMENT` — genuinely requires representative hardware, protected authority/external environment or manual evidence.

Gradle, Kotlin compilation, Lint, R8, unit tests, unsigned Android builds and emulator instrumentation are `REMOTE_AUTOMATED`, not `REAL_ENVIRONMENT`, when ChatGPT lacks Android tooling. Executor class never upgrades environment fidelity.

Run all local gates. If selected deterministic gates remain remote and semantic/base/diff/documentation checks pass, status becomes `READY_FOR_REMOTE_PREFLIGHT` and control passes to `skills/remote-preflight/SKILL.md`; do not ask the user to run Gradle.

## 6. Failure discipline and parity

Classify failures as `CHANGE_REGRESSION`, `BASELINE_FAILURE`, `ENVIRONMENT`, `FLAKY`, `BASE_DRIFT` or `ASSUMPTION`. Identify violated invariant and owner before editing. Never suppress/weaken a legitimate gate merely to go green. If the same gate fails after a repair, form a new falsifiable hypothesis before another edit.

Re-run documentation impact, blast-radius selection and E2E fidelity after material fixes because the repair may alter durable behavior or broaden scope.

Local and remote automation must invoke the same project-owned commands/scripts/selector semantics. If remote runs are routinely broader than required, improve the selector rather than making every PR full.

## Output

```text
HEAD: <revision>
TARGET: <branch>@<revision>
AMBIGUITY: PASS|FAIL
BASE_FRESHNESS: PASS|FAIL
FULL_DIFF_REVIEW: PASS|FAIL
DOCUMENTATION_IMPACT:
  README_IDENTITY: UPDATED|N/A <reason when useful>
  README_USAGE: UPDATED|N/A <reason when useful>
  FEATURE_DOCS: UPDATED|N/A <reason when useful>
  ARCHITECTURE: UPDATED|N/A <reason when useful>
  ADR: UPDATED|N/A <reason when useful>
  SECURITY_DATA: UPDATED|N/A <reason when useful>
  OPERATIONS: UPDATED|N/A <reason when useful>
  PRODUCT_EXPERIENCE: UPDATED|N/A <reason when useful>
  CURRENT_STATE: UPDATED|N/A <reason when useful>
DOCS_CURRENT_WITH_IMPLEMENTATION: PASS|FAIL
VALIDATION_PROFILE: LEAN|SCOPED|STRONG|FULL
PROFILE_REASON: <reason>
EXECUTION_CAPABILITY: local|mixed|remote-only
E2E_JOURNEYS:
  <journey>: <environment-id> / <fidelity-class> / PASS|FAIL|PENDING|N/A
E2E_RESIDUAL_GAPS:
  <journey>: <gap or N/A>
AGENT_LOCAL:
  <gate>: PASS|FAIL|N/A
REMOTE_AUTOMATED:
  <gate>: PASS|FAIL|PENDING|N/A
REAL_ENVIRONMENT:
  <gate>: PASS|PENDING|N/A
READINESS: READY_FOR_CI|READY_FOR_REMOTE_PREFLIGHT|AUTOMATED_PREFLIGHT_CONFIRMED|NOT_READY_FOR_AUTOMATED_PREFLIGHT
```

Documentation must be current for every ready state. Any later material edit/rebase/base/environment change invalidates affected evidence and requires documentation impact plus fidelity selection to be rechecked.
