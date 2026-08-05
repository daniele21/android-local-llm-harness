# Roadmap

This file is the authoritative source for current implementation status. Detailed target behavior and acceptance criteria remain in [`implementation-plan.md`](implementation-plan.md).

## Current execution status — August 2026

The functional runtime, observability, connected phone console, model distribution and retained benchmark history are implemented. Harness 0.5.0 is now following the protected `dev` integration plan: `dev` is the ordinary development line, while `main` remains stable and release-oriented.

The active sequence is governance and cumulative CI, focused model-management recovery, real Android branding, Compose architecture and surface completion, UI/accessibility validation, then signed internal distribution and physical-device evidence.

The repository remains **not production-ready** until the representative physical-device GGUF evidence gate is completed.

## Repository and branch control

- [x] keep `main` as the protected stable and release-oriented line
- [x] create `dev` from the restored green repository baseline
- [x] make `dev` the documented base and target for ordinary work
- [x] add automated rejection of ordinary pull requests opened directly to `main`
- [x] add cumulative Android and native validation after merges to `dev`
- [x] add complete non-scoped validation and packaging for `dev -> main` promotions
- [x] document hotfix, forward-port, merge and rollback behavior in ADR 0008
- [ ] apply repository-level protection to `dev` and verify direct pushes are rejected
- [ ] delete historical remote branches after their unique commits and recovery notes are audited

Historical branches are read-only audit references. They must not receive new implementation commits and must never be used as the base for new feature work.

## Harness 0.5.0 integration sequence

- [x] restore the green repository baseline and protect `main` through PR #55
- [x] establish the `dev` branch and correct its curated-model-catalog Detekt regression through PR #58
- [x] implement repository-owned `dev` validation, promotion gates, target-branch policy, PR template and integration ADR
- [ ] apply the equivalent repository ruleset to `dev`
- [ ] align, validate and squash-merge the focused model-management recovery in PR #53
- [ ] close PR #34 as superseded after auditing its unique behavior
- [ ] integrate real Android launcher, themed-icon and Compose brand assets
- [ ] complete Navigation Compose, ViewModel/UDF and the five primary product surfaces
- [ ] add Compose UI, screenshot, accessibility and responsive-layout evidence
- [ ] build and upload the signed AAB to Google Play Internal Testing
- [ ] capture privacy-safe representative physical-device GGUF evidence
- [ ] promote the validated `dev` candidate to `main` with a merge commit and tag Harness 0.5.0 only after the applicable release gates pass

## Verified repository gate

The cumulative validation gate covers:

- [x] coding-agent navigation and llama.cpp pin guards
- [x] repository-wide Android fan-out for public runtime and telemetry contract changes
- [x] shell and Python runner validation
- [x] native host configuration, compilation and tests
- [x] Spotless and ktlint formatting checks
- [x] Detekt static analysis and model-artifact repository guard
- [x] JVM unit tests
- [x] simulated Phase 1 acceptance lifecycle using the real content-addressed model store and runtime orchestrator
- [x] Android Lint
- [x] explicit APK, AAB and AAR assembly
- [x] binary inspection of native packaging, ABI and ELF architecture
- [x] Room schema and repository tests
- [x] health-engine tests
- [x] resource-observability tests
- [x] benchmark-engine tests

These checks provide host-side and simulated evidence. They do not prove Android linker behavior on representative physical devices, OEM memory management, real-device thermal throttling or device-specific native stability.

## Phase 0 — repository foundation

- [x] Gradle multi-module structure
- [x] model-aware contracts
- [x] explicit app/use-case binding
- [x] GGUF profile contracts
- [x] telemetry and dashboard contracts
- [x] developer console shell
- [x] centralized and pinned build versions
- [x] Spotless and ktlint
- [x] Detekt and Android Lint
- [x] dependency locking
- [x] CI artifact publication
- [x] CODEOWNERS, security, versioning and ADR foundations
- [x] model-binary repository guard
- [x] committed and checksum-validated Gradle Wrapper

## Phase 1 — functional embedded runtime

### Runtime and model lifecycle

- [x] pin and verify a specific `llama.cpp` commit
- [x] compile the Android `arm64-v8a` CPU backend
- [x] inspect GGUF metadata without full model load
- [x] import models through streaming SHA-256 content-addressed storage
- [x] deduplicate and verify model artifacts
- [x] load and unload models through opaque native handles
- [x] create and release contexts
- [x] run deterministic generation
- [x] stream aggregated text deltas
- [x] cancel queued and active generation cooperatively
- [x] serialize inference through a single-decode scheduler
- [x] support request priority and queue cancellation
- [x] orchestrate model, context, session and request lifecycle
- [x] reuse a compatible loaded model
- [x] reject unsafe model switches while active work owns the model
- [x] handle Android background and low-memory signals
- [x] recover after cancellation and request failure

