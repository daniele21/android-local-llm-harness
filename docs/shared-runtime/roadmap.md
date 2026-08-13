# Shared Android runtime roadmap

Status: active
Document type: roadmap
Owner: shared-runtime
Canonical scope: shared-runtime.roadmap
Read when: selecting a shared-runtime milestone, checking dependencies or defining a focused pull request
Last reviewed: 2026-08-13

This roadmap owns capability order and exit gates. Detailed behavior belongs in the linked workstreams; integrated repository priority and blockers remain in [`../current-state.md`](../current-state.md).

## Sequence

```text
SR-0 Decision and scope                    DONE
   |
SR-1 Binder protocol v1                    DONE
   |--------------------------|
SR-2 Host service          SR-3 Client SDK DONE / DONE
   |--------------------------|
SR-4 Two-APK vertical slice                IN PROGRESS
   |
SR-5 Resilience and isolation              DONE
   |
SR-6 Device evidence and release           IN PROGRESS

Q35 physical runtime evidence ----- release dependency -----^
```

SR-2 and SR-3 may proceed independently only after SR-1 freezes the v1 fixtures and semantics. They must not invent separate wire DTOs or error mappings.

## SR-0 — Decision and scope

State: **DONE**

Goal: convert the proposal into an accepted durable deployment decision.

Required outputs:

- ADR 0012 covering same-signer trust, bound-only lifecycle, explicit component binding, host-owned identity/model selection and protocol independence;
- confirmed module ownership from [`architecture.md`](architecture.md);
- explicit proof-host versus final-host decision boundary;
- compatibility and privacy review of the proposed v1 surface;
- no service or empty Gradle modules before the decision is accepted.

Exit gate: all decisions listed in [`target.md`](target.md) are accepted or the dependent plan is revised.

## SR-1 — Binder protocol v1

State: **DONE**

Goal: define and test the wire contract without connecting a production runtime.

Owner: [`workstreams/protocol-v1.md`](workstreams/protocol-v1.md)

Exit gate:

- protocol/module compiles in host and client consumers;
- version and feature negotiation is deterministic;
- all supported core request/event/error projections round-trip through parcels;
- invalid tags, sizes and incompatible versions fail closed;
- callback ordering, terminal uniqueness and chunk limits are contract-tested.

## SR-2 — Host service

State: **DONE**

Goal: expose the existing data plane through an authenticated, lifecycle-safe Android service.

Owner: [`workstreams/host-service.md`](workstreams/host-service.md)

Dependencies: SR-0 and SR-1.

Progress: **SR-HOST-01 through SR-HOST-09 are implemented. The host now exposes the shared process-scoped runtime through the signature-protected proof service, resolves the external console only through exact host-owned bindings, and delegates Android memory/service lifecycle without duplicate runtime ownership.**

Exit gate:

- exported service is protected by signature permission and explicit caller policy;
- Binder threads do no heavy runtime work;
- client/session/request ownership is isolated and death-aware;
- host resolves exact application/use-case/model binding without client model control;
- service delegate tests cover denial, cancellation, death, teardown and idempotent cleanup.

## SR-3 — Client SDK

State: **DONE**

Goal: hide AIDL plumbing behind a lifecycle-safe client artifact.

Owner: [`workstreams/client-sdk.md`](workstreams/client-sdk.md)

Dependencies: SR-0 and SR-1. SR-2 is complete for the proof host.

Progress: **SR-CLIENT-01 through SR-CLIENT-08 are implemented. The client exposes a high-level `BinderLocalLlmClient` over exact binding, v1 negotiation, registered-client lifecycle, non-main prepare/session adaptation, ordered bounded streaming, idempotent cancellation and deterministic epoch/dead-object/timeout handling. The Console now has explicit shared-runtime connect/retry states and a remote inference target without conflating proof-host inference with console-local diagnostics. Packaged client/contract AAR consumption, consumer shrinker rules and client-AAR structure are repository-validated.**

Exit gate:

- explicit bind and protocol handshake have typed connection states;
- `BinderLocalLlmClient` preserves supported core semantics on non-main executors;
- ordered callbacks reconstruct terminal core events without duplicate aggregate transfer;
- disconnect, close and `DeadObjectException` paths are deterministic;
- generated Binder implementation does not leak into consumer application code.

## SR-4 — Two-APK vertical slice

State: **IN PROGRESS**

Goal: prove the full path using existing repository applications.

Dependencies: SR-2 and SR-3.

Integrated implementation:

