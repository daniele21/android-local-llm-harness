# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-07

This is the operational ledger for integrated state, blockers and immediate work. Capability history belongs in [`roadmap.md`](roadmap.md); milestone detail stays in focused workstreams.

## Integration lines

- `dev` is the canonical base/target for ordinary work and Internal Testing candidates.
- `main` is the stable/release line.
- New work starts from the latest green `dev` unless explicitly hotfixed.
- Stable promotions preserve `main` ancestry in `dev` before the next `dev -> main` release cycle, per ADR 0008.
- Repository governance is aligned to `repo-template-sw` `0.11.0` with proportional product-development routing plus the local Android/local-AI/product-UI customizations recorded in `.engineering/baseline.json`.

## Integrated baseline

### Runtime, product and control plane

Harnex has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, verified GGUF installation, model/generation lifecycle, cancellation, scheduling, memory-pressure handling, model-aware planning, output constraints and presets. Product support is curated Qwen3.5 dense 0.8B/2B plus the reviewed 4B **4-bit-only** candidate tier; the 4B artifacts remain `CANDIDATE` pending exact-artifact representative-device runtime, memory, thermal and output-quality evidence. Q35-6 still needs representative-device tuning evidence.

`apps/local-llm-phone-test` exposes Overview, Playground, Activity, Applications, Performance, Models, Diagnostics and Settings. Applications control-plane work is complete through ACUX-80 and CPREC-10..70; broader representative-device UX/runtime evidence remains.

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 tooling are integrated. The public Consumer SDK is now `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.11`; publication from the integrated Harnex candidate completed successfully, including unauthenticated downstream consumption from the public Maven repository.

ADR 0018 is the active trust boundary for independently distributed consumers. The public Harnex service is explicitly bindable with no custom bind permission so Consumer-before-Host installation cannot permanently block reachability. Reachability is not authorization: authority remains fail-closed Binder UID -> exact installed package -> current signer -> Harnex Control Plane authorization -> enabled use case. Known external consumers are source-observed as `PENDING`; signer replacement becomes `SIGNATURE_CHANGED`; both require explicit user authorization.

Consumer SDK `disconnect()` is part of alpha.11 and supports reversible Settings-owned disconnect/reconnect without weakening Harnex authority. Emulator-only fault/control authority remains separate from production inference authority: the ordinary fault receiver is signature protected and the bounded shell bridge exists only in the Harnex `emulatorE2e` Host process.

### Cross-repository RedactGuard evidence

RedactGuard now consumes immutable Consumer SDK `0.1.0-alpha.11` and exposes explicit Connect / Disconnect / Retry behavior in Settings while leaving authorization Harnex-owned.

Exact automated evidence is green for both cross-app paths:

- Consumer-first install -> Host absent -> later Harnex install without RedactGuard reinstall -> `PENDING` -> exact Harnex authorization -> Connect / Disconnect / Reconnect -> replacement signer denied as `SIGNATURE_CHANGED`;
- the complete Two-APK product/lifecycle/fault matrix, including ViewModel/Home continuity and Binder cancellation/process-loss/critical-pressure handling.

The focused physical Play Internal run also confirms the real current distribution journey: RedactGuard-first, Harnex-later without reinstall, source-observed `PENDING`, exact authorization, Connect / Disconnect / Reconnect and representative production Consumer SDK/Binder/local-inference execution all work on device.

However, the Play signing metadata collected for that physical run reports the **same current Play App Signing SHA-256 digest for Harnex and RedactGuard**. The physical run therefore proves the actual same-signer Play topology; it does not prove the repository-declared distinct-signer REAL_ENVIRONMENT topology. Dedicated deterministic emulator lanes continue to prove cross-signer authorization semantics, but emulator evidence must not be relabeled as the required physical signer confirmation.

The commits added after the published/runtime-qualified candidates are limited to documentation, repository governance, verification scripts and workflow-policy surfaces; no Harnex app/runtime source or Android build configuration changed. This preserves the applicability of the functional physical journey but does not close the signer-fidelity gap above.

### Consumer API, OMBRA, evaluation and audit

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMB-6B identity approval, OMB-8 measured quality execution and physical evidence remain open. Model-evaluation work is integrated through EVAL-D-09 with later Android runner/persistence/comparison work continuing.

Local inference Activity/audit is integrated under ADR 0017: accepted inference history uses bounded encrypted app-private storage, verified Binder caller attribution and truthful restart reconciliation; normal telemetry/diagnostics stay content-free.

## Open blockers

### 1. Play distinct-signer release topology

The current physical Play pair uses the same signing certificate, while the current release/evidence contract declares a distinct-signer production confirmation as blocking. Stable promotion must not silently reinterpret that requirement.

Close this in one of two legitimate ways:

- configure/obtain distinct Play App Signing identities for the two applications and rerun the focused physical journey; or
- if same-signer first-party distribution is intentionally the product topology, deliberately reshape the product/security/evidence contract and affected ADR/E2E/runbook claims before promotion, while retaining deterministic proof that third-party/distinct-signer Consumers remain supported and fail closed correctly.

### 2. Representative Android runtime evidence

LAS-07 and remaining CRV/SR/Q35/resource claims require representative physical Android evidence with exact candidate, production JNI/llama.cpp path and compatible GGUF where applicable. The new 4B 4-bit candidate tier is explicitly part of this evidence gap; catalog admission does not certify runtime suitability. Memory, thermal and OEM observations remain distinct from deterministic emulator evidence.

### 3. OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed artifact/configuration identities against policy v1. Model evaluation, Q35 device tuning, RAM/thermal evidence and LLUP continue independently where ownership does not conflict.

## Immediate next block

1. resolve the Play signer-topology decision above without weakening or reinterpreting the current blocking release contract merely to promote;
2. once the applicable signer evidence/contract is truthful and complete, rerun RELEASE/FULL on the resulting exact `dev` HEAD against live `main`;
3. promote reconciled `dev` to stable `main` only with all applicable blocking release evidence complete;
4. continue the independent ARM64/GGUF/runtime/resource/evaluation evidence workstreams without relabeling emulator evidence as physical proof.

## Source links

- Product strategy / decision boundaries: [`product.md`](product.md)
- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Independent consumer authorization: [`adr/0018-independently-signed-consumer-authorization.md`](adr/0018-independently-signed-consumer-authorization.md)
- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Local inference audit: [`features/local-inference-activity-audit.md`](features/local-inference-activity-audit.md), [`adr/0017-durable-local-inference-audit.md`](adr/0017-durable-local-inference-audit.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- LLUP: [`workstreams/llama-cpp-v0-3-residency-qualification.md`](workstreams/llama-cpp-v0-3-residency-qualification.md)