### Validation and developer tooling

- [x] Kotlin and native tests
- [x] simulated end-to-end acceptance lifecycle
- [x] exact APK/AAR native packaging and AArch64 ELF verification
- [x] physical-device test application using production store, runtime and backend
- [x] ADB host runner for streaming an external GGUF into app-private storage
- [x] device tests for lifecycle, cancellation and optional PSS regression cycles
- [x] privacy-safe device-evidence capture tooling
- [x] embedded API and lifecycle documentation
- [x] ARM64 emulator preflight with a real Qwen3 GGUF on PR #28
- [x] Play-installable launcher app for physical-device validation without developer mode on PR #29
- [x] Storage Access Framework GGUF selection and private content-addressed import in the phone-test app
- [x] copyable and shareable privacy-safe PASS/FAIL report in the phone-test app
- [x] external PKCS12 upload-key and macOS Keychain signing workflow
- [ ] signed Play internal-testing release installed on a physical phone

The clean ARM64 emulator run is recorded in [`emulator-e2e-results.md`](emulator-e2e-results.md). It validates the AVD execution path but does not satisfy any item in the physical-device production-readiness gate below.

### Physical-device production-readiness gate

- [ ] install the signed phone-test AAB through Google Play internal testing on representative Android hardware
- [ ] execute the complete lifecycle on representative physical Android `arm64-v8a` devices
- [ ] inspect and run a supported external GGUF through the real JNI backend
- [ ] verify cancellation during both prefill and decode
- [ ] verify repeated load/generate/unload cycles do not show unbounded memory growth
- [ ] confirm packaged JNI loading on representative devices
- [ ] record latency, throughput, PSS and thermal baselines
- [ ] preserve or reference the privacy-safe evidence archive in a release record

Required lifecycle:

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

Use [`device-e2e-testing.md`](device-e2e-testing.md), [`device-e2e-evidence.md`](device-e2e-evidence.md) and [`play-internal-phone-test.md`](play-internal-phone-test.md) for execution and evidence requirements.

## Phase 2 — observability and health

### Persistent telemetry — PR #21

