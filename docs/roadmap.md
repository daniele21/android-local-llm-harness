# Roadmap

## Phase 0 — repository foundation

- [x] Gradle multi-module structure
- [x] model-aware contracts
- [x] explicit app/use-case binding
- [x] GGUF profile contracts
- [x] llama.cpp JNI boundary stub
- [x] telemetry and dashboard contracts
- [x] developer console shell

## Phase 1 — functional embedded runtime

- [ ] pin a llama.cpp commit
- [ ] compile arm64-v8a CPU backend
- [ ] inspect GGUF metadata without full model load
- [ ] SHA-256 content-addressed model import
- [ ] model loading/unloading
- [ ] context creation and deterministic generation
- [ ] token streaming and cancellation
- [ ] single-decode scheduler
- [ ] runtime memory-pressure handling

## Phase 2 — observability and health

- [ ] Room-backed telemetry store
- [ ] run timeline and structured log viewer
- [ ] cold/warm load, TTFT, prefill and decode metrics
- [ ] memory and thermal snapshots
- [ ] model integrity checks
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
