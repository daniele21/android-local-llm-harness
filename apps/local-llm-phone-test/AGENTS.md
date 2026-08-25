# Connected Phone Test App — Coding Agent Guide

## Scope

This guide applies to `apps/local-llm-phone-test/**` and supplements the repository-wide [`AGENTS.md`](../../AGENTS.md). It covers the connected Compose app, process-scoped runtime composition, local model distribution/management, Performance and Diagnostics presentation, the shared-runtime proof host and the Play-installed physical-device validation surface.

The app orchestrates existing domain contracts. It must not become an alternate owner of runtime, model-installation, telemetry, evaluation, Binder protocol or benchmark policy.

## Navigation

Read the active product state before UI or orchestration work:

- [`current-state.md`](../../docs/current-state.md) and the active [`dev-integration-and-harness-0.5-plan.md`](../../docs/dev-integration-and-harness-0.5-plan.md);
- [`shared-runtime/roadmap.md`](../../docs/shared-runtime/roadmap.md), [`shared-runtime/workstreams/host-service.md`](../../docs/shared-runtime/workstreams/host-service.md) and ADR 0012 for proof-host service work;
- [`harness-ux-ui-implementation-plan.md`](../../docs/harness-ux-ui-implementation-plan.md) and [`harness-ux-ui-implementation-progress.md`](../../docs/harness-ux-ui-implementation-progress.md) for Compose structure and remaining evidence;
- [`model-evaluation/README.md`](../../docs/model-evaluation/README.md) for Performance evaluation contracts and sequencing;
- [`design-system.md`](../../docs/design-system.md) and the shared `ui/design-system` sources for reusable UI;
- [`model-management-phone.md`](../../docs/model-management-phone.md) and [`phone-model-distribution.md`](../../docs/phone-model-distribution.md) for model flows;
- [`play-internal-phone-test.md`](../../docs/play-internal-phone-test.md) for release installation and manual validation.

Route by responsibility:

| Concern | Start here | Owning dependency to inspect |
| --- | --- | --- |
| App/process composition | `HarnessRuntimeGraph.kt`, `MainActivity.kt` | Runtime, transport, observability and design-system contracts |
| Shared-runtime proof host | `HarnessSharedRuntimeService.kt`, `HarnessSharedRuntimePolicy.kt`, manifest/build variants | `integrations/android-service-host`, Binder contract and ADR 0012 |
| Destinations and responsive shell | `HarnessDestination.kt`, Compose entry points | Shared navigation/components under `ui/design-system` |
| Playground state and inference | `HarnessViewModel.kt`, `PhonePlaygroundController.kt` and related UI | `LocalLlmClient`, runtime lifecycle and generation contracts |
| Performance evaluation UI | `PerformanceViewModel.kt`, `PerformanceScreen.kt`, presentation helpers | `evaluation/*` contracts, persistence/comparison and telemetry evidence |
| Catalog/download/install UI | `PhoneModelDistributionController.kt`, actions and UI | The [`models` guide](../../models/AGENTS.md) and each stage's contracts |
| Installed selection, verification and removal | `PhoneModelManagementControl.kt`, metadata store | ModelStore ownership and runtime loaded-model identity |
| Health, logs, resources and benchmarks | `HarnessViewModel.kt`, `Harness*Source.kt`, Diagnostics UI | The [`observability` guide](../../observability/AGENTS.md) |
| Physical validation and report | `PhoneTestController.kt`, models and UI | Device/evidence docs and production runtime/backend |
| Version, manifest, signing and packaging | `version.properties`, `build.gradle.kts`, manifest/resources | Release script and Play runbook |

Use focused searches before editing the large app surface:

```bash
rg '<state-or-action>' apps/local-llm-phone-test/src/main apps/local-llm-phone-test/src/test
rg 'RuntimeOrchestrator|ModelStore|TelemetryRepository|Executor' apps/local-llm-phone-test/src/main
rg --files apps/local-llm-phone-test/src/main/kotlin apps/local-llm-phone-test/src/test apps/local-llm-phone-test/src/androidTest
```

## Local invariants

