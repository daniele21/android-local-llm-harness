# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-02

This is the operational ledger for integrated state, blockers and immediate work. Capability history belongs in [`roadmap.md`](roadmap.md); milestone detail stays in its focused workstream/specification.

## Integration lines

- `dev` is the canonical base/target for ordinary work.
- `main` is the protected stable/release line.
- New work starts from the latest green `dev` unless explicitly hotfixed.

## Integrated baseline

### Runtime and models

The repository has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, GGUF inspection/verified installation, explicit model lifecycle, generation/streaming/cancellation, single-decode scheduling, memory-pressure handling, model-aware context planning, output constraints and versioned presets. Product support remains curated Qwen3.5 dense 0.8B/2B; exact artifact/runtime choice is Harnex-owned. Q35-1..5 are complete; Q35-6 still needs representative-device tuning evidence. See [`qwen35/README.md`](qwen35/README.md).

### Android product and control plane

`apps/local-llm-phone-test` exposes Overview, Playground, Applications, Performance, Models, Diagnostics and Settings over real repository sources. Public identity is **Harnex** — **“Your local AI harness for Android.”** Historical `Harness*`, package/Binder IDs and compatibility filenames remain technical identifiers.

Applications control-plane work is complete through ACUX-80 and CPREC-10..70. Startup reconciles mandatory built-ins before UI/Binder readers, preserves valid custom/default/disabled state and stays off the main thread. CPREC-80/90 and broader phone UX/runtime claims still require representative-device evidence. See [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md).

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 release-evidence tooling are integrated; representative physical SR-6 evidence remains. CRV repository implementation is complete through its automated candidate gate, while CRV-110 remains the frozen same-signer real-GGUF physical gate. See [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md) and [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

Background/process lifecycle hardening is integrated through the core durable-job/service boundary. PR #502 provides caller/use-case-scoped durable `ConsumerInferenceJobId`, idempotent submit, authoritative query/result after reconnect, explicit cancel, exact prepared `ConsumerExecutionIdentity` pinning, detached session/generation-handle ownership and started/foreground service demand while durable work is active. Setup resolution remains protocol minor 5 and logical jobs protocol minor 6. PR #510 supplies the protected emulator fault gate used by downstream disconnect/rebind tests. PR #511 fixes concrete Consumer setup-resolution forwarding through Binder. Consumer SDK `0.1.0-alpha.9` is published from the integrated `dev` line at `f86b53ad29d2396660f095d5eaadd41c19bda8c7`.

RedactGuard has consumed logical jobs and alpha.9 on its active LAS candidate. Remaining lifecycle work is Host process-restart reconciliation/retry semantics, deterministic downstream lifecycle completion and representative physical evidence. See [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md).

### Consumer API, OMBRA and evaluation

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMBRA repository work includes document ingestion, deterministic analysis planning/validation, host-owned PII use-case policy, redaction/export, product UI, synthetic corpus v2 and pre-registered quality policy v1. OMB-6B identity approval, OMB-8 measured quality execution and physical two-APK evidence remain open. Canonical state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

Model-evaluation contracts/evaluators and core dataset pipeline are integrated through EVAL-D-09; Android import D-10 and runner/persistence/comparison/Performance work continue. See [`model-evaluation/README.md`](model-evaluation/README.md).

## Open blockers

### 1. Background/process lifecycle convergence

The durable-job/Binder/service foundation is no longer waiting on RedactGuard migration: RedactGuard's LAS candidate consumes the logical-job API and contains app-switch/ViewModel/Binder lifecycle journeys. The current downstream exact Two-APK run `33594812860` fails **before those lifecycle assertions**. It successfully builds/installs the Harnex Host, passes the Host-absent scenario, negotiates protocol minor 6 with `consumer-setup-resolution-v1`, and validates the assigned use case plus published preset; RedactGuard then remains at generic `INCOMPATIBLE` and emits no setup-resolution diagnostic event.

Current RedactGuard source analysis shows its diagnostic reason formatting can mask the original typed `ConsumerControlPlaneFailure`. Therefore the current failure is not sufficient evidence of a Harnex model-store/setup-resolver/runtime defect. RedactGuard must first preserve the typed setup-resolution outcome and rerun the exact journey; only that result may route a new Harnex functional fix. Do not seed a different model or weaken setup criteria to make the downstream E2E pass.

Independent Harnex work remains: reconcile stale non-terminal logical-job metadata after actual Host process restart so impossible native work becomes interrupted/process-lost, then define privacy-safe retry-attempt semantics. After the initial downstream setup gate is healthy, complete disconnect/reconnect/cancel lifecycle evidence, residency composition evidence and final physical validation.

### 2. OMBRA completion

OMB-6B remains review-gated; green automation does not imply visual approval. OMB-8 must execute reviewed Qwen3.5 artifact/configuration identities against policy v1 without lowering thresholds to fit results. Physical/release claims remain separate from CI/emulator evidence.

### 3. Representative Android evidence

CRV-110, CPREC-80/90, SR-6, Q35-6, HBG-64 and remaining phone UX/resource claims require representative physical evidence. Sessions may be combined where practical, but each acceptance gate remains independently recorded; emulator evidence is never promoted into ARM64/native/model/OEM claims.

### 4. Follow-on hardening

Remaining parallel work includes RAM warm-idle/device restoration evidence, Q35-7 lifecycle/memory/thermal validation, model evaluation and the [`LLUP`](workstreams/llama-cpp-v0-3-residency-qualification.md) upgrade stream where ownership does not conflict.

## Immediate next block

1. continue independent Harnex HBG-42 Host process-restart reconciliation from current `dev`, with STRONG selector-driven validation;
2. on RedactGuard, make setup diagnostics non-interfering and preserve the typed Consumer setup-resolution code;
3. rerun the exact Two-APK setup path and route the first typed outcome to its canonical owner; create a Harnex corrective slice only if that evidence points to Harnex;
4. after setup resolution reaches the lifecycle journey, complete deterministic app-switch, Activity recreation, Binder disconnect/reconnect, explicit-cancel and process-loss evidence;
5. finish HBG retry/residency evidence and exact-head automated preflight;
6. execute remaining ARM64/JNI/GGUF/model-memory/OEM evidence;
7. continue OMB-6B, OMB-8, evaluation and LLUP independently where ownership does not conflict.

## Source links

- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- Harnex 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)
