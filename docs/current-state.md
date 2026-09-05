# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-05

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

`apps/local-llm-phone-test` exposes Overview, Playground, Activity, Applications, Performance, Models, Diagnostics and Settings over real repository sources. Public identity is **Harnex** — **“Your local AI harness for Android.”** Historical `Harness*`, package/Binder IDs and compatibility filenames remain technical identifiers.

Applications control-plane work is complete through ACUX-80 and CPREC-10..70. Startup reconciles mandatory built-ins before UI/Binder readers, preserves valid custom/default/disabled state and stays off the main thread. CPREC-80/90 and broader phone UX/runtime claims still require representative-device evidence. See [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md).

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 release-evidence tooling are integrated; representative physical SR-6 evidence remains. The previously published Consumer boundary uses the original ADR 0012 same-signer/signature-permission trust shape, versioned Maven artifacts and durable logical jobs with explicit cancellation and exact prepared execution identity.

A development correction is now defined by ADR 0018: independently distributed consumers must not share Harnex signing credentials. The candidate replaces the inference gate with the `BIND_LOCAL_LLM` normal capability permission while retaining fail-closed Binder UID -> exact installed package -> signer -> Harnex Control Plane authorization. Known external consumers are source-observed as `PENDING`; signing identity replacement becomes `SIGNATURE_CHANGED`; both require explicit user authorization before live access. Emulator fault/control authority remains separately signature-protected.

Background/process lifecycle hardening is integrated through durable logical jobs, detached execution ownership, explicit cancellation, exact prepared execution identity and started/foreground Host demand. HBG-42 reconciles stale persisted non-terminal jobs to `INTERRUPTED` across Host restart without claiming native work survives process death.

The final LAS runtime/Binder fixes are integrated from source identity `6b34fe9fcba70f6b8abd107fd58b61c418ac737d`. They preserve accepted cancellation when a concurrent backend error arrives, close Binder connection-loss ordering races, prevent stale endpoint failures from tearing down replacement registrations and make the reusable Two-APK candidate build Host + Consumer SDK from one exact Harnex revision.

The public Consumer SDK `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.10` remains the currently published integrated artifact. The independent-signer correction adds reusable `disconnect()` and is versioned as candidate `0.1.0-alpha.11`; it must not be described as published until exact validated `dev` publication succeeds.

### Cross-repository RedactGuard evidence

The integrated RedactGuard stable baseline consumes alpha.10. Its earlier automated same-key E2E was valid for that test topology but did not represent separate Play App Signing identities. A physical Play Internal run exposed that limitation when Harnex and RedactGuard were installed with distinct Play signers and RedactGuard was denied before runtime use.

The current cross-repository correction therefore requires exact-candidate distinct-signer automation: build Harnex + candidate Consumer SDK and RedactGuard from recorded revisions, sign Host and consumer with different ephemeral identities, prove denial while RedactGuard is pending, explicitly authorize the exact source-observed RedactGuard identity from Harnex-owned authority, then prove connect/disconnect/reconnect. Physical Play Internal confirmation remains separate REAL_ENVIRONMENT evidence for the actual distribution signer identities.

The previous final RedactGuard PR candidate passed FULL integration validation and the complete API 35 Two-APK lifecycle/fault/serialization matrix against Harnex `6b34fe9f...`. Exact integrated RedactGuard source also passed its stable promotion gates. Those historical results remain evidence for the earlier baseline but cannot establish the new independent-signer trust claim.

### Stable release promotion

The validated Harnex baseline was promoted to `main` through PR #530 after RELEASE/FULL Validate #3834, Package Android Artifacts #507, Consumer SDK validation #360, model-distribution #539, evaluation-dataset #147, evaluation-persistence #151, repository-health #899, documentation #1459 and native-host validation were green on the exact promotion head.

The resulting `main` merge commit was synchronized back into `dev` through PR #531, restoring explicit shared ancestry for the next development cycle. The corresponding RedactGuard release was promoted through PR #195 and synchronized back to its `dev` through PR #196.

