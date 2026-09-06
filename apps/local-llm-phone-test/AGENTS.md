# Connected Phone Test App — Coding Agent Guide

## Scope

Applies to `apps/local-llm-phone-test/**` and supplements root [`AGENTS.md`](../../AGENTS.md). This app owns connected Compose composition, process-scoped runtime wiring, shared-runtime proof-host composition and the Play/device validation surface; domain policy remains in its owning runtime/model/evaluation/observability/Binder/audit modules.

## Navigation

Read only the owners needed for the change:

- [`current-state.md`](../../docs/current-state.md) and active integration plan for current product state;
- [`shared-runtime/roadmap.md`](../../docs/shared-runtime/roadmap.md), [`shared-runtime/workstreams/host-service.md`](../../docs/shared-runtime/workstreams/host-service.md), ADR 0012 and ADR 0018 for shared-runtime work;
- the Harness UX/UI plan/progress and [`design-system.md`](../../docs/design-system.md) for Compose/product work;
- [`model-evaluation/README.md`](../../docs/model-evaluation/README.md) for Performance;
- [`features/local-inference-activity-audit.md`](../../docs/features/local-inference-activity-audit.md) and ADR 0017 for inference audit;
- [`model-management-phone.md`](../../docs/model-management-phone.md) and [`phone-model-distribution.md`](../../docs/phone-model-distribution.md) for model flows;
- [`play-internal-phone-test.md`](../../docs/play-internal-phone-test.md) for release/device evidence.

Route by responsibility:

| Concern | Start here | Owning dependency |
| --- | --- | --- |
| App/process composition | `HarnessRuntimeGraph.kt`, `MainActivity.kt` | Runtime, control plane, transport, observability, audit |
| Shared-runtime proof host | `HarnessSharedRuntimeService.kt`, `HarnessSharedRuntimePolicy.kt`, variants | `integrations/android-service-host`, Binder, ADR 0012/0018 |
| Navigation/UI | `HarnessDestination.kt`, Compose entry points | `ui/design-system` |
| Playground/inference | `HarnessViewModel.kt`, `PhonePlaygroundController.kt` | `LocalLlmClient`, runtime/generation contracts |
| Performance | `PerformanceViewModel.kt`, `PerformanceScreen.kt` | `evaluation/*`, telemetry |
| Model distribution/management | distribution controller, management control | `models/*`, ModelStore |
| Inference Activity | Activity source/ViewModel/presentation | `InferenceAuditRepository` |
| Diagnostics | `HarnessViewModel.kt`, `Harness*Source.kt` | `observability/*` |
| Physical validation | `PhoneTestController.kt` | device/evidence docs, production backend |
| Packaging | `version.properties`, build/manifest | release script, Play runbook |

## Local invariants

- `MainActivity` is a composition/Activity Result/lifecycle-effect boundary, not a domain-policy or long-lived render-state owner. Screens use ViewModels/controllers and neutral contracts, not orchestrators, stores, repositories or executors directly.
- Diagnostics history is renderable `HarnessUiState`. Async diagnostics use ViewModel generation tokens; stale callbacks fail closed and teardown invalidates them before owned resources close.
- Navigation/observation never implicitly loads a model, starts inference/evaluation, downloads/installs, benchmarks, captures resources, mutates baselines or repairs caches.
- Performance shows only source-backed evaluation state; do not rank without compatible aggregated latency/throughput/memory/quality evidence.
- The proof `Service` reuses `HarnessRuntimeGraph.from(...)`. Binding/handshake must not create another runtime, select/load a model, or open a parallel control-plane database.
- Shared-runtime inference has no custom bind permission; explicit reachability is not authority. Authorize Binder UID → exact installed package → current signer → persisted Harnex authorization → enabled use case. Source-observed consumers start pending; signer changes fail closed until explicit reauthorization; package matching stays exact.
- Test control stays emulator-only and separate: the ordinary fault receiver is signature-protected; cross-signer CI may use only the `emulatorE2e` Host-process allowlisted `DUMP` bridge, absent from production. Never reuse the inference bind surface for test control.
- Download, install, selection, verification, removal, health, benchmark, evaluation and validation remain distinct explicit user actions.
- Prompt/output stay out of saved state, normal telemetry/logs/reports. Persistent content exists only in the ADR-0017 Harnex-owned encrypted audit store with bounded retention. Never persist document URIs, signed URLs or private paths.
- Display only source-backed or explicitly unavailable values. Model removal requires confirmation and is blocked while runtime-owned; preserve valid store state on failure. SAF input is copied into private staging/content-addressed storage, never used as durable identity.
- UI state covers applicable loading, empty, populated, unavailable, warning, failure, cancellation and recovery states. Reusable design/accessibility semantics belong in `ui/design-system`.
- Emulator evidence may prove deterministic Binder authorization semantics; Play App Signing identity and representative ARM64/native/model/resource behavior remain REAL_ENVIRONMENT evidence.

## Change routing

- Reusable UI belongs in `ui/design-system`; runtime/model/telemetry/audit/evaluation/health/benchmark policy belongs in its owning module behind the smallest neutral contract.
- Binder/AIDL, generic caller authorization and caller-owned ledgers stay in transport/integration modules. This app owns proof-service composition, known-consumer discovery and explicit Harnex Control Plane authorization.
- Audit encryption/retention/transitions stay behind `InferenceAuditRepository`; UI never receives ciphertext, Room entities, Keystore handles or raw Binder caller objects.
- Keep immutable UI models and one process-scoped runtime graph across destinations, Activity recreation and Binder connections.
- Model flows test progress/success/cancellation/invalid state/source failure/cleanup/restart; destructive actions test confirmation/resource protection/privacy-safe terminal results.
- Async diagnostics test success/failure/stale-generation/lifecycle invalidation. Navigation tests compact/expanded destinations, back behavior and absence of side effects.

## Validation

Use repository selection first, then the narrowest sufficient app/owner gates. Common focused commands:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :integrations:android-service-host:testDebugUnitTest :integrations:android-service-host:lintDebug
./gradlew :apps:local-llm-phone-test:testDebugUnitTest :apps:local-llm-phone-test:lintDebug :apps:local-llm-phone-test:assembleDebug
python3 scripts/verify-android-packaging.py
```

Run applicable instrumentation/E2E when selected. A signed Play build and representative physical GGUF evidence are separate release gates; never infer Play identity or physical behavior from assembly/emulator success.

## Maintaining this guide

Update this guide when its ownership/routing or durable local invariants change, including shared-runtime trust/permissions/caller identity. Put detailed behavior in focused feature/UX/runbook docs and architecture boundaries in ADRs; do not record transient PR/version/completion state here. After edits run `python3 scripts/verify-agent-navigation.py`.
