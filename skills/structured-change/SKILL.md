---
name: structured-change
description: Guard meaningful code and product changes against duplicated ownership, unresolved material assumptions, unnecessary complexity, unbounded resources, incomplete failure handling, unsafe data lifecycle changes, operational residue, weak UX hierarchy and cross-layer contract drift.
---

# Structured Change

Use this Skill as a pre-edit and pre-final review for meaningful behavior, architecture, persistence, API, concurrency, resource, security, build/runtime, user-facing experience or cross-layer changes.

## 1. Find the owner before editing

- Locate the canonical contract/config/state owner.
- Inspect direct consumers, fakes and tests before changing a shared boundary.
- Search before adding constants, settings, statuses, endpoint paths, mappings or lifecycle rules.
- Prefer extending the existing owner over introducing parallel state or duplicated policy.
- For `product-ui`, find the canonical design/component/token owner before adding a new visual pattern.

Ask: **who owns this after my change?** If there is no clear answer, fix ownership before adding behavior.

## 2. Resolve material ambiguity before implementation

Do not turn an unresolved product/contract decision into an implementation assumption merely to keep moving.

First inspect the canonical owner, durable docs/ADRs, direct consumers/fakes and tests. If two reasonable interpretations remain and they would materially change product behavior, public/API/protocol contracts, persistence/migration semantics, security/trust/privacy boundaries, failure/resource/concurrency/lifecycle semantics, backward compatibility, acceptance criteria or meaningful UX, ask the user before implementing that decision.

When asking, state the smallest decision required, the concrete alternatives and a recommendation when the repository evidence supports one.

Do not ask about implementation-local naming, formatting or equivalent choices that preserve observable semantics. If interaction is unavailable, mark the dependent work blocked/conditional rather than silently guessing.

## 3. Spend complexity deliberately

For every new abstraction, dependency, cache, service, worker, queue, layer, UI component or interaction pattern ask:

- Which concrete problem requires it?
- Why is the current boundary/component insufficient?
- What failure/resource/upgrade/experience surface does it add?
- Can a smaller direct solution preserve the same invariants?

Do not add speculative extensibility or visual novelty without a product reason.

## 4. Respect the project operating contract

Read `.engineering/commands.json` when the change affects setup, local runtime, validation, build, packaging, artifacts or cleanup.

Preserve the common semantics even when the underlying tool changes:

- a material `build` receives a unique build identity;
- successful artifacts are immutable and promoted only after validation;
- every successful comparable build generates its build delta;
- local artifact retention remains bounded;
- project-owned local runtimes close processes/listeners on every exit path;
- temporary resources are owned, isolated and cleaned deterministically.

Do not introduce a second undocumented way to run/build/package the project when the canonical command contract already owns that intent.

## 5. Respect the product experience contract when applicable

When `.engineering/baseline.json` includes `product-ui`, read `design/ux-contract.json` and `design/brand-kit.json` before meaningful UI changes.

For structural UX, interaction redesign, adaptive behavior, motion-system or other semantically meaningful UI work, use `design-product-experience` before implementation. Keep visual-only local token/style edits proportional rather than reopening settled product structure without a reason.

Check that the change:

- starts from the user outcome/task rather than the desired visual treatment;
- preserves the owning information architecture/critical journey or explicitly updates it;
- preserves a clear primary/secondary/destructive action hierarchy;
- reveals advanced/expert/diagnostic complexity progressively;
- uses sensible defaults for normal use;
- handles applicable loading/empty/error/disabled and recovery states;
- gives timely, understandable feedback;
- preserves accessibility and adaptive/platform behavior;
- reuses semantic tokens/components where an owner already exists;
- uses motion only for a product purpose and keeps graphics functional-before-decorative;
- keeps critical journeys aligned with E2E evidence where required.

Do not treat "show all available information" as a neutral choice; excess simultaneous information has cognitive cost. Do not use animation, illustration or visual polish to compensate for an unresolved task flow, hierarchy or feedback model.

## 6. Define resource lifecycle when applicable

For each new/changed significant resource identify owner/acquisition, lifetime/cardinality, budgets, concurrency/backpressure, timeout/cancellation, release/failure cleanup, idle/pressure behavior and metrics.

No unbounded queue/list/cache on an unbounded input path. Prefer admission/reservation before expensive allocation when exhaustion is predictable.

Temporary processes, sockets, locks, test stores, workspaces, build staging areas, logs and caches are resources too. Cleanup must cover success, failure, timeout, cancellation, user interrupt and partial initialization.

## 7. Treat failure as normal behavior

Check applicable paths:

- invalid input;
- partial initialization;
- dependency failure;
- timeout;
- cancellation;
- shutdown during work;
- restart/recovery;
- interrupted persistence/migration;
- failed build/package promotion;
- stale PID/lock/temp state from a previous crashed run;
- user-facing loading/empty/error/offline/permission/partial states where applicable.

Cleanup and UI recovery must restore useful ownership/invariants rather than merely catch an exception or show a generic error.

When validation fails, classify the failure and identify the violated invariant/owner before editing production code. Do not use repeated local patches without a new hypothesis to chase a green check.

## 8. Preserve data/security semantics

When data changes, check creation, storage, owner, trust/encryption boundary, retention, deletion, backup/export, logging, migration and recovery.

Never introduce silent cloud fallback, content logging, secret persistence or destructive migration behavior without explicit contract/review. Temporary credentials/signing data must not leak into logs, caches or distributed artifacts. UI/E2E screenshots/traces must remain privacy-safe.

## 9. Verify cross-layer contracts

When changing a public/shared boundary, inspect every material adapter/consumer and test. Keep domain policy out of UI/transport/persistence adapters unless that layer genuinely owns the policy.

UI text and controls should translate domain capability into the user's task rather than mirror internal object/service names mechanically.

## 10. Pre-final questions

- Is there exactly one owner/source of truth?
- Did I resolve every material ambiguity from repository evidence or the user rather than silently choosing?
- Did the change add more complexity than the problem requires?
- Are resource bounds and cleanup explicit?
- Are cancellation/shutdown/failure states coherent?
- Does a local runtime leave no project-owned process/listener after stop?
- Are build/artifact identity, immutability, retention and build delta preserved when applicable?
- If user-facing, did the design start from user outcome/task rather than visual polish?
- Are hierarchy/progressive disclosure/defaults coherent at the depth this change affects?
- Are critical states/feedback/recovery/accessibility/adaptive behavior coherent?
- Is meaningful motion purposeful, restrained and reduced-motion aware?
- Did we reuse the canonical design component/token instead of creating drift?
- Are persisted/user data and trust boundaries preserved?
- Are tests/evidence focused on real invariants and critical journeys rather than implementation trivia?
- Did documentation/design contracts update only durable current truth?
- Can deterministic parts of this rule move from prose into tooling/CI?

A change is implementation-complete only when applicable code, integration, failure/resource semantics, operating-contract behavior, product-experience behavior, validation and durable documentation agree. Before publication, use `preflight-change` to establish exact-head `READY_FOR_CI`.
