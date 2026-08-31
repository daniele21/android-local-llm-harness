---
name: validate-change
description: Select the narrowest sufficient validation for a change while iterating, diagnose failures at their owning invariant, and identify the correct final gate by blast radius without confusing unavailable agent-local execution with a human testing requirement or confusing emulator evidence with target-environment evidence.
---

# Validate Change

## Principle

Do not run the entire repository for every edit, and do not stop at a local unit test when a shared contract, runtime boundary or critical user experience changed. Validation follows blast radius and the strength of the claim.

Use `.engineering/commands.json` as the canonical repository-level command routing surface. Read `.engineering/e2e.json` when a complete workflow, platform/device/browser/runtime assumption or environment-dependent claim is affected. When `product-ui` is adopted and user-facing behavior changes, also read `design/ux-contract.json` and `design/brand-kit.json`.

This Skill owns iterative validation selection. `preflight-change` owns final exact-head execution classification/readiness. `remote-preflight` owns deterministic remote execution when the current agent lacks an equivalent local environment.

## Validation ladder

### Level A — local iteration

Use for private implementation inside one owner:

- formatter/linter for touched surface;
- focused unit/component tests;
- module/package compile or typecheck.

Run these directly when the current agent has the required environment. If not, record the gate as a candidate `REMOTE_AUTOMATED` gate for preflight rather than asking the user to run it by default.

### Level B — direct consumers

Add when a contract or behavior affects known callers/adapters:

- direct consumer tests;
- contract/fake compatibility;
- persistence/migration tests if applicable;
- affected UI/transport compilation and component-state tests.

### Level C — integration/repository

Add for public contracts, multiple domains, build/configuration, CI/tooling or broad dependency changes:

- canonical `check` command;
- canonical `test` command or relevant scoped subset;
- integration/contract tests;
- canonical `build` when build/runtime/package behavior may be affected;
- repository/operating/E2E-fidelity/product-experience health checks as applicable.

### Level D — end-to-end/product flow

Add when the claim crosses a complete user/system workflow boundary and lower-level tests cannot establish the final outcome:

- canonical `e2e` command or smallest relevant critical-journey subset;
- complete workflow assertion through the real public/UI/protocol boundary;
- the cheapest declared automated environment in `.engineering/e2e.json` that can truthfully prove the changed claim;
- fidelity escalation when the claim depends on a device/platform/runtime/artifact dimension missing from the cheaper environment;
- built/package artifact execution when the claim depends on distributable behavior and this is technically practical;
- zero-residue cleanup of app/device/test state owned by the run;
- bounded failure evidence with build/run/environment identity and declared fidelity class.

Execution capability and environment fidelity are separate. `REMOTE_AUTOMATED` says where/who executed the gate; fidelity classes say what environment claim the evidence supports. Never treat a green Android emulator run as physical-device/ARM64/thermal evidence.

For Harness specifically, `binder-api35-emulator` may prove the declared Binder contract journey at `simulated_or_emulated` fidelity. It does not prove the `local-inference-device-lifecycle` journey, which retains an explicit automation gap for production ARM64 JNI/llama.cpp, real GGUF, memory reclamation and thermal/OEM behavior.

Do not require E2E for every change. Prefer unit/integration coverage when it can prove the same invariant more deterministically and cheaply.

### Level E — real environment / representative evidence

Required only for claims ordinary deterministic automation cannot truthfully prove or where `.engineering/e2e.json` declares residual confirmation:

- physical Android device behavior;
- production ARM64 JNI/llama.cpp execution with a real model;
- memory reclamation/unified/GPU footprint under representative hardware conditions;
- performance/thermal characteristics;
- protected signing/release behavior when credentials must not be available to automation;
- representative-user usability or assistive-technology evidence when the UX claim requires it.

Do not place ordinary Gradle compile, R8, lint, unit, deterministic integration or unsigned build tasks here merely because the current agent lacks Android tooling. Those are `REMOTE_AUTOMATED` when they cannot run agent-local.

