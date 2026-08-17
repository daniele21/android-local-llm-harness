# Shared Android runtime plan

Status: active
Document type: feature-index
Owner: shared-runtime
Canonical scope: shared-runtime.routing
Read when: locating the shared Binder runtime objective, milestone order or owning workstream
Last reviewed: 2026-08-17

This is the progressive-disclosure entry point for exposing local generation from one Android APK to another. Integrated state remains owned by [`../current-state.md`](../current-state.md); detailed milestone truth remains in [`roadmap.md`](roadmap.md).

## Proposed v1 boundary

- A host APK owns model acquisition, installed bytes, runtime, native backend and telemetry.
- A separately installed client APK binds explicitly to a signature-protected Android service.
- AIDL owns only the Android wire protocol; core lifecycle contracts remain free of `Parcelable`, `Binder`, app UI and `llama.cpp` types.
- The host authenticates the Binder caller and derives its internal `ApplicationId`; it never trusts identity supplied in a request.
- The client always selects an allowlisted `UseCaseId`; any wider logical model/preset choice is governed by the focused [`consumer-api/`](consumer-api/) plan and is not assumed until its CA-0 decisions are accepted.
- Generation is asynchronous and streams bounded events through a callback. Cancellation and cleanup are first-class protocol operations.
- The first slice is bound-only: work is cancelled when the client or service connection dies and does not continue invisibly in the background.
- Prompts and generated content remain bounded in memory and out of normal telemetry, persistence and evidence.

The trust, deployment and lifecycle choices are owned by the accepted shared-runtime ADRs and target. The consumer-API plan may propose a narrower application-facing capability model without changing those durable decisions silently.

## Progress at a glance

Use [`roadmap.md`](roadmap.md) for the canonical SR milestone state. Do not duplicate milestone status here.

The public consumer API is a follow-on design/implementation stream. Its current planning state and exit gates are owned by [`consumer-api/roadmap.md`](consumer-api/roadmap.md).

## What to read

Start here, then open only the source that owns the current question.

| Need | Read |
| --- | --- |
| Product goal, trust assumption, user flow and non-goals | [`target.md`](target.md) |
| Deployment shape, module boundaries, identity and lifecycle | [`architecture.md`](architecture.md) |
| Milestone order, dependencies, PR slicing and exit gates | [`roadmap.md`](roadmap.md) |
| Application-facing boundary: model/preset choice, reasoning, metrics and consumer API | [`consumer-api/README.md`](consumer-api/README.md) |
| External Android SDK publication, Maven coordinates and standalone consumer verification | [`consumer-android-sdk.md`](consumer-android-sdk.md) |
| RedactGuard cross-repository ownership and cutover gates | [`workstreams/redactguard-cross-repo-extraction.md`](workstreams/redactguard-cross-repo-extraction.md) |
| AIDL methods, wire DTOs, event semantics and compatibility | [`workstreams/protocol-v1.md`](workstreams/protocol-v1.md) |
| Host authorization, runtime composition and client cleanup | [`workstreams/host-service.md`](workstreams/host-service.md) |
| Client binding, adapter behavior and console integration | [`workstreams/client-sdk.md`](workstreams/client-sdk.md) |
| Test matrix, two-APK device runner, evidence and rollout | [`workstreams/validation-rollout.md`](workstreams/validation-rollout.md) |

Read existing sources only when the selected workstream routes to them. In particular, do not load all model, runtime, observability and application plans for a protocol-only change.

## Ownership map

| Boundary | Owner | Existing dependency to preserve |
| --- | --- | --- |
| Android wire schema and protocol compatibility | `transports/android-binder-contract` | `core/contracts` semantics |
| Binding state and client adapter | `transports/android-binder-client` | `LocalLlmClient` |
| Service security, caller ownership and runtime delegation | `integrations/android-service-host` | runtime and model binding contracts |
| Proof host composition | `apps/local-llm-phone-test` | one process-scoped runtime graph |
| Proof/reference client composition | `apps/local-llm-console` | packaged public client boundary |
| Consumer capability/policy contract | `shared-runtime/consumer-api` plan, then accepted implementation owner | existing use-case/model resolution |
| Physical two-APK execution | `scripts` and application instrumentation | device evidence policy |

## Coding-agent execution rules

1. Read the root `AGENTS.md`, this index and exactly one active workstream/index routed from the question.
2. Read that workstream's `Inspect before editing` list and the closest scoped agent guide for every application subtree touched.
3. Confirm the roadmap dependency and choose one task ID or one coherent vertical slice.
4. Inspect current implementations, direct consumers, fakes and tests before changing a public or wire boundary.
5. Keep AIDL parcel types out of `core/contracts`; map at the transport edge.
6. Keep host authorization and client resource ownership out of Activities and Compose.
7. Add success, invalid input, cancellation, death, cleanup and compatibility coverage required by the selected slice.
8. Run the narrow workstream checks while iterating, then expand to the repository-wide gate for shared contracts, Gradle, manifests or multiple apps.
9. Update task state only after the behavior is integrated. Record repository priority and blockers only in `../current-state.md`.
10. Do not claim app-consumer readiness without the physical-device evidence owned by the validation workstream.

## Documentation ownership

These files own intended shared-runtime behavior. The `consumer-api/` directory owns the proposed application-facing inference contract and must not duplicate Binder lifecycle/security details already owned here. Once a slice is implemented, update the focused specification and only the canonical current-state/roadmap summaries that changed. Accepted deployment or protocol decisions belong in ADR/target owners; public usage belongs in an API reference after the surface exists.
