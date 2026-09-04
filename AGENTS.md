# Harnex — Coding Agent Guide

Harnex is an Android local-AI harness. This guide owns routing and durable repository invariants; architecture/current work belong to their canonical docs.

## Read only what the task requires

Always read this file, then only the closest scoped `AGENTS.md`, owning code/contracts/tests and relevant canonical docs. Read:

- `.engineering/commands.json` for delivery stage, validation/execution/build routing;
- `.engineering/e2e.json` for complete-workflow/environment claims;
- `skills/validate-change/SKILL.md` during implementation;
- `skills/preflight-change/SKILL.md` when a coherent outcome becomes integration/release-ready;
- `skills/remote-preflight/SKILL.md` only for required deterministic gates unavailable locally;
- `design/*` + `design-product-experience` for meaningful product UI work.

Do not ingest every workstream or run release-grade validation for every edit.

## Durable invariants

- Local-first/privacy-first behavior: do not add silent cloud inference or content logging.
- Model/runtime/Binder state has one canonical owner; do not duplicate lifecycle/policy in UI or adapters.
- JNI/native handles, model resources, jobs, processes and temporary evidence are bounded, owned, cancellable and cleaned on every applicable exit path.
- Public Binder/Consumer contracts require direct-consumer compatibility evidence.
- Emulator evidence never becomes ARM64/native/GGUF/memory/thermal/OEM evidence by implication.
- Build/package identity and immutable successful artifact semantics remain truthful.
- UI follows user task, hierarchy, progressive disclosure, accessibility/adaptive behavior and canonical design tokens/components.

## Ownership routing

Start from the owning module before editing consumers. Important cross-boundary areas include:

- `core/contracts`, `core/backend-spi`, `core/runtime-core` — public/runtime contracts;
- `models/model-store` and control-plane stores — model/lifecycle truth;
- `transports/android-binder-*` — Binder protocol/client contract;
- `integrations/android-service-host` — Host/process boundary;
- `apps/shared-runtime-client-consumer-fixture` — real Consumer compatibility fixture;
- `backends/llama-cpp` + `third_party/llama.cpp` — native backend/JNI;
- `apps/local-llm-console` and phone surfaces — product UI/diagnostics.

Inspect direct consumers/fakes/tests before changing a shared boundary.

## Delivery model

Delivery stage and validation depth are separate.

### ITERATION — default

Use while implementation is changing, including draft collaboration PRs.

Goal: fast falsification. Prefer formatter/static checks, affected compile, focused unit/component tests and only directly implicated contract tests. Exact-head publication evidence, full-diff review, durable-doc freshness, remote preflight, packaging/R8/emulator/physical E2E are not default iteration requirements.

### INTEGRATION

Use when a coherent **observable user/system outcome** is ready to converge into `dev` or a PR is ready for merge/review.

Now refresh live `dev` base/head, review the complete diff, make affected durable docs current, select risks -> required gates, run/route deterministic evidence and add only affected critical E2E.

### RELEASE

`dev -> main` / release-candidate work is RELEASE. Use FULL plus release-critical artifact/E2E and residual physical evidence required by the claim.

## Validation model

The selector reports:

`outcome -> risk dimensions -> required gates -> LEAN|SCOPED|STRONG|FULL -> executor`.

Profiles are shorthand, not fixed giant suites. FULL is exceptional for ordinary feature work and expected for release, selector/global-build/toolchain/unknown-scope changes.

Draft PRs may run ITERATION. A ready PR to `dev` runs INTEGRATION. `main` promotion runs RELEASE.

When deterministic work cannot run locally, use repository-owned remote automation; never make the user the fallback Gradle runner.

## Evidence reuse

Before dispatching remote preflight, reuse successful evidence when it still matches exact source HEAD, live target base, sufficient profile/required gates and material E2E identity.

PR recreation, draft/ready state or comments alone do not invalidate source evidence. Source edits, material base/dependency changes, changed required gates or stronger E2E requirements do.

Do not run automatic PR `Validate` and then repeat the same expensive validation merely because `/preflight` was requested.

## E2E / fidelity

Use the cheapest declared automated environment sufficient for the claim.

UI evidence modes:

- `ASSERTIONS` — UI is incidental to deterministic system behavior;
- `SCREENSHOTS` — stable layout/hierarchy/copy/state/recovery/adaptive outcome matters;
- `FULL_MEDIA` — motion, timing/progression, navigation/transition sequence, lifecycle visibility or release acceptance is part of the claim.

UI presence alone does not force video.

Harnex examples:

- Binder serialization/two-APK emulator journeys prove simulated Android/Binder behavior, not production ARM64 native inference.
- `phone-cold-start` normally requires screenshots, not continuous video.
- production llama.cpp/GGUF/memory/thermal/OEM claims retain explicit physical-device evidence.

## Parallel development

Plan work as vertical observable outcomes. Technical layers are subtasks unless independently valuable/mergeable/reviewable.

Agents may work on temporary parallel branches with non-conflicting ownership, but converge early onto a coherent feature/integration branch. Parallel work does not imply a stacked publication chain. Use stacked PRs only when each level genuinely needs independent review/publication; pure stack-sync PRs are a smell.

## Documentation

`docs/current-state.md` describes integrated/blocked/next repository truth, not branch-by-branch activity. Active plans are bounded and disposable.

During ITERATION durable docs may remain pending while behavior changes. At INTEGRATION every affected canonical owner must describe the exact candidate behavior. Delete completed workstreams after durable knowledge transfer by default.

## Failure discipline

Classify failures before editing: change regression, baseline, environment, flaky, base drift or assumption. Fix the owning invariant. Do not suppress/weaken legitimate tests or broaden keep rules blindly to gain speed. A repeated failure after a repair requires a new hypothesis.

## Stop conditions

Surface rather than bypass: material ambiguity, privacy/security/trust conflicts, duplicate ownership, unbounded resources, stale affected docs at integration/release, required deterministic gates with no automation route, stronger environment claims than evidence supports, or a request to weaken a legitimate gate merely for velocity.
