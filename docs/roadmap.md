# Roadmap

This file is the authoritative source for current implementation status. Detailed target behavior and acceptance criteria remain in [`implementation-plan.md`](implementation-plan.md).

## Current execution status — August 2026

Phase 1 was merged into `main` through pull request #13 at merge commit `6a7e4f6e2a6b7fa11484e8f57ff0a11053b52fbf`.

### Verified pre-merge GitHub Actions gate

Run #159 on commit `a0498504706241f4b518a1f8c9e1f843f0cc7351` completed successfully from a clean checkout immediately before merge.

- [x] coding-agent navigation and llama.cpp pin guards
- [x] shell and Python runner validation
- [x] native host configuration, compilation and tests
- [x] Spotless and ktlint formatting checks
- [x] Detekt static analysis and model-artifact repository guard
- [x] JVM unit tests
- [x] simulated Phase 1 acceptance lifecycle using the real content-addressed model store and runtime orchestrator
- [x] Android Lint for the debug and console internal variants
- [x] explicit assembly of all Android library, console and device-test variants
- [x] binary inspection of APK/AAR native packaging, ABI and ELF architecture
- [x] publication of one internal console APK, the device-runner APK, its instrumentation APK and eight AARs
- [x] publication of validation reports and the combined Android artifact-build log

### Pre-hardware merge gate

The repository merged Phase 1 after the complete simulated and packaging gate passed. This gate does not claim that physical-device behavior has been proven.

- [x] import a deterministic model fixture through the real `FileSystemModelStore`
- [x] verify SHA-256 identity, store lookup and model-store snapshot behavior
- [x] prepare and load through the real `RuntimeOrchestrator`
- [x] create a session and validate ordered streaming events
- [x] cancel an active generation and receive the typed cancelled terminal event
- [x] recover and complete a subsequent generation in the same runtime
- [x] close the session and release its context
- [x] unload an idle model through the Android memory-pressure policy
- [x] reload the model and complete idempotent runtime shutdown
- [x] build all APK/AAR variants from a clean checkout
- [x] verify the device application APK and llama.cpp AAR contain only the expected `arm64-v8a` libraries
- [x] verify every packaged native library is a 64-bit AArch64 ELF object
- [x] verify the instrumentation APK does not duplicate the native payload

These checks provide strong host-side evidence for orchestration, storage, cancellation, recovery and packaging. They cannot reproduce Android linker behavior, OEM memory management, real GGUF execution, thermal throttling or device-specific native failures.

### Branch and pull-request control

- [x] designate `agent/phase-1-consolidation` and PR #13 as the single Phase 1 implementation line
- [x] audit Phase 0 and Phase 1 branch ancestry and unique commits
- [x] confirm the historical native-runtime CPU-backend configuration is already present in the consolidated implementation
- [x] close superseded implementation PRs #8 and #12 with recovery notes
- [x] keep Dependabot infrastructure upgrades isolated from the functional consolidation
- [x] document branch, stacked-PR and merge discipline in [`BRANCHING.md`](../BRANCHING.md)
- [x] merge PR #13 into `main`
- [ ] delete superseded historical remote branches after the merge is audited
- [x] recreate the deferred `gradle/actions` dependency review against the post-Phase-1 `main` as PR #20

Historical branches are retained temporarily for traceability only and must not receive new implementation commits.

### Physical-device tooling

- [x] provide a physical-device runner that streams an external GGUF into app-private storage
- [x] provide a privacy-safe evidence wrapper that captures logs, metrics, APK hashes, JNI inventory and selected memory/thermal snapshots
- [x] document evidence-bundle interpretation and matrix requirements in [`device-e2e-evidence.md`](device-e2e-evidence.md)

Use:

```bash
bash scripts/capture-device-e2e-evidence.sh \
  --model /absolute/path/to/model.gguf \
  --architecture <architecture> \
  --quantization <quantization> \
  --memory-repeat 5
```

### Embedded API documentation

- [x] document public contracts, explicit model resolution and module responsibilities
- [x] provide minimal Android assembly, import, prepare, session and generation examples
- [x] document streaming events, cancellation, shutdown, model switching, memory pressure and typed failures
- [x] record current Phase 1 platform and integration limits in [`api-usage.md`](api-usage.md)

### Phase 2 first vertical slice

