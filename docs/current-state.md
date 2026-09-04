# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-04

This is the operational ledger for integrated state, blockers and immediate work. Capability history belongs in [`roadmap.md`](roadmap.md); milestone detail stays in its focused workstream/specification.

## Integration lines

- `dev` is the canonical base/target for ordinary work and Internal Testing candidates.
- `main` is the protected stable/release line.
- New work starts from the latest green `dev` unless explicitly hotfixed.

## Integrated baseline

### Runtime and models

The repository has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, GGUF inspection/verified installation, explicit model lifecycle, generation/streaming/cancellation, single-decode scheduling, memory-pressure handling, model-aware context planning, output constraints and versioned presets. Product support remains curated Qwen3.5 dense 0.8B/2B; exact artifact/runtime choice is Harnex-owned. Q35-1..5 are complete; Q35-6 still needs representative-device tuning evidence. See [`qwen35/README.md`](qwen35/README.md).

### Android product and control plane

`apps/local-llm-phone-test` exposes Overview, Playground, Applications, Performance, Models, Diagnostics and Settings over real repository sources. Public identity is **Harnex** — **“Your local AI harness for Android.”** Historical `Harness*`, package/Binder IDs and compatibility filenames remain technical identifiers.

Applications control-plane work is complete through ACUX-80 and CPREC-10..70. Startup reconciles mandatory built-ins before UI/Binder readers, preserves valid custom/default/disabled state and stays off the main thread. CPREC-80/90 and broader phone UX/runtime claims still require representative-device evidence. See [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md).

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 release-evidence tooling are integrated; representative physical SR-6 evidence remains. The Consumer boundary uses signature-protected Binder access, versioned Maven artifacts and durable logical jobs with explicit cancellation and exact prepared execution identity.

Background/process lifecycle hardening is integrated through durable logical jobs, detached execution ownership, explicit cancellation, exact prepared execution identity and started/foreground Host demand. HBG-42 reconciles stale persisted non-terminal jobs to `INTERRUPTED` across Host restart without claiming native work survives process death.

PR #527 is the active Harnex integration candidate for the final LAS automated lifecycle fixes. It preserves accepted cancellation when a concurrent backend error arrives, closes the Binder connection-loss ordering race, prevents stale endpoint failures from tearing down replacement registrations and makes the reusable Two-APK candidate build Host + Consumer SDK from one exact Harnex revision.

The executable runtime/Binder behavior of PR #527 was proven cross-repository by RedactGuard Two-APK #144: Host absence, cross-process product flow, ViewModel/Home continuity, Binder loss/reconnect without implicit cancellation, explicit cancellation, Host process loss/restart, critical-pressure interruption, RedactGuard process loss/privacy behavior and independent-consumer deterministic serialization are green on API 35. Emulator evidence remains emulator evidence and does not establish ARM64/JNI/GGUF/resource/OEM claims.

The production Consumer SDK candidate is now `0.1.0-alpha.10`. Publication is owned by `.github/workflows/publish-consumer-sdk.yml`: after exact-head integration validation and merge of PR #527 to `dev`, the workflow must publish alpha.10 successfully before downstream applications adopt it. See [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md).

### Consumer API, OMBRA and evaluation

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMBRA repository work includes document ingestion, deterministic analysis planning/validation, host-owned PII use-case policy, redaction/export, product UI, synthetic corpus v2 and pre-registered quality policy v1. OMB-6B identity approval, OMB-8 measured quality execution and physical two-APK evidence remain open. Canonical state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

Model-evaluation contracts/evaluators and core dataset pipeline are integrated through EVAL-D-09; Android import D-10 and runner/persistence/comparison/Performance work continue. See [`model-evaluation/README.md`](model-evaluation/README.md).

## Open blockers

### 1. LAS publication convergence

The automated lifecycle/fault/serialization matrix is complete. No additional Harnex runtime patch is indicated by the current automated evidence.

The remaining publication sequence is:

1. exact-head integration validation for PR #527;
2. merge PR #527 to `dev`;
3. publish `consumer-android:0.1.0-alpha.10` from that integrated Harnex source;
4. publish the corresponding Harnex phone-test candidate to Play Internal Testing;
5. update RedactGuard to consume alpha.10 and re-establish its exact-head/cross-repository evidence before its Internal Testing publication.

This sequence is required so the phone candidate does not combine a new Host with the older alpha.9 Consumer Binder client.

### 2. Representative Android evidence

LAS-07 and the remaining CRV/SR/Q35/phone resource claims require representative physical Android evidence. LAS-07 specifically requires a physical `arm64-v8a` device, the production JNI/llama.cpp path, a real compatible GGUF and the exact Harnex/RedactGuard candidate identities. Physical memory/thermal/OEM observations remain distinct from emulator evidence.

Play Internal builds are useful for on-device product/runtime testing, but the canonical two-APK LAS-07 Binder journey still requires signer identity to satisfy the signature-protected shared-runtime permission. If Play-installed packages do not share the same app-signing certificate, use the repository-owned same-signer release-APK runbook instead of treating Play installation as equivalent evidence.

### 3. OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed Qwen3.5 artifact/configuration identities against policy v1 without lowering thresholds to fit results. Remaining parallel work includes representative RAM/thermal/device restoration evidence, model evaluation and the LLUP upgrade stream where ownership does not conflict.

## Immediate next block

1. complete exact-head automated validation for PR #527 with Consumer SDK alpha.10 metadata;
2. integrate PR #527 to `dev` and verify both real Consumer SDK alpha.10 publication and Harnex Play Internal publication;
3. update RedactGuard to alpha.10 in one coherent dependency/documentation change and rerun affected deterministic + Two-APK evidence;
4. integrate the validated RedactGuard candidate to `dev` and verify its Play Internal publication;
5. execute LAS-07 on representative physical hardware, keeping Play/device-product evidence separate from the canonical same-signer Binder claim when app-signing certificates differ;
6. continue OMB-6B, OMB-8, evaluation and LLUP independently where ownership does not conflict.

## Source links

- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- Harnex 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)