- `apps/local-llm-phone-test` exposes the proof host service and exact same-signer console binding;
- `apps/local-llm-console` contains a real Binder instrumentation flow for prepare, session, stream, complete, cancel and close;
- `scripts/run-shared-runtime-device-e2e.sh` installs the two debug APKs and executes the cross-process preflight;
- unavailable, denied, incompatible and disconnected states remain typed.

Remaining exit evidence: execute the repeatable two-APK preflight on an actual emulator/device with a curated Qwen3.5 model installed and selected in the host. Repository implementation alone does not satisfy this physical/execution gate.

Exit gate: a repeatable emulator/device preflight installs both APKs and completes the functional flow without bypassing runtime, store or authorization policy.

## SR-5 — Resilience and isolation

State: **DONE**

Goal: harden the boundary for multiple callers and partial failure.

Owner: [`workstreams/validation-rollout.md`](workstreams/validation-rollout.md)

Dependencies: SR-4 implementation.

Integrated evidence:

- external IDs are isolated through host-owned internal identities;
- one client's close/death cannot drain another client's resources;
- bounded host/client callback queues fail closed under backpressure;
- client/host disconnect, callback failure, stale epochs and cancellation converge deterministically;
- prepare/runtime detail is scrubbed at the Binder boundary;
- protocol compatibility fixtures from SR-1 remain green.

Exit gate:

- same external IDs from different clients cannot collide internally;
- one client cannot observe or control another client's resources;
- client/host death, callback failure, unbind and reconnect cleanly converge;
- bounded callback queues apply backpressure without unbounded memory;
- transport telemetry is privacy-safe and prompt/output sentinel checks pass;
- protocol compatibility fixtures remain valid across supported minor versions.

## SR-6 — Physical-device evidence and release

State: **IN PROGRESS**

Goal: validate the exact two-APK distribution and decide whether the client artifact can be published.

Owner: [`workstreams/validation-rollout.md`](workstreams/validation-rollout.md)
Runbook: [`sr6-release-evidence.md`](sr6-release-evidence.md)

Dependencies: SR-5 plus applicable Q35 runtime/device gates.

Repository implementation in progress:

- packaged release Binder client/contract AAR consumer fixture;
- same-signer release-like functional/cancellation/process-death instrumentation;
- independently signed ephemeral negative fixture;
- physical-device evidence capture with package/certificate/protocol/device/memory/thermal identity;
- explicit evidence privacy boundary and archive format.

Remaining exit evidence requires execution on representative physical hardware and completion of the public API/security/versioning/release review for the exact candidate. Q35 physical runtime evidence remains an independent release dependency.

Exit gate:

- same-signer release-like host/client APKs pass the two-APK physical-device matrix;
- an independently signed negative fixture is denied without committed signing material;
- Binder overhead, memory, cancellation and process-death evidence is reviewable;
- public API, security, versioning, packaging and consumer sample review pass;
- release notes bind host version, client SDK version, protocol version, runtime/backend and model evidence.

## Pull-request slicing

| PR | Coherent deliverable | Target |
| --- | --- | --- |
| SR-0 | ADR and any plan corrections; no runtime code | `dev` |
| SR-1a | Binder contract module, wire validation and fixtures | `dev` |
| SR-1b | Core/wire mapping and compatibility tests | `dev` |
| SR-2 | Host integration module with fake `LocalLlmClient` tests | `dev` |
| SR-3 | Client adapter with fake Binder service tests | `dev` |
| SR-4 | Phone host plus console client vertical slice | `dev` |
| SR-5 | Death, multi-client, backpressure and privacy hardening | `dev` |
| SR-6 | Device runner, evidence and release packaging | `dev` |

Combine adjacent slices only when the intermediate state would not compile or test independently. Do not combine model-management redesign, diagnostics bridge, Capacitor work or unrelated UX migration.

## Branch and validation rule

Ordinary work starts from the latest green `dev`. If `main` contains a promotion merge not yet synchronized back, complete the protected `main -> dev` synchronization first. Each shared-contract, Gradle, manifest or multi-app change runs the repository-wide gate in addition to focused workstream checks.

## State update rule

- `PLANNED` means no integrated implementation may be assumed.
- Mark a task or milestone `IN PROGRESS` only in the canonical state owner when repository work has actually begun.
- Mark `DONE` only after its exit gate is integrated and the focused tests are green.
- Physical-device evidence remains pending unless it was executed for the exact recorded host/client/runtime identity.