Pull request #21 establishes the persistent observability foundation without claiming completion of the entire phase.

- [x] add `observability/room-store` as the Android persistence boundary behind `TelemetryRepository`
- [x] persist generation run state across `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED` and `CANCELLED`
- [x] persist correlated, bounded structured logs without prompt or generated-output content
- [x] retain queue, model-load, TTFT, prefill, decode, token-count and throughput metrics
- [x] expose bounded run, request-timeline and health query APIs through stable contracts
- [x] keep Room writes serialized and non-blocking for the generation caller
- [x] make telemetry failures non-fatal to inference
- [x] add retention, ordering, mapping, lifecycle and privacy tests
- [x] compile and validate the Room AAR and Android test APK in the aggregate repository gate
- [x] document Room ownership, sandbox constraints and shutdown behavior in ADR 0001 and the embedded API guide

The separate console application cannot directly read another embedded application's private Room database. Cross-application viewing remains dependent on the signature-protected diagnostics bridge planned for Phase 3.

### Phase 2 second vertical slice

Pull request #23 adds the health control-plane foundation and the first concrete model-integrity check without claiming completion of generation sanity or cache-health suites.

- [x] add `observability/health-engine` as an independently testable orchestration boundary
- [x] register checks by stable, unique and non-blank IDs
- [x] run complete or targeted suites and aggregate the worst status
- [x] return explicit `NOT_RUN` results for unknown check IDs
- [x] measure check duration through an injectable monotonic clock
- [x] persist every result through `TelemetryRepository`
- [x] convert unexpected exceptions into privacy-safe failure summaries
- [x] verify every installed artifact through `ModelStore.verify()`
- [x] expose aggregate model-integrity outcomes without paths, bytes or verification details
- [x] add deterministic orchestration, persistence, privacy and integrity tests
- [x] validate Android Lint and the health-engine AAR in the aggregate repository gate
- [x] document API, threading, ownership and current limitations in [`health-engine.md`](health-engine.md)

### Phase 2 third vertical slice

Pull request #24 adds a backend-agnostic generation sanity check through the public `LocalLlmClient` lifecycle.

- [x] bind each sanity check explicitly to one application and use case
- [x] prepare the configured model, create a session, generate and close the session
- [x] support exact or substring output matching with configurable case sensitivity
- [x] apply deterministic temperature and seed overrides by default
- [x] bound generation with an explicit timeout and cancel timed-out requests cooperatively
- [x] expose typed runtime failures by stable error code only
- [x] keep prompts, generated output and backend exception messages out of persisted health details
- [x] treat session cleanup failure as a failed sanity check
- [x] add tests for success, mismatch, runtime failure, timeout, preparation failure and cleanup failure
- [x] validate formatting, static analysis, JVM tests, Android Lint, AAR assembly and native packaging
- [x] document configuration, lifecycle, threading, privacy and physical-device limitations in [`health-engine.md`](health-engine.md)

The repository gate validates the reusable sanity implementation with deterministic contract fakes. Execution against a physical Android device and a real GGUF remains part of the separate production-readiness gate.

### Post-merge production-readiness gate

Physical-device evidence remains mandatory before the runtime is called production-ready, released to application consumers or used as the baseline for device performance claims.

- [ ] execute the complete lifecycle on a physical Android `arm64-v8a` device with a supported external GGUF
- [ ] verify cancellation during prefill and decode
- [ ] collect repeated load/unload and generation memory evidence
- [ ] confirm runtime JNI loading on representative devices
- [ ] record baseline latency, throughput, memory and thermal measurements
- [ ] preserve or reference the resulting evidence archive from the release record

Deferring this gate permits continued repository development and integration work. It does not convert simulated results into hardware evidence.

## Phase 0 — repository foundation

- [x] Gradle multi-module structure
- [x] model-aware contracts
- [x] explicit app/use-case binding
- [x] GGUF profile contracts
- [x] llama.cpp JNI boundary stub
- [x] telemetry and dashboard contracts
- [x] developer console shell

### Repository hardening

- [x] centralized and pinned build versions
- [x] Spotless and ktlint formatting checks
- [x] Detekt CLI and Android Lint checks
- [x] debug, internal and release console variants
- [x] dependency locking configuration
- [x] CI artifact publication infrastructure
- [x] CODEOWNERS, security, versioning and ADR foundations
- [x] model-binary repository guard
- [x] generated Gradle Wrapper committed and checksum-validated
- [x] clean cumulative CI validation completed

