# Shared Android runtime roadmap

Status: active
Document type: roadmap
Owner: shared-runtime
Canonical scope: shared-runtime.roadmap
Read when: selecting a shared-runtime milestone, checking dependencies or defining a focused pull request
Last reviewed: 2026-08-12

This roadmap owns capability order and exit gates. Detailed behavior belongs in the linked workstreams; integrated repository priority and blockers remain in [`../current-state.md`](../current-state.md).

## Sequence

```text
SR-0 Decision and scope                    DONE
   |
SR-1 Binder protocol v1                    DONE
   |--------------------------|
SR-2 Host service          SR-3 Client SDK DONE / IN PROGRESS
   |--------------------------|
SR-4 Two-APK vertical slice                PLANNED
   |
SR-5 Resilience and isolation              PLANNED
   |
SR-6 Device evidence and release           PLANNED

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

State: **IN PROGRESS**

Goal: hide AIDL plumbing behind a lifecycle-safe client artifact.

Owner: [`workstreams/client-sdk.md`](workstreams/client-sdk.md)

Dependencies: SR-0 and SR-1. SR-2 is complete for the proof host.

Progress: **SR-CLIENT-01 and SR-CLIENT-02 are implemented on the active client-SDK branch: the new `transports/android-binder-client` module owns exact host configuration plus explicit bind/protocol-negotiation/disconnect state. Registration and `LocalLlmClient` adaptation are next.**

Exit gate:

- explicit bind and protocol handshake have typed connection states;
- `BinderLocalLlmClient` preserves supported core semantics on non-main executors;
- ordered callbacks reconstruct terminal core events without duplicate aggregate transfer;
- disconnect, close and `DeadObjectException` paths are deterministic;
- generated Binder implementation does not leak into consumer application code.

## SR-4 — Two-APK vertical slice

State: **PLANNED**

Goal: prove the full path using existing repository applications.

Dependencies: SR-2 and SR-3.

Planned slice:

- `apps/local-llm-phone-test` exposes the completed proof host service and exact same-signer console binding;
- the host user explicitly installs/selects a curated Qwen3.5 model;
- `apps/local-llm-console` connects through the SR-3 client adapter and uses its existing inference control;
- prepare, open session, stream, cancel, complete and close cross the real process boundary;
- disconnected, unavailable and incompatible states remain distinct in the console.

Exit gate: a repeatable emulator/device preflight installs both APKs and completes the functional flow without bypassing runtime, store or authorization policy.

## SR-5 — Resilience and isolation

State: **PLANNED**

Goal: harden the boundary for multiple callers and partial failure.

Owner: [`workstreams/validation-rollout.md`](workstreams/validation-rollout.md)

Dependencies: SR-4.

Exit gate:

- same external IDs from different clients cannot collide internally;
- one client cannot observe or control another client's resources;
- client/host death, callback failure, unbind and reconnect cleanly converge;
- bounded callback queues apply backpressure without unbounded memory;
- transport telemetry is privacy-safe and prompt/output sentinel checks pass;
- protocol compatibility fixtures remain valid across supported minor versions.

## SR-6 — Physical-device evidence and release

State: **PLANNED**

Goal: validate the exact two-APK distribution and decide whether the client artifact can be published.

Owner: [`workstreams/validation-rollout.md`](workstreams/validation-rollout.md)

Dependencies: SR-5 plus applicable Q35 runtime/device gates.

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
