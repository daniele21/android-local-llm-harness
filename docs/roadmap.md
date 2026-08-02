# Roadmap

This file is the authoritative source for current implementation status. Detailed target behavior and acceptance criteria remain in [`implementation-plan.md`](implementation-plan.md).

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
- [x] CI artifact publication
- [x] CODEOWNERS, security, versioning and ADR foundations
- [x] model-binary repository guard
- [x] generated Gradle Wrapper committed and checksum-validated
- [x] clean CI validation completed

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

### Consolidation gate

Phase 1 is functionally implemented but is not production-ready until all items below are complete.

- [x] create a root `AGENTS.md` navigation guide for coding agents
- [x] add a repository guard for agent-document links and module discoverability
- [x] document modularity and repository-wide Definition of Done
- [ ] complete cumulative pull-request validation from a clean checkout
- [x] reconcile the Phase 1 branch with the latest `main` history
- [ ] run the end-to-end lifecycle on a real Android `arm64-v8a` device with a supported GGUF
- [ ] verify repeated load/unload and generation do not show unbounded memory growth
- [ ] verify cancellation during prefill and decode on device
- [ ] verify the packaged APK/AAR loads the expected JNI libraries on representative devices
- [ ] publish feature-level API and lifecycle documentation with minimal usage examples
- [ ] merge the consolidated Phase 1 work into `main`

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