- Keep `MainActivity` as a composition/Activity Result/lifecycle effect boundary, not an owner of domain policy or long-lived mutable render state. Do not add to controller/state ownership debt; move state behind the ViewModel in coherent vertical slices.
- Screens depend on ViewModels/controllers and neutral contracts; they do not call `RuntimeOrchestrator`, `ModelStore`, repositories or executors directly.
- Diagnostics resource history is renderable `HarnessUiState`; Compose must not call `HarnessResourceSource.history()` during rendering.
- Health, resource and benchmark asynchronous actions use ViewModel-owned generation tokens. A stale callback must fail closed, and Activity teardown must invalidate outstanding diagnostic generations before executor/controller teardown.
- Navigation, opening a screen and observational refresh do not implicitly load a model, start inference/evaluation, download, install, run health, capture resources, mutate a baseline or repair a cache.
- Performance may present only source-backed evaluation state. It must not rank models/configurations while compatible aggregated latency, throughput, memory and quality evidence is unavailable.
- The shared-runtime proof `Service` reuses `HarnessRuntimeGraph.from(...)`; binding/handshake must not create a second runtime, select a model or load a GGUF.
- Shared-runtime release and debug variants use deterministic, distinct signature-permission names. Caller package matching is exact; never strip application ID suffixes to authorize a caller.
- Download, install, selection, verification, removal, health, benchmark, evaluation and validation are distinct explicit user actions.
- Prompt and generated output stay bounded in process memory and out of saved state, Room, normal telemetry and shared reports.
- Document URIs, signed/download URLs and private filesystem paths never appear in persisted metadata, UI diagnostics or shareable reports.
- Every displayed value is source-backed or explicitly unavailable; illustrative mockup values must never appear as live data.
- Model removal requires confirmation and must be blocked while the runtime owns the model. Failure must preserve valid store objects and metadata when possible.
- Storage Access Framework input streams into private staging/content-addressed storage; never treat a document URI as durable model identity.
- UI state covers loading, empty, populated, unavailable, warning, failure, cancellation and recovery where applicable.
- Shared design tokens/components and accessibility semantics belong in `ui/design-system`, not duplicated locally.
- Emulator and host results are preflight only. Only a Play-installed or ADB-captured physical-device run supports device evidence, and only for its exact matrix entry.

## Change routing

- Move reusable visual tokens/components to `ui/design-system`; keep app-specific composition and data mapping here.
- Move runtime, model, telemetry, evaluation, health or benchmark policy to the owning module and expose the smallest neutral contract the app needs.
- Keep shared-runtime Binder/AIDL, caller authorization and caller-owned ledgers in their transport/integration modules; the phone app owns only the concrete proof service and explicit host configuration.
- Keep UI models immutable and separate observation from mutating capabilities.
- Preserve a single process-scoped runtime graph across destinations and the proof service; do not create a runtime per screen, Activity recreation or Binder connection.
- For model flows, test progress plus success, cancellation, invalid state, source failure, cleanup and restart reconciliation.
- For destructive actions, require explicit confirmation, active-resource protection and a privacy-safe terminal result.
- For asynchronous Diagnostics actions, test success/failure plus stale-generation and lifecycle invalidation behavior.
- For navigation, test compact/expanded destinations, back behavior and absence of side effects.

## Validation

Run the connected app unit, UI, lint and packaging checks appropriate to the change:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :integrations:android-service-host:testDebugUnitTest \
  :integrations:android-service-host:lintDebug \
  :integrations:android-service-host:assembleDebug
./gradlew :apps:local-llm-phone-test:compileDebugKotlin \
  :apps:local-llm-phone-test:compileDebugUnitTestKotlin \
  :apps:local-llm-phone-test:testDebugUnitTest \
  :apps:local-llm-phone-test:lintDebug \
  :apps:local-llm-phone-test:assembleDebug
LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE=true \
  ./gradlew :apps:local-llm-phone-test:bundleRelease
./gradlew :backends:llama-cpp:assembleDebug \
  :apps:device-test-runner:assembleDebug \
  :apps:device-test-runner:assembleDebugAndroidTest
python3 scripts/verify-android-packaging.py
```

When an emulator or physical device is available, run the applicable instrumentation/visual check. A signed Play build and representative physical GGUF evidence remain separate release gates; never infer them from successful assembly or emulator tests.

## Maintaining this guide

Update this file in the same change when:

- composition roots, screen state ownership, navigation or destination structure change;
- model distribution/management, evaluation, observability or validation orchestration moves between classes;
- a new direct domain dependency or capability is introduced;
- privacy, persistence, destructive-action or implicit-side-effect rules change;
- design-system ownership or accessibility expectations change;
- app unit, instrumentation, lint, bundle, signing or evidence commands change.

Update the root guide only when the app's repository-level responsibility or cross-domain routing changes. Update the focused UX/model/runbook document for behavior details, architecture/ADR docs for durable boundaries, and current state/roadmap for implementation or evidence status. Do not put transient release version, PR or completion status here.

After editing, run from the repository root:

```bash
python3 scripts/verify-agent-navigation.py
```
