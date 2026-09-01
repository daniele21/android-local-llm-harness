# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-01

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

Background/process lifecycle hardening is active in PR #502 and ADR 0016. The Harness candidate now provides caller/use-case-scoped durable `ConsumerInferenceJobId` state, idempotent submit, authoritative query/result after reconnect, explicit cancel, exact prepared `ConsumerExecutionIdentity` pinning, detached session/generation-handle ownership and started/foreground service demand while durable work is active. Setup resolution remains protocol minor 5; logical jobs are minor 6. Consumer SDK candidate `0.1.0-alpha.8` has a frozen ABI baseline. Remaining work is RedactGuard logical-job consumption, host-process restart reconciliation, dedicated lifecycle E2E and physical evidence. See [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md).

### Consumer API, OMBRA and evaluation

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMBRA repository work includes document ingestion, deterministic analysis planning/validation, host-owned PII use-case policy, redaction/export, product UI, synthetic corpus v2 and pre-registered quality policy v1. OMB-6B identity approval, OMB-8 measured quality execution and physical two-APK evidence remain open. Canonical state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

Model-evaluation contracts/evaluators and core dataset pipeline are integrated through EVAL-D-09; Android import D-10 and runner/persistence/comparison/Performance work continue. See [`model-evaluation/README.md`](model-evaluation/README.md).

## Open blockers

### 1. Background/process lifecycle convergence

PR #502 implements the Harness durable-job/service candidate, but end-to-end continuity is not complete. Required sequence: exact-head automated preflight; merge to `dev`; publish Consumer SDK `0.1.0-alpha.8`; migrate RedactGuard multi-chunk generation to stable Harness logical jobs; prove app switch, Activity recreation, Binder disconnect/reconnect and explicit cancel through deterministic two-APK journeys with privacy-safe state plus screenshots/video; then record same-signer ARM64/JNI/GGUF/OEM/model-residency evidence. Host process death remains a truthful interruption boundary.

### 2. OMBRA completion

OMB-6B remains review-gated; green automation does not imply visual approval. OMB-8 must execute reviewed Qwen3.5 artifact/configuration identities against policy v1 without lowering thresholds to fit results. Physical/release claims remain separate from CI/emulator evidence.

### 3. Representative Android evidence

CRV-110, CPREC-80/90, SR-6, Q35-6, HBG-64 and remaining phone UX/resource claims require representative physical evidence. Sessions may be combined where practical, but each acceptance gate remains independently recorded; emulator evidence is never promoted into ARM64/native/model/OEM claims.

### 4. Follow-on hardening

Remaining parallel work includes RAM warm-idle/device restoration evidence, Q35-7 lifecycle/memory/thermal validation, model evaluation and the [`LLUP`](workstreams/llama-cpp-v0-3-residency-qualification.md) upgrade stream where ownership does not conflict.

## Immediate next block

1. close #502 on a documentation-current exact head with required CI and `/preflight strong`;
2. merge #502 to `dev` and verify GitHub Packages publishes `0.1.0-alpha.8` from that integrated source;
3. bump RedactGuard to alpha.8 and complete LAS-08B logical-job reconciliation without duplicate generation;
4. add LAS-08C two-APK lifecycle journeys with screenshots/video before real-device validation;
5. execute remaining ARM64/JNI/GGUF/model-memory/OEM evidence;
6. continue OMB-6B, OMB-8, evaluation and LLUP independently.

## Source links

- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- Harnex 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)
