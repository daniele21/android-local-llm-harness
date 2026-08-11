# Shared Android runtime plan

Status: active
Document type: feature-index
Owner: shared-runtime
Canonical scope: shared-runtime.routing
Read when: locating the shared Binder runtime objective, milestone order or owning workstream
Last reviewed: 2026-08-11

This is the progressive-disclosure entry point for exposing local generation from one Android APK to another. It defines a planned capability, not implemented behavior. Integrated state remains owned by [`../current-state.md`](../current-state.md).

## Proposed v1 boundary

- A host APK owns model acquisition, installed bytes, selection, runtime, native backend and telemetry.
- A separately installed client APK binds explicitly to a signature-protected Android service.
- AIDL owns only the Android wire protocol; core lifecycle contracts remain free of `Parcelable`, `Binder`, app UI and `llama.cpp` types.
- The host authenticates the Binder caller and derives its internal `ApplicationId`; it never trusts identity supplied in a request.
- The client selects only an allowlisted `UseCaseId`. It cannot provide a model path, arbitrary model URL, digest or load profile.
- Generation is asynchronous and streams bounded events through a callback. Cancellation and cleanup are first-class protocol operations.
- The first slice is bound-only: work is cancelled when the client or service connection dies and does not continue invisibly in the background.
- Prompts and generated content remain bounded in memory and out of normal telemetry, persistence and evidence.

The trust, deployment and lifecycle choices require an accepted ADR before the wire protocol is treated as stable. ADR 0010 already requires the embedded and future shared deployments to execute the same data plane.

## Progress at a glance

| Milestone | State | Outcome |
| --- | --- | --- |
| SR-0 Decision and scope | PLANNED | Accept the same-signer, bound-only v1 deployment and compatibility rules. |
| SR-1 Binder protocol v1 | PLANNED | Add versioned AIDL, wire DTOs, mappings and deterministic contract tests. |
| SR-2 Host service | PLANNED | Protect and connect a host service to the existing runtime data plane. |
| SR-3 Client SDK | PLANNED | Provide lifecycle-safe binding and a Binder-backed `LocalLlmClient`. |
| SR-4 Two-APK slice | PLANNED | Use the phone-test APK as host and console APK as the first client. |
| SR-5 Resilience and isolation | PLANNED | Prove multi-client ownership, death cleanup, privacy and bounded transport. |
| SR-6 Device and release evidence | PLANNED | Validate two signed APKs on physical devices before consumer distribution. |

The repository-wide immediate gate remains Q35-6 physical Android tuning. Shared-runtime implementation may be developed as an explicitly experimental slice, but consumer distribution remains blocked by the applicable physical-device and release gates.

## What to read

Start here, then open only the source that owns the current question.

| Need | Read |
| --- | --- |
| Product goal, trust assumption, user flow and non-goals | [`target.md`](target.md) |
| Deployment shape, module boundaries, identity and lifecycle | [`architecture.md`](architecture.md) |
| Milestone order, dependencies, PR slicing and exit gates | [`roadmap.md`](roadmap.md) |
| AIDL methods, wire DTOs, event semantics and compatibility | [`workstreams/protocol-v1.md`](workstreams/protocol-v1.md) |
| Host authorization, runtime composition and client cleanup | [`workstreams/host-service.md`](workstreams/host-service.md) |
| Client binding, adapter behavior and console integration | [`workstreams/client-sdk.md`](workstreams/client-sdk.md) |
| Test matrix, two-APK device runner, evidence and rollout | [`workstreams/validation-rollout.md`](workstreams/validation-rollout.md) |

Read existing sources only when the selected workstream routes to them. In particular, do not load all model, runtime, observability and application plans for a protocol-only change.

## Ownership map

| Boundary | Planned owner | Existing dependency to preserve |
| --- | --- | --- |
| Android wire schema and protocol compatibility | `transports/android-binder-contract` | `core/contracts` semantics |
| Binding state and client adapter | `transports/android-binder-client` | `LocalLlmClient` |
| Service security, caller ownership and runtime delegation | `integrations/android-service-host` | runtime and model binding contracts |
| Proof host composition | `apps/local-llm-phone-test` | one process-scoped runtime graph |
| Proof client composition | `apps/local-llm-console` | existing inference control |
| Physical two-APK execution | `scripts` and application instrumentation | device evidence policy |

Module names are target ownership boundaries. SR-0 must confirm them before Gradle modules are created; agents must not add empty placeholder modules.

## Coding-agent execution rules

1. Read the root `AGENTS.md`, this index and exactly one active workstream.
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

These files own intended shared-runtime behavior. Once a slice is implemented, update the focused specification and only the canonical current-state/roadmap summaries that changed. An accepted deployment or protocol decision belongs in a new ADR; public usage belongs in a dedicated API reference or a clearly separated shared-runtime section after the API exists.
