# Consumer runtime readiness and visibility workstream

Status: active
Document type: workstream-state
Owner: models/model-profile + core/runtime-core + shared Consumer API + apps/local-llm-phone-test; cross-repo consumer: redactguard-android
Canonical scope: workstream.consumer-runtime-readiness-visibility
Read when: coordinating Harness-to-consumer configuration, preparation/residency and truthful runtime status
Last reviewed: 2026-08-27

Durable configuration UX remains owned by [`../features/application-control-plane-ux.md`](../features/application-control-plane-ux.md). Control-plane startup repair remains owned by [`control-plane-state-reconciliation.md`](control-plane-state-reconciliation.md). This workstream owns only the new execution-readiness/visibility convergence and its cross-repo sequencing.

## Goal

Make the consumer path behave and read as one explicit lifecycle:

```text
consumer transport
 -> assigned application/use case
 -> published/default preset
 -> exact model/config resolution
 -> activation
 -> runtime preparation
 -> automatic load/reuse/safe switch of the resolved installed model
 -> session ready
 -> generation
 -> cleanup
```

Harness must own model/configuration/residency. RedactGuard consumes only host-published safe choices and truthful lifecycle state. A user must not manually load the model in Harness before consumer inference.

## Non-goals and invariants

- no phone-global selected-model fallback;
- no RedactGuard-owned model ID, digest, generation tuning or residency policy;
- no second model loader: `RuntimeOrchestrator` preparation remains the runtime materialization owner;
- activation resolves/locks configuration but does not itself load a GGUF;
- simple connection/navigation/discovery remains side-effect free and never implicitly loads/downloads/infers;
- automatic preparation loads only an already installed/verified eligible model; missing/unusable model fails closed with recovery;
- automatic-model policy remains explicit policy; every accepted execution still resolves one concrete model identity before runtime preparation;
- transport connectivity, configuration readiness and runtime activity are distinct dimensions;
- UI shows only source-backed phases; no invented percentages or inferred quality/latency claims;
- consumer-safe status must not expose model digest/private path or prompt/output content;
- one resident model / one production active decode default and existing lease/session cleanup invariants remain unchanged.

## Execution DAG

| ID | State | Depends on | Owns / writes | Parallel with | Acceptance |
| --- | --- | --- | --- | --- | --- |
| CRV-00 | DONE | — | this scope/invariants + durable-owner mapping | — | Owners, lifecycle, non-goals, integration points and validation strategy are explicit. |
| CRV-10 | ACTIVE | CRV-00 | `models/model-profile` resolver/preset execution semantics + focused tests only | CRV-20, CRV-50, CRV-60 | Explicit and automatic preset model policies deterministically resolve one installed compatible model; failures stay typed/fail-closed; no global fallback. |
| CRV-20 | ACTIVE | CRV-00 | Consumer-safe readiness/progress contract and required Binder/host adapters + compatibility tests | CRV-10, CRV-50, CRV-60 | Consumer can distinguish transport/configuration/preparing/ready/generating/failure at the smallest compatible boundary without Host model identity leakage. |
| CRV-30 | BLOCKED | CRV-10 | runtime/host integration from activation binding into prepare/create-session + focused lifecycle tests | CRV-20 | Cold load, warm reuse and safe switch use the exact resolved execution; missing/conflicting resources fail closed; cancellation/disconnect clean up. |
| CRV-40 | BLOCKED | CRV-20, CRV-30 | source-backed runtime/readiness observation and privacy-safe projection | — | Host and consumer projections observe real preparation/residency activity without changing resource ownership. |
| CRV-50 | ACTIVE | CRV-00 | Harness Applications/preset UX contract and presentation mapping only | CRV-10, CRV-20, CRV-60 | Presets expose model policy, effective model when resolvable, inference configuration/context and truthful connection/runtime state with progressive disclosure. |
| CRV-60 | ACTIVE | CRV-00 | RedactGuard UX/state contract and consumer-safe presentation only | CRV-10, CRV-20, CRV-50 | Binder-connected is no longer equivalent to analysis-ready; selected host mode and configuration/preparation/recovery are visible without leaking Host internals. |
| CRV-70 | BLOCKED | CRV-10, CRV-40, CRV-50 | Harness phone Applications/readiness UI + tests | CRV-80 | Application/use-case/preset surfaces show canonical execution config plus current activation/preparation/runtime state; observation has no load side effect. |
| CRV-80 | BLOCKED | CRV-20, CRV-40, CRV-60 | redactguard-android consumer composition/ViewModel/UI + tests | CRV-70 | Analysis automatically activates/prepares the Host path; UI truthfully transitions through configuration/preparation/analysis and exposes actionable recovery. |
| CRV-90 | BLOCKED | CRV-30, CRV-40, CRV-70, CRV-80 | isolated cross-layer/cross-repo regression matrix | — | Covers cold, warm, model switch/conflict, missing model, stale config, disconnect, cancellation, restart and privacy-safe failure identity. |
| CRV-100 | BLOCKED | CRV-90 | exact-head integration/preflight/candidate metadata in both repos | — | Auto-selected validation is not downgraded; runtime/Binder changes receive STRONG-or-higher deterministic evidence; new unambiguous candidate identities are produced. |
| CRV-110 | BLOCKED | CRV-100 | representative two-APK physical evidence only | — | RedactGuard starts with the assigned model not resident, Harness prepares it automatically, both apps show truthful states, inference succeeds, and failure variants fail closed. |
| CRV-120 | BLOCKED | CRV-110 | durable docs/current-state transfer + workstream cleanup | — | Durable behavior/evidence agree; dependent ACUX/HCP/CPREC/RG-HCP gates are updated and this temporary file is removed by default. |

