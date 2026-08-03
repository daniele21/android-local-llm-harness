# Roadmap

This file is the authoritative source for current implementation status. Detailed target behavior and acceptance criteria remain in [`implementation-plan.md`](implementation-plan.md).

## Current execution status — August 2026

Phase 1 is merged into `main` through pull request #13. Phase 2 has progressed through persistent telemetry, health checks, generation sanity, cache health, Android resource observability and benchmark regression checks.

The current `main` head contains the work merged through pull request #27. Repository validation run #269 completed successfully on that line.

The repository is merge-ready for continued development but is **not production-ready** until the physical-device GGUF evidence gate is completed.

## Repository and branch control

- [x] use `main` as the canonical integrated implementation line
- [x] consolidate the complete Phase 1 runtime through PR #13
- [x] close superseded Phase 1 implementation PRs #8 and #12
- [x] keep dependency-only changes separate from functional runtime work
- [x] document branch and pull-request discipline in [`BRANCHING.md`](../BRANCHING.md)
- [x] supersede the alternative Phase 2 health-control-plane line in PR #22
- [x] recover only compatible sanity-assertion behavior on a fresh branch from current `main`
- [ ] delete historical remote branches after their unique commits and recovery notes are audited

Historical branches are read-only audit references. They must not receive new implementation commits and must never be used as the base for new feature work.

## Verified repository gate

The cumulative validation gate covers:

- [x] coding-agent navigation and llama.cpp pin guards
- [x] shell and Python runner validation
- [x] native host configuration, compilation and tests
- [x] Spotless and ktlint formatting checks
- [x] Detekt static analysis and model-artifact repository guard
- [x] JVM unit tests
- [x] simulated Phase 1 acceptance lifecycle using the real content-addressed model store and runtime orchestrator
- [x] Android Lint
- [x] explicit APK and AAR assembly
- [x] binary inspection of native packaging, ABI and ELF architecture
- [x] Room schema and repository tests
- [x] health-engine tests
- [x] resource-observability tests
- [x] benchmark-engine tests

These checks provide host-side and simulated evidence. They do not prove Android linker behavior, OEM memory management, real GGUF execution, thermal throttling or device-specific native stability.

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
- [x] adb host runner for streaming an external GGUF into app-private storage
- [x] device tests for lifecycle, cancellation and optional PSS regression cycles
- [x] privacy-safe device-evidence capture tooling
- [x] embedded API and lifecycle documentation

### Physical-device production-readiness gate

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

Use [`device-e2e-testing.md`](device-e2e-testing.md) and [`device-e2e-evidence.md`](device-e2e-evidence.md) for execution and evidence requirements.

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

### Benchmark baselines and regressions — PR #27

- [x] benchmark key by application, use case, model digest and cold/warm classification
- [x] reject `UNKNOWN` classification for baseline creation
- [x] median TTFT
- [x] nearest-rank p95 TTFT
- [x] median total latency
- [x] nearest-rank p95 total latency
- [x] median decode throughput
- [x] persisted baseline storage in memory and Room
- [x] non-destructive Room schema migration
- [x] post-baseline regression comparison
- [x] `WARN` for missing baseline or insufficient samples
- [x] `FAIL` for policy regressions
- [x] privacy-safe metric-class summaries
- [ ] physical-device baseline collection on representative devices
- [ ] baseline history beyond the current active baseline per key

### Remaining Phase 2 work

- [ ] console run timeline
- [ ] structured-log viewer
- [ ] request detail view
- [ ] installed-model and active-runtime views
- [ ] health and sanity execution controls
- [ ] resource and thermal charts
- [ ] cache-health view and repair actions
- [ ] benchmark baseline and regression views
- [ ] privacy-redacted diagnostic bundle export
- [ ] signature-protected diagnostics bridge for cross-application console access

## Phase 3 — integrations

- [ ] production-oriented native Android SDK adapter
- [ ] native sample application
- [ ] Capacitor plugin with aggregated token streaming
- [ ] Capacitor cancellation and typed error mapping
- [ ] Capacitor sample application
- [ ] Android `content://` model import source
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