## Phase 1 — functional embedded runtime

### Implemented

- [x] pin a llama.cpp commit and verify the pin in CI
- [x] compile the Android `arm64-v8a` CPU backend
- [x] inspect GGUF metadata without full model load
- [x] import and verify artifacts through SHA-256 content-addressed storage
- [x] load and unload models through opaque native handles
- [x] create and release contexts
- [x] run deterministic generation
- [x] stream aggregated text deltas
- [x] cancel queued and active generation cooperatively
- [x] serialize inference through a single-decode scheduler
- [x] support request priority and queue cancellation
- [x] orchestrate model, context, session and request lifecycle
- [x] reuse the active model when the resolved profile is compatible
- [x] reject unsafe model switches while active work owns the model
- [x] collect base runtime and generation metrics
- [x] handle Android background and low-memory signals
- [x] add Kotlin and native tests for the implemented behavior
- [x] add a real-device test application using the production store, runtime and llama.cpp backend
- [x] add an adb host runner that streams an external GGUF into app-private storage
- [x] add device tests for generation lifecycle, active cancellation and optional PSS regression cycles
- [x] add reproducible device-evidence capture without storing model, prompt, output or serial data
- [x] document the embedded API and lifecycle with minimal usage examples
- [x] add a simulated end-to-end acceptance lifecycle using the real store and runtime
- [x] add exact native packaging and AArch64 ELF verification

### Consolidation and merge gate

- [x] create a root `AGENTS.md` navigation guide for coding agents
- [x] add a repository guard for agent-document links and module discoverability
- [x] document modularity and repository-wide Definition of Done
- [x] document a single canonical branch and PR workflow
- [x] close superseded functional implementation pull requests
- [x] add reproducible real-device test and evidence tooling
- [x] complete cumulative pull-request validation from a clean checkout
- [x] reconcile the Phase 1 branch with the latest `main` history
- [x] pass the simulated lifecycle and post-cancellation recovery gate
- [x] validate exact APK/AAR native packaging and ELF architecture
- [x] publish feature-level API and lifecycle documentation with minimal usage examples
- [x] merge the consolidated Phase 1 work into `main`
- [ ] delete historical Phase 0/1 branches
- [x] refresh the deferred dependency review against current `main`

### Production-readiness gate

- [ ] run the end-to-end lifecycle on a real Android `arm64-v8a` device with a supported GGUF
- [ ] verify repeated load/unload and generation do not show unbounded memory growth
- [ ] verify cancellation during prefill and decode on device
- [ ] verify the packaged runtime loads the expected JNI libraries on representative devices
- [ ] record device-specific latency, throughput, memory and thermal baselines

The required physical-device lifecycle is:

```text
initialize
inspect
import
verify
load
create context
generate
stream
cancel
release context
unload
shutdown
```

The embedded API is documented in [`api-usage.md`](api-usage.md). The executable procedure is documented in [`device-e2e-testing.md`](device-e2e-testing.md). Evidence collection and review are documented in [`device-e2e-evidence.md`](device-e2e-evidence.md).

## Phase 2 — observability and health

Phase 2 repository work may begin after the Phase 1 merge. Production claims remain blocked by the physical-device gate above.

- [x] Room-backed telemetry store with bounded retention
- [x] persistent run lifecycle and request-correlated structured-log query APIs
- [ ] console run timeline and structured log viewer
- [x] queue, model-load, TTFT, prefill, decode, token-count and throughput metrics
- [ ] explicit cold-versus-warm load classification and comparison
- [ ] memory and thermal snapshots
- [x] model integrity checks exposed through the control plane
- [x] reusable health-suite orchestration and persisted result aggregation
- [x] generation sanity suites
- [ ] cache health suites
- [ ] benchmark baselines and regressions
- [ ] redacted diagnostic bundle export

## Phase 3 — integrations

- [ ] native sample application
- [ ] Capacitor plugin with aggregated token streaming
- [ ] app diagnostics bridge protected by signature permission
- [ ] downloadable/on-demand model source

## Phase 4 — shared runtime

- [ ] versioned AIDL contracts
- [ ] Binder transport
- [ ] central model store
- [ ] global scheduler and memory budget
- [ ] console app promoted to shared runtime host