Allowed states: `READY`, `ACTIVE`, `BLOCKED`, `DONE`.

## Current executable slices

Four non-conflicting lanes may execute now:

- **CRV-10** — resolver/preset execution semantics;
- **CRV-20** — smallest consumer-safe readiness/progress contract;
- **CRV-50** — Harness configuration/runtime UX contract and source mapping;
- **CRV-60** — RedactGuard consumer-safe state/UX contract.

CRV-30 starts as soon as CRV-10 settles exact execution identity. CRV-70 and CRV-80 then run in parallel after CRV-40 establishes the source-backed state boundary.

## Required semantics

Keep three dimensions distinct:

```text
Transport:      disconnected -> connecting -> connected
Configuration:  discovering -> ready | setup-required | stale | incompatible
Runtime:        idle -> preparing -> loading/reusing/switching -> ready -> generating -> ready | failed
```

`UseCaseReadiness.READY` currently describes model-store/capability availability; it must not be relabeled as RAM residency. Runtime residency/activity needs its own source-backed projection.

Automatic model selection is allowed only as an explicit preset policy. Before preparation, `HostExecutionResolver` must still produce the concrete execution identity used by activation/session creation. No later layer may silently substitute another model.

## Preset product contract

Harness is the technical configuration owner. A usable preset must make the effective execution understandable:

- consumer-safe display name/description;
- explicit model policy: Automatic or a concrete eligible model profile;
- effective concrete model when source-backed/resolvable;
- inference preset/generation profile and effective sampling/thinking/output values;
- context and cache/runtime policy summary;
- default/exposed/creation-source/revision state.

Do not duplicate generation values into a second persistence owner. If the control plane stores an `InferencePresetRef`, UI resolves that canonical reference for display/editing and only extends mutation contracts when the existing canonical generation owner cannot represent the requested user action.

## RedactGuard product contract

Normal UI remains consumer-safe. It may show the selected Host mode and truthful phases such as `Preparing local AI`, but model IDs/digests/residency implementation terms remain diagnostic-only unless needed for recovery. A single available preset may remain auto-selected, but the selected mode/configuration-readiness summary must not disappear merely because there is no choice to make.

Analysis readiness requires more than Binder connectivity. Transport connection must trigger side-effect-free configuration discovery; analysis repeats the authoritative discovery/activation/prepare handshake and owns terminal failure.

## Validation and evidence

Blast radius is STRONG whenever shared contracts/Binder/runtime/residency/model execution identity changes. Contained phone/consumer UI-only slices may select SCOPED if the repository selector agrees. Deterministic Android/Gradle/R8/Binder gates unavailable agent-local are `REMOTE_AUTOMATED`, not delegated to the user.

Physical two-APK, same-signer, real GGUF/residency behavior is `REAL_ENVIRONMENT` and belongs only to CRV-110 after exact-head automated preflight. Existing Harness v30 / RedactGuard v10 candidates predate CRV and do not close CRV-110.

CRV-110 should, where exact criteria align, be the same physical session used to close the remaining CPREC-80/90, ACUX-90 and RedactGuard RG-HCP-8 evidence without conflating their independent pass/fail criteria.

## Durable destinations

- `docs/features/application-control-plane-ux.md` — final Harness configuration/readiness user-visible semantics;
- architecture/ADR only if ownership or public lifecycle boundary materially changes;
- Consumer API/runtime contracts and tests — executable truth for readiness/progress;
- RedactGuard `docs/features/` + `design/ux-contract.json` — consumer-safe readiness behavior;
- both `docs/current-state.md` files — integrated state/blockers/evidence, not implementation diary.
