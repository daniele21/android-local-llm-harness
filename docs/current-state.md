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
- `main` is the stable/release line.
- New work starts from the latest green `dev` unless explicitly hotfixed.
- The 2026-09-04 Harnex release promotion is complete and the resulting `main` merge commit has been synchronized back into `dev` per ADR 0008.

## Integrated baseline

### Runtime and models

The repository has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, GGUF inspection/verified installation, explicit model lifecycle, generation/streaming/cancellation, single-decode scheduling, memory-pressure handling, model-aware context planning, output constraints and versioned presets. Product support remains curated Qwen3.5 dense 0.8B/2B; exact artifact/runtime choice is Harnex-owned. Q35-1..5 are complete; Q35-6 still needs representative-device tuning evidence. See [`qwen35/README.md`](qwen35/README.md).

### Android product and control plane

`apps/local-llm-phone-test` exposes Overview, Playground, Applications, Performance, Models, Diagnostics and Settings over real repository sources. Public identity is **Harnex** — **“Your local AI harness for Android.”** Historical `Harness*`, package/Binder IDs and compatibility filenames remain technical identifiers.

Applications control-plane work is complete through ACUX-80 and CPREC-10..70. Startup reconciles mandatory built-ins before UI/Binder readers, preserves valid custom/default/disabled state and stays off the main thread. CPREC-80/90 and broader phone UX/runtime claims still require representative-device evidence. See [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md).

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 release-evidence tooling are integrated; representative physical SR-6 evidence remains. The Consumer boundary uses signature-protected Binder access, versioned Maven artifacts and durable logical jobs with explicit cancellation and exact prepared execution identity.

Background/process lifecycle hardening is integrated through durable logical jobs, detached execution ownership, explicit cancellation, exact prepared execution identity and started/foreground Host demand. HBG-42 reconciles stale persisted non-terminal jobs to `INTERRUPTED` across Host restart without claiming native work survives process death.

The final LAS runtime/Binder fixes are integrated from source identity `6b34fe9fcba70f6b8abd107fd58b61c418ac737d`. They preserve accepted cancellation when a concurrent backend error arrives, close Binder connection-loss ordering races, prevent stale endpoint failures from tearing down replacement registrations and make the reusable Two-APK candidate build Host + Consumer SDK from one exact Harnex revision.

The public Consumer SDK `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.10` is published successfully from that integrated source. The corresponding Harnex phone-test is also published to Google Play Internal Testing.

### Cross-repository RedactGuard evidence

RedactGuard consumes alpha.10 and the validated product baseline from source identity `0e329c49e8ce5985b3677e9ca5566bc3cb6f3b96` is now on its stable `main` line.

The final RedactGuard PR candidate passed FULL integration validation and the complete API 35 Two-APK lifecycle/fault/serialization matrix against Harnex `6b34fe9f...`. Exact integrated RedactGuard source also passed `Validate` push run #949 and Play Internal publication run #4; the subsequent direct `dev -> main` promotion passed RELEASE/FULL Validate #953 before merge.

That automated evidence covers Host absence, cross-process product flow, ViewModel/Home continuity, Binder loss/reconnect without implicit cancellation, explicit cancellation, Host process loss/restart, critical-pressure interruption, RedactGuard process-loss privacy behavior and independent-consumer deterministic serialization.

A representative manual product run has additionally confirmed the real Android application works end to end. This is product acceptance evidence, not a replacement for the formal ARM64/JNI/GGUF/resource identity bundle where those stronger claims are required.

### Stable release promotion

The validated Harnex baseline was promoted to `main` through PR #530 after RELEASE/FULL Validate #3834, Package Android Artifacts #507, Consumer SDK validation #360, model-distribution #539, evaluation-dataset #147, evaluation-persistence #151, repository-health #899, documentation #1459 and native-host validation were green on the exact promotion head.

The resulting `main` merge commit was synchronized back into `dev` through PR #531, restoring explicit shared ancestry for the next development cycle. The corresponding RedactGuard release was promoted through PR #195 and synchronized back to its `dev` through PR #196.

### Consumer API, OMBRA and evaluation

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMBRA repository work includes document ingestion, deterministic analysis planning/validation, host-owned PII use-case policy, redaction/export, product UI, synthetic corpus v2 and pre-registered quality policy v1. OMB-6B identity approval, OMB-8 measured quality execution and physical two-APK evidence remain open. Canonical state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

Model-evaluation contracts/evaluators and core dataset pipeline are integrated through EVAL-D-09; Android import D-10 and runner/persistence/comparison/Performance work continue. See [`model-evaluation/README.md`](model-evaluation/README.md).

## Open blockers

### 1. Representative Android evidence

LAS-07 and the remaining CRV/SR/Q35/phone resource claims require representative physical Android evidence. LAS-07 specifically requires a physical `arm64-v8a` device, the production JNI/llama.cpp path, a real compatible GGUF and exact Harnex/RedactGuard candidate identities. Physical memory/thermal/OEM observations remain distinct from emulator evidence.

A successful ordinary manual app run is useful product acceptance evidence, but does not automatically satisfy every LAS-07 identity/scenario requirement. Play Internal builds are useful for on-device testing, while the canonical same-signer two-APK Binder claim still depends on verified signer identity.

### 2. OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed Qwen3.5 artifact/configuration identities against policy v1 without lowering thresholds to fit results. Remaining parallel work includes representative RAM/thermal/device restoration evidence, model evaluation and the [LLUP residency-qualification workstream](workstreams/llama-cpp-v0-3-residency-qualification.md) where ownership does not conflict.

## Immediate next block

1. execute LAS-07 only for the representative physical claims it genuinely owns, retaining exact source/APK/model/device identity;
2. continue OMB-6B and OMB-8 from the now-stable cross-repository baseline;
3. continue model evaluation, Q35 device tuning, RAM/thermal evidence and LLUP independently where ownership does not conflict;
4. keep future release tags/artifacts tied to exact validated `main` commits and preserve the ADR 0008 `main -> dev` synchronization after promotions.

## Source links

- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- LLUP / llama.cpp residency qualification: [`workstreams/llama-cpp-v0-3-residency-qualification.md`](workstreams/llama-cpp-v0-3-residency-qualification.md)
- Harnex 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)
