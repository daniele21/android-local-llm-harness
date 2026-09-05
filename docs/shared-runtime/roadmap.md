# Shared Android runtime roadmap

Status: active
Document type: roadmap
Owner: shared-runtime
Canonical scope: shared-runtime.roadmap
Read when: selecting a shared-runtime milestone, checking dependencies or defining a focused pull request
Last reviewed: 2026-09-05

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

ADR 0017 is the accepted trust amendment for independently signed consumers. It supersedes the same-signer/signature-permission portion of ADR 0012 without changing the Host-owned model/use-case or Binder protocol boundaries.

## SR-0 — Decision and scope

State: **DONE**

Goal: convert the proposal into an accepted durable deployment decision.

Required outputs:

- ADR 0012 covering the original shared-runtime boundary and ADR 0017 covering independently signed consumer authorization;
- confirmed module ownership from [`architecture.md`](architecture.md);
- explicit proof-host versus final-host decision boundary;
- compatibility and privacy review of the supported surface;
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

Progress: **SR-HOST-01 through SR-HOST-10 are implemented. The host exposes the shared process-scoped runtime behind the `BIND_LOCAL_LLM` capability permission, performs exact Binder caller authorization through Harnex Control Plane policy, supports independently signed consumers without shared signing credentials, and delegates Android memory/service lifecycle without duplicate runtime ownership.**

Exit gate:

- exported service uses the explicit bind capability while actual authority remains Binder UID/package/signer + Harnex Control Plane policy;
- independently signed known consumers are source-observed pending and require explicit authorization; signer replacement fails closed;
- Binder threads do no heavy runtime work;
- client/session/request ownership is isolated and death-aware;
- host resolves exact application/use-case/model binding without client model control;
- service delegate tests cover denial, cancellation, death, teardown and idempotent cleanup.

## SR-3 — Client SDK

State: **DONE**

Goal: hide AIDL plumbing behind a lifecycle-safe client artifact.

Owner: [`workstreams/client-sdk.md`](workstreams/client-sdk.md)

Dependencies: SR-0 and SR-1. SR-2 is complete for the proof host.

Progress: **SR-CLIENT-01 through SR-CLIENT-08 are implemented. The client exposes a high-level `BinderLocalLlmClient` over exact binding, v1 negotiation, registered-client lifecycle, non-main prepare/session adaptation, ordered bounded streaming, idempotent cancellation and deterministic epoch/dead-object/timeout handling. The Consumer SDK also supports reusable explicit disconnect/reconnect without turning transport lifetime into authorization state. Packaged client/contract AAR consumption, consumer shrinker rules and client-AAR structure are repository-validated.**

Exit gate:

- explicit bind and protocol handshake have typed connection states;
- `BinderLocalLlmClient` preserves supported core semantics on non-main executors;
- ordered callbacks reconstruct terminal core events without duplicate aggregate transfer;
- disconnect, reconnect, close and `DeadObjectException` paths are deterministic;
- generated Binder implementation does not leak into consumer application code.

## SR-4 — Two-APK vertical slice

State: **IN PROGRESS**

Goal: prove the full path using existing repository applications and real external consumers.

Dependencies: SR-2 and SR-3.

Integrated implementation:

- `apps/local-llm-phone-test` exposes the proof Host service and exact Host-owned application/use-case policy;
- same-publisher Console flows remain supported where intentionally configured;
- independently signed consumers such as RedactGuard use source-observed package/signer identity and explicit Harnex authorization;
- `apps/local-llm-console` contains a real Binder instrumentation flow for prepare, session, stream, complete, cancel and close;
- cross-repository RedactGuard evidence signs Host and consumer with distinct ephemeral identities and proves denial-before-approval plus authorized reconnect;
- unavailable, pending, signer-changed, denied, incompatible and disconnected states remain typed.

Remaining exit evidence depends on the exact claim: deterministic emulator evidence proves the cross-APK trust and lifecycle boundary; physical Play/Internal and real-GGUF/device evidence remain separate when release/distribution/runtime claims require them.

Exit gate: repeatable cross-APK preflight completes the functional flow without bypassing runtime, store or authorization policy and uses the signing topology being claimed.

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

Goal: validate the exact supported distribution topology and decide whether the client artifact can be published/promoted.

Owner: [`workstreams/validation-rollout.md`](workstreams/validation-rollout.md)
Runbook: [`sr6-release-evidence.md`](sr6-release-evidence.md)

Dependencies: SR-5 plus applicable Q35 runtime/device gates.

Repository implementation/evidence includes:

- packaged release Binder client/contract AAR consumer fixture;
- same-publisher release-like functional/cancellation/process-death instrumentation where that topology is still intentionally supported;
- independently signed Host/consumer E2E for external consumers, including distinct certificate proof and explicit Harnex authorization;
- negative unknown/mismatched signer evidence;
- physical-device evidence capture with package/certificate/protocol/device/memory/thermal identity;
- explicit evidence privacy boundary and archive format.

Remaining exit evidence requires completion of the public API/security/versioning/release review for the exact candidate plus the REAL_ENVIRONMENT evidence material to the distribution/runtime claim. Q35 physical runtime evidence remains an independent release dependency.

Exit gate:

- every claimed Host/client signing topology has production-shaped evidence;
- independently distributed consumers prove distinct signers, denial before authorization and success only after explicit authorization of the exact observed identity;
- unknown/mismatched signer fixtures remain denied without committed signing material;
- Binder overhead, memory, cancellation and process-death evidence is reviewable where required;
- public API, security, versioning, packaging and consumer sample review pass;
- release notes bind host version, client SDK version, protocol version, signing identities, runtime/backend and model evidence.

## Pull-request slicing

| PR | Coherent deliverable | Target |
| --- | --- | --- |
| SR-0 | ADR and any plan corrections; no runtime code | `dev` |
| SR-1a | Binder contract module, wire validation and fixtures | `dev` |
| SR-1b | Core/wire mapping and compatibility tests | `dev` |
| SR-2 | Host integration module with fake `LocalLlmClient` tests | `dev` |
| SR-3 | Client adapter with fake Binder service tests | `dev` |
| SR-4 | Phone host plus consumer vertical slices | `dev` |
| SR-5 | Death, multi-client, backpressure and privacy hardening | `dev` |
| SR-6 | Device runner, evidence and release packaging | `dev` |

Combine adjacent slices only when the intermediate state would not compile or test independently. Do not combine model-management redesign, diagnostics bridge, Capacitor work or unrelated UX migration.

## Branch and validation rule

Ordinary work starts from the latest green `dev`. If `main` contains a promotion merge not yet synchronized back, complete the protected `main -> dev` synchronization first. Each shared-contract, Gradle, manifest or multi-app change runs the repository selector with `profile=auto` in addition to focused workstream checks; security/Manifest/public-SDK/cross-app changes require the resulting strong/full gates without manual downgrades.

## State update rule

- `PLANNED` means no integrated implementation may be assumed.
- Mark a task or milestone `IN PROGRESS` only in the canonical state owner when repository work has actually begun.
- Mark `DONE` only after its exit gate is integrated and the focused tests are green.
- Physical-device evidence remains pending unless it was executed for the exact recorded host/client/runtime identity.