The target-environment run should primarily confirm residual fidelity gaps that could not be reproduced earlier. If it repeatedly discovers ordinary workflow failures that could have been automated, strengthen the declared automated E2E environment/journey instead of normalizing the device test as the first whole-system check.

## E2E environment fidelity

When Level D or E is relevant, use `.engineering/e2e.json` to answer:

1. Which critical journey owns the changed outcome?
2. Which target dimensions are material to the claim?
3. Which declared automated environment is the cheapest one that represents those dimensions strongly enough?
4. Which fidelity gaps remain and therefore still require physical/target confirmation?

Prefer only as needed:

```text
lower-level tests
-> automated E2E
-> built/installable artifact E2E when material
-> highest practical automated fidelity
-> residual physical/target confirmation
```

If no automated environment can exercise a required critical journey, preserve the explicit `automation_gap_reason` from `.engineering/e2e.json` and report it. Do not silently convert the workflow into an undocumented manual test.

## Product experience validation

When `product-ui` is adopted and a change affects user-facing behavior, validate the experience properties actually changed rather than only visual appearance. Preserve user outcome/task, journey/hierarchy, progressive disclosure/defaults, critical states/recovery, accessibility/adaptive behavior, semantic component/token ownership and purposeful motion/graphics at the depth the change affects.

A screenshot can support a visual claim but cannot by itself prove interaction, accessibility, recovery, adaptive behavior or usability.

## Smoke vs E2E

A build passing is not equivalent to the built artifact working, and smoke is not equivalent to E2E.

- `smoke` proves minimal install/start/launch/viability where applicable;
- `e2e` proves a complete critical workflow outcome across the assembled system.

Use both only when both claims matter.

## Failure diagnosis

A red gate must be understood before it drives another code edit. Classify it as current-change regression, baseline/pre-existing failure, environment/toolchain/dependency issue, flaky/non-deterministic behavior, stale-base integration effect or incorrect requirement/design/contract assumption.

Identify the violated invariant and owner. Never weaken/delete/suppress a legitimate failing test merely to make the change green. Repeated failure after a repair requires a new hypothesis.

## Operational validation

When runtime/build/package/E2E/lifecycle behavior changes, validate applicable operating invariants: unique build identity, immutable successful artifact promotion, manifest/checksum/build delta/retention, cleanup of owned device/test/temp state, bounded privacy-safe evidence, truthful environment/fidelity identity and no promotion of failed/partial artifacts.

## Workflow

1. Identify changed owner, user-visible impact and public blast radius.
2. Read the nearest agent guide and `.engineering/commands.json`; read `.engineering/e2e.json` when a complete workflow or environment-dependent claim is relevant; read design contracts for meaningful UI work.
3. Run the cheapest deterministic gate that can falsify the current edit when the current agent can execute it.
4. On failure, classify cause and owner before editing again.
5. Expand only when the change crosses a boundary or is ready for final integration.
6. Use E2E only when the full product/system outcome is part of the claim; select the declared critical journey and cheapest sufficient environment fidelity.
7. Escalate E2E fidelity only when target dimensions materially affect the claim; preserve residual real-environment evidence separately.
8. If a deterministic gate cannot run in the current agent environment, mark it for `REMOTE_AUTOMATED` routing; do not default to asking the user to execute it.
9. Report exact validation executed, E2E environment/fidelity used and evidence still pending.
10. Before publication, hand accumulated evidence to `preflight-change`.

## Output

Distinguish `PASS`, `FAIL`, `PENDING` and `N/A`. Also record whether a pending gate is `REMOTE_AUTOMATED` or `REAL_ENVIRONMENT`. For E2E evidence, record the `.engineering/e2e.json` environment ID/fidelity class and residual gaps. Absence of agent-local execution is not evidence that a user must run the gate.
