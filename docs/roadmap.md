# Roadmap

This file is the authoritative source for current implementation status. Detailed target behavior and acceptance criteria remain in [`implementation-plan.md`](implementation-plan.md).

## Current execution status — August 2026

Pull request #13 and `agent/phase-1-consolidation` are the only active Phase 1 implementation line.

### Verified in clean cumulative GitHub Actions runs

Run #142 on commit `eea01c1492f1be66b1b0b6b6a2175e5a9de11e2f` completed successfully from a clean checkout. The current pull-request head must remain green after every subsequent documentation or tooling change.

- [x] coding-agent navigation and llama.cpp pin guards
- [x] native host configuration, compilation and tests
- [x] Spotless and ktlint formatting checks
- [x] Detekt static analysis and model-artifact repository guard
- [x] JVM unit tests
- [x] Android Lint for the debug and console internal variants
- [x] explicit assembly of all Android library, console and device-test variants
- [x] publication of one internal console APK, the device-runner APK, its instrumentation APK and eight AARs
- [x] publication of validation reports and the combined Android artifact-build log

### CI gate

- [x] complete one cumulative clean run that assembles and uploads every expected APK and AAR

The artifact build uses explicit module-scoped Gradle tasks rather than the root `assembleDebug` fan-out. Its combined Gradle output is persisted as `build/android-artifacts.log` in the `validation-reports` artifact so future Android build failures can be diagnosed from the same run.

### Branch and pull-request control

- [x] designate `agent/phase-1-consolidation` and PR #13 as the single active Phase 1 line
- [x] audit Phase 0 and Phase 1 branch ancestry and unique commits
- [x] confirm the historical native-runtime CPU-backend configuration is already present in the consolidated implementation
- [x] close superseded implementation PRs #8 and #12 with recovery notes
- [x] keep Dependabot infrastructure upgrades isolated from the functional consolidation
- [x] document branch, stacked-PR and merge discipline in [`BRANCHING.md`](../BRANCHING.md)
- [ ] delete superseded historical remote branches after #13 is merged and audited
- [ ] rebase or recreate dependency-only pull requests against the post-Phase-1 `main`

Historical branches are retained temporarily for traceability only and must not receive new implementation commits.

### Device evidence tooling

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

### Device evidence still required

- [ ] execute the complete lifecycle on a physical Android `arm64-v8a` device with a supported external GGUF
- [ ] verify cancellation during prefill and decode
- [ ] collect repeated load/unload and generation memory evidence
- [ ] confirm packaged JNI loading on representative devices
- [ ] record baseline latency, throughput, memory and thermal measurements
- [ ] attach or reference the resulting evidence archive from PR #13

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

### Implemented on the Phase 1 development line

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

### Consolidation gate

Phase 1 is functionally implemented but is not production-ready until all items below are complete.

- [x] create a root `AGENTS.md` navigation guide for coding agents
- [x] add a repository guard for agent-document links and module discoverability
- [x] document modularity and repository-wide Definition of Done
- [x] document a single canonical branch and PR workflow
- [x] close superseded functional implementation pull requests
- [x] add reproducible real-device test and evidence tooling
- [x] complete cumulative pull-request validation from a clean checkout
- [x] reconcile the Phase 1 branch with the latest `main` history
- [ ] run the end-to-end lifecycle on a real Android `arm64-v8a` device with a supported GGUF
- [ ] verify repeated load/unload and generation do not show unbounded memory growth
- [ ] verify cancellation during prefill and decode on device
- [ ] verify the packaged APK/AAR loads the expected JNI libraries on representative devices
- [ ] publish feature-level API and lifecycle documentation with minimal usage examples
- [ ] merge the consolidated Phase 1 work into `main`
- [ ] delete historical Phase 0/1 branches and refresh deferred dependency pull requests

The required device lifecycle is:

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

The executable procedure is documented in [`device-e2e-testing.md`](device-e2e-testing.md). Evidence collection and review are documented in [`device-e2e-evidence.md`](device-e2e-evidence.md).

## Phase 2 — observability and health

Begin only after the Phase 1 consolidation gate is complete.

- [ ] Room-backed telemetry store
- [ ] run timeline and structured log viewer
- [ ] cold/warm load, TTFT, prefill and decode metrics
- [ ] memory and thermal snapshots
- [ ] model integrity checks exposed through the control plane
- [ ] generation sanity suites
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