- [x] Room-backed telemetry store with bounded retention
- [x] in-memory implementation for deterministic tests and ephemeral use
- [x] persistent run states: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`
- [x] request-correlated structured logs
- [x] queue, model-load, TTFT, prefill, decode, token-count and throughput metrics
- [x] serialized non-blocking Room writes
- [x] telemetry failures isolated from inference
- [x] prompt and generated-output exclusion from normal telemetry
- [x] query APIs for runs, request timelines, logs and health results

### Health control plane and model integrity — PR #23

- [x] independently testable `observability/health-engine`
- [x] stable and unique health-check IDs
- [x] complete or targeted suite execution
- [x] worst-status aggregation
- [x] explicit `NOT_RUN` for unknown check IDs
- [x] duration measurement through an injectable monotonic clock
- [x] persisted privacy-safe results
- [x] aggregate installed-model integrity check through `ModelStore.verify()`
- [x] unexpected exception isolation

### Generation sanity — PR #24 and selective PR #22 recovery

- [x] bind each sanity check to an explicit application and use case
- [x] execute `prepare`, session creation, generation and cleanup through `LocalLlmClient`
- [x] deterministic temperature and seed defaults
- [x] bounded timeout and cooperative cancellation
- [x] typed runtime failures without backend-message persistence
- [x] privacy-safe assertion failures without output disclosure
- [x] exact output assertion
- [x] required substring assertion
- [x] non-empty output assertion
- [x] forbidden substring assertion
- [x] regular-expression assertion
- [x] case-sensitive and case-insensitive matching
- [x] invalid regex rejection before generation
- [x] cleanup failure treated as a failed health check
- [ ] physical-device execution with a real GGUF

The alternative `HealthControlPlane`, multi-fixture DTO set and granular model-integrity implementation from PR #22 are not merged. They overlap with the current HealthEngine architecture and remain historical reference material only.

### Model-integrity cache health — PR #25

- [x] neutral `CacheHealthProbe` and `CacheHealthSnapshot` contracts
- [x] runtime-owned probe over the actual injected `ModelIntegrityCache`
- [x] healthy, stale and orphaned cache classification
- [x] observational and non-mutating snapshots
- [x] privacy-safe aggregate health results
- [x] re-hash a previously verified artifact after its file stamp changes
- [ ] health contracts for future tokenizer caches
- [ ] health contracts for future prompt/template caches
- [ ] health contracts for future KV/context caches
- [ ] health contracts for future downloaded-model caches

### Resource observability and load classification — PR #26

- [x] explicit `COLD`, `WARM` and `UNKNOWN` model-load classification
- [x] record model-load duration only for genuinely cold sessions
- [x] process PSS snapshots
- [x] native heap snapshots
- [x] Java heap usage snapshots
- [x] available-memory and Android low-memory state
- [x] Android thermal-status mapping
- [x] nullable unavailable measurements rather than invented zero values
- [x] explicit caller-driven capture with no hidden timer
- [x] bounded in-memory and Room retention
- [x] non-destructive Room schema migration
- [ ] physical-device memory and thermal evidence under a real GGUF workload

### Benchmark baselines and regressions — PR #27 and PR #33

- [x] benchmark key by application, use case, model digest and cold/warm classification
- [x] reject `UNKNOWN` classification for baseline creation
- [x] median TTFT
- [x] nearest-rank p95 TTFT
- [x] median total latency
- [x] nearest-rank p95 total latency
- [x] median decode throughput
- [x] persisted active baseline storage in memory and Room
- [x] non-destructive Room schema migration
- [x] post-baseline regression comparison
- [x] `WARN` for missing baseline or insufficient samples
- [x] `FAIL` for policy regressions
- [x] privacy-safe metric-class summaries
- [x] bounded retained baseline history in memory and Room
- [x] separate active baseline and immutable history semantics
- [x] non-destructive Room schema 3→4 migration that seeds existing active baselines into history
- [x] structured comparison results with values, ratios, thresholds and metric regression flags
- [x] partial non-actionable comparison previews before minimum sample readiness
- [ ] physical-device baseline collection on representative devices

### Console observability, controls and cache repair — PR #31

- [x] tabbed console navigation for overview, installed models, active runtime, runs, logs, health, caches, resources and benchmarks
- [x] `ConsoleDataSource` boundary over existing observability contracts
- [x] runtime-state provider boundary independent from runtime, Room, Binder and backend types
- [x] runtime adapter over the public `LocalLlmClient.runtimeSnapshot()` contract
- [x] model-inventory provider boundary over the existing content-addressed `ModelStore.snapshot()` contract
- [x] model-store adapter that removes private backing-file paths before presentation
- [x] explicit disconnected, connected-empty and source-failure inventory states
- [x] active installed-model correlation against the runtime snapshot
- [x] standalone wiring limited to the console application's private `FileSystemModelStore`
- [x] bounded run, log and resource queries
- [x] privacy-safe disconnected and telemetry-failure states
- [x] read-only generation metric cards
- [x] read-only structured-log cards with deterministic field ordering
- [x] read-only health, resource and benchmark cards
- [x] selectable request detail from generation-run and correlated-log cards
- [x] request-scoped run and structured-log queries
- [x] chronological request timeline with event sequence and run-relative offsets
- [x] privacy-safe missing-run, empty-timeline and source-error states
- [x] process PSS, native heap and Java heap trend chart
- [x] available-device-memory trend chart
- [x] discrete Android thermal-pressure chart with low-memory signal count
- [x] nullable measurements and unknown thermal states rendered as gaps rather than zero values
- [x] chart rendering from persisted explicit captures without timers or hidden polling
- [x] `ConsoleHealthControl` execution boundary over the existing `HealthEngine`
- [x] explicit run-all and targeted per-check actions
- [x] standalone `ModelIntegrityHealthCheck` execution against the console sandbox store
- [x] automatic targeted controls for registered generation-sanity check IDs
- [x] health execution outside the Android main thread through a single-thread executor
- [x] action disabling while a health suite is in progress
- [x] persisted-result refresh through the existing telemetry repository
- [x] fixed privacy-safe health-control failure states
- [x] neutral `CacheMaintenanceControl` contract separate from observational cache probes
- [x] `ConsoleCacheControl` discovery and targeted-repair boundary
- [x] independent cache-source loading and privacy-safe failure isolation
- [x] disconnected, connected-empty, healthy, unhealthy and unavailable cache states
- [x] repair actions exposed only for unhealthy caches with a registered maintenance capability
- [x] runtime-owned model-integrity repair that revalidates stale entries and removes orphaned or invalid entries
- [x] failed revalidation remains visible as an unresolved stale entry
- [x] cache actions executed outside the Android main thread and disabled while running
- [x] before/after, revalidated, removed and failed repair counts
- [x] standalone cache control remains explicitly disconnected because the console does not own the runtime cache
- [x] fixed privacy-safe cache-health and cache-repair error states
- [x] refresh and back navigation without implicit runtime, model or cache mutation
- [x] pure Kotlin presenter, data-source, adapter, health-control, cache-control and chart-model tests
- [x] Android controls and custom-view compilation, lint and packaging validation
- [x] explicit documentation of standalone sandbox, runtime-contract, health-capability, cache-ownership and resource-history limitations
- [ ] connect the standalone console to a real cross-application diagnostics source

### Benchmark regression and baseline history console — selectively recovered through PR #51

- [x] stacked branch and draft PR based on PR #31
- [x] `BenchmarkComparisonEvaluator` shared by health checks and console presentation
- [x] comparison lookback independent from the visible Runs limit
- [x] active-key and PASS/WARN/FAIL summary
- [x] baseline/current metric values, ratios and policy thresholds
- [x] explicit ready, preview, regression, within-policy and unavailable states
- [x] retained baseline cards with active-capture identification
- [x] chronological median TTFT history chart
- [x] chronological p95 total-latency history chart
- [x] chronological median decode-throughput history chart
- [x] nullable history metrics rendered as gaps rather than zero values
- [x] in-memory and Room active/history persistence tests
- [x] evaluator, data-source and presenter tests
- [x] Spotless, Detekt, Android Lint, compilation and packaging validation
- [x] recover the current retained-history behavior through PR #51 without reviving the legacy standalone console

### Legacy explicit model management console — PR #34

- [x] stacked branch and draft PR based on PR #31
- [x] `ConsoleModelControl` boundary over existing `ModelStore` import, verify and remove operations
- [x] observational inventory kept separate from mutating capabilities
- [x] Storage Access Framework GGUF selection and private staging
- [x] streaming SHA-256 and provider-size validation before import
- [x] existing content-addressed store reused for digest, size, deduplication and conflict checks
- [x] import, verification and removal outside the Android main thread
- [x] model actions disabled while an operation is active
- [x] explicit confirmation before removal
- [x] loaded-model removal blocked in presenter and control layers when runtime identity is available
- [x] fixed privacy-safe import, verification, removal and source failures
- [x] staged-file cleanup after success and failure
- [x] control, presenter and data-source tests
- [x] Spotless, Detekt, Android Lint, compilation and packaging validation
- [ ] close PR #34 as superseded after the focused PR #53 recovery is merged into `dev`

### Manual inference playground — PR #35

- [x] stacked branch and draft PR based on PR #31
- [x] `ConsoleInferenceControl` boundary over the public `LocalLlmClient` contract
- [x] explicit application/use-case target registration supplied by the embedding application
- [x] one-shot prepare, session creation and generation lifecycle
- [x] target, prompt, maximum-output-token, temperature and seed request dialog
- [x] queued, started, streaming, completed, failed and cancelled state mapping
- [x] bounded 131,072-character in-memory output with explicit truncation state
- [x] queue, load, TTFT, prefill, decode, total, token and throughput metric presentation
- [x] cooperative cancellation through the runtime `GenerationHandle`
- [x] race-safe synchronous terminal and cancellation callbacks
- [x] session cleanup after completed, failed and cancelled terminal events
- [x] cleanup failure overrides the terminal result with a privacy-safe failure state
- [x] prompts excluded from console snapshots, telemetry and saved state
- [x] generated output retained only in bounded in-memory playground state
- [x] standalone console remains explicitly disconnected because it owns no configured runtime targets
- [x] control, presenter and data-source tests
- [x] Spotless, Detekt, Android Lint, compilation and packaging validation
- [ ] rebase or retarget onto `main` after PR #31 is merged

### Remaining Phase 2 work

- [ ] privacy-redacted diagnostic bundle export
- [ ] signature-protected diagnostics bridge for cross-application console access

## Phase 3 — integrations

- [ ] production-oriented native Android SDK adapter
- [ ] native sample application
- [ ] Capacitor plugin with aggregated token streaming
- [ ] Capacitor cancellation and typed error mapping
- [ ] Capacitor sample application
- [ ] reusable production Android `content://` model import adapter
- [ ] streamed HTTP/on-demand model source
- [ ] signature-protected app diagnostics bridge

## Phase 4 — shared runtime

- [ ] versioned AIDL contracts
- [ ] Binder transport
- [ ] shared service lifecycle
- [ ] caller authentication and signature permissions
- [ ] central model store
- [ ] cross-application artifact deduplication
- [ ] global scheduler
- [ ] global memory budget and application quotas
- [ ] console application promoted to shared runtime host