### Consumer API, OMBRA and evaluation

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMBRA repository work includes document ingestion, deterministic analysis planning/validation, host-owned PII use-case policy, redaction/export, product UI, synthetic corpus v2 and pre-registered quality policy v1. OMB-6B identity approval, OMB-8 measured quality execution and physical two-APK evidence remain open. Canonical state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

Model-evaluation contracts/evaluators and core dataset pipeline are integrated through EVAL-D-09; Android import D-10 and runner/persistence/comparison/Performance work continue. See [`model-evaluation/README.md`](model-evaluation/README.md).

### Local inference activity and audit

Local inference Activity/audit is implemented as the Harnex-owned durable history for accepted local inference. Sensitive input, rendered effective prompt, answer/reasoning and metrics are persisted in bounded encrypted app-private storage; normal telemetry, structured logs and diagnostics export stay content-free. External records use verified Binder caller attribution, internal Playground generation uses `HARNEX_INTERNAL`, and restart reconciliation converts orphaned non-terminal records to truthful `INTERRUPTED` state.

The API 35 exact-candidate acceptance matrix covers RedactGuard `COMPLETED` persistence across Host restart, explicit `CANCELLED`, deterministic backend `FAILED`, Host process-loss `INTERRUPTED / HOST_PROCESS_LOSS`, and a completed Harnex-internal generation. Cross-process evidence exports metadata and sensitive-field presence only, not sensitive values. Durable behavior is owned by [`features/local-inference-activity-audit.md`](features/local-inference-activity-audit.md) and ADR 0017.

## Open blockers

### 1. Independent Play signing topology

Before the independent-consumer correction can be considered integration-ready, exact deterministic gates must pass on the final Harnex candidate and the RedactGuard cross-repository E2E must prove distinct signers, fail-closed pending authorization, exact identity approval and reusable connect/disconnect/reconnect. After merge/publication, RedactGuard must consume the immutable alpha.11 artifact and pass its own exact-head validation.

Before any stable promotion claim, Play Internal builds must then be retested on a physical Android device so the actual Harnex and RedactGuard Play App Signing identities — the environment that exposed the original mismatch — are represented truthfully.

### 2. Representative Android runtime evidence

LAS-07 and the remaining CRV/SR/Q35/phone resource claims require representative physical Android evidence. LAS-07 specifically requires a physical `arm64-v8a` device, the production JNI/llama.cpp path, a real compatible GGUF and exact Harnex/RedactGuard candidate identities. Physical memory/thermal/OEM observations remain distinct from emulator evidence.

A successful ordinary manual app run is useful product acceptance evidence, but does not automatically satisfy every LAS-07 identity/scenario requirement. Play Internal builds are useful for on-device distribution testing; same-publisher fixture evidence and independently signed consumer evidence are now treated as separate trust topologies under ADR 0018.

### 3. OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed Qwen3.5 artifact/configuration identities against policy v1 without lowering thresholds to fit results. Remaining parallel work includes representative RAM/thermal/device restoration evidence, model evaluation and the [LLUP residency-qualification workstream](workstreams/llama-cpp-v0-3-residency-qualification.md) where ownership does not conflict.

## Immediate next block

1. close the ADR 0018 independent-signer implementation with exact Harnex validation and distinct-signer RedactGuard E2E;
2. publish the validated Consumer SDK alpha.11 from integrated `dev`, then update RedactGuard to that immutable artifact and validate/merge its connection-management UX;
3. publish both Internal Testing candidates and execute the focused physical Play signer/authorization/connectivity retest before any `dev -> main` promotion;
4. continue LAS-07, OMB-6B/OMB-8, model evaluation, Q35 device tuning, RAM/thermal evidence and LLUP independently where ownership does not conflict.

## Source links

- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Independent consumer authorization: [`adr/0018-independently-signed-consumer-authorization.md`](adr/0018-independently-signed-consumer-authorization.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Local inference activity/audit: [`features/local-inference-activity-audit.md`](features/local-inference-activity-audit.md), [`adr/0017-durable-local-inference-audit.md`](adr/0017-durable-local-inference-audit.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- LLUP / llama.cpp residency qualification: [`workstreams/llama-cpp-v0-3-residency-qualification.md`](workstreams/llama-cpp-v0-3-residency-qualification.md)
- Harnex 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)