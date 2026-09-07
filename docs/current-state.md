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
- Repository governance is aligned to `repo-template-sw` `0.10.0` with the local Android/local-AI/product-UI customizations recorded in `.engineering/baseline.json`.

## Integrated baseline

### Runtime, product and control plane

Harnex has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, verified GGUF installation, model/generation lifecycle, cancellation, scheduling, memory-pressure handling, model-aware planning, output constraints and presets. Product support is curated Qwen3.5 dense 0.8B/2B plus the reviewed 4B **4-bit-only** candidate tier; the 4B artifacts remain `CANDIDATE` pending exact-artifact representative-device runtime, memory, thermal and output-quality evidence.

A host-owned physical acceptance run on Samsung `SM-A566B` now proves the exact curated Qwen3.5 2B `Q4_K_M` artifact through the production Android/JNI/backend path with generation, active cancellation, five load/generate/unload cycles, bounded PSS growth and thermal capture. The run recorded TTFT `779 ms`, PSS growth `1,516 KB` and thermal `0 -> 0`. Its one-token sanity completion is not treated as representative decode-throughput evidence. Broader Q35-6 tuning, sustained performance, memory-pressure/switching and the 4B candidate tier remain open where separately required.

`apps/local-llm-phone-test` exposes Overview, Playground, Activity, Applications, Performance, Models, Diagnostics and Settings. Applications control-plane work is complete through ACUX-80 and CPREC-10..70; broader representative-device UX/runtime evidence remains.

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 tooling are integrated. The public Consumer SDK is now `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.11`; publication from the integrated Harnex candidate completed successfully, including unauthenticated downstream consumption from the public Maven repository.

ADR 0018 is the active production trust boundary for independently distributed consumers. The public Harnex service is explicitly bindable with no custom bind permission so Consumer-before-Host installation cannot permanently block reachability. Reachability is not authorization: authority remains fail-closed Binder UID -> exact installed package -> current signer -> Harnex Control Plane authorization -> enabled use case. Known external consumers are source-observed as `PENDING`; signer replacement becomes `SIGNATURE_CHANGED`; both require explicit user authorization.

Consumer SDK `disconnect()` is part of alpha.11 and supports reversible Settings-owned disconnect/reconnect without weakening Harnex authority. Emulator-only fault/control authority remains separate from production inference authority: the ordinary fault receiver is signature protected and the bounded shell bridge exists only in the Harnex `emulatorE2e` Host process.

### Cross-repository RedactGuard evidence

RedactGuard now consumes immutable Consumer SDK `0.1.0-alpha.11`. Its integrated `dev` baseline is independently signed from Harnex and exposes explicit Connect / Disconnect / Retry behavior in Settings while leaving authorization Harnex-owned.

Exact automated evidence is green for both cross-app paths:

- Consumer-first install -> Host absent -> later Harnex install without RedactGuard reinstall -> `PENDING` -> exact Harnex authorization -> Connect / Disconnect / Reconnect -> replacement signer denied as `SIGNATURE_CHANGED`;
- the complete Two-APK product/lifecycle/fault matrix, including ViewModel/Home continuity and Binder cancellation/process-loss/critical-pressure handling.

The release-package topology lane additionally exercises the production Harnex package identity with a distinct-signer consumer and proves fail-closed authorization plus observed signer replacement under deterministic emulator control. Applications and Application detail re-project source-observed package/signer identity on foreground resume so stale persisted authorization cannot overstate effective access.

Both current Harnex and RedactGuard candidates were published to Google Play Internal Testing and were physically exercised on 2026-09-07 with RedactGuard installed first, Harnex installed later without reinstalling RedactGuard, `PENDING` observation, explicit Harnex authorization, Connect / Disconnect / Reconnect and real consumer inference. Google Play Console reported the same Play App Signing SHA-256 digest for both current applications: `D6:2D:3C:C8:51:D5:72:05:C3:42:C1:7F:86:26:40:58:E3:FE:29:6A:AE:1B:0E:43:FD:AC:58:82:24:44:1A:BD`. This closes the focused physical install-order/authorization/connectivity check for the current Play topology; distinct-signer behavior remains proved by deterministic release-identity E2E rather than by these same-signer Play builds.

### Consumer API, OMBRA, evaluation and audit

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMB-6B identity approval, OMB-8 measured quality execution and physical evidence remain open. Model-evaluation work is integrated through EVAL-D-09 with later Android runner/persistence/comparison work continuing.

Local inference Activity/audit is integrated under ADR 0017: accepted inference history uses bounded encrypted app-private storage, verified Binder caller attribution and truthful restart reconciliation; normal telemetry/diagnostics stay content-free.

## Open blockers

### 1. Exact release-candidate identity and remaining SR-6 physical gates

The focused Play Internal install-order/authorization/connectivity retest is complete for the actual current Play App Signing topology. Stable promotion still requires the applicable Harness 0.5 release record to bind the final candidate source/build identity to the installed release artifact and to close any remaining SR-6 physical gates required by [`releases/harness-0.5.md`](releases/harness-0.5.md). The current same-signer Play topology must not be relabeled as physical distinct-signer evidence; that security boundary remains covered by deterministic release-identity integration evidence.

### 2. Remaining representative Android runtime evidence

The Samsung `SM-A566B` Qwen3.5 2B `Q4_K_M` physical acceptance closes exact-artifact generation, cancellation, repeated load/generate/unload bounded-memory sanity and thermal capture for that run. Remaining release/runtime evidence includes the applicable exact-release-source binding, LOW_MEMORY and cross-model/lifecycle gates, sustained cold/warm performance/resource measurements where claimed, and any other release-checklist physical observations not covered by this single-model run. The 4B 4-bit candidate tier remains explicitly uncertified until exact-artifact representative-device runtime, memory, thermal and output-quality evidence is recorded.

### 3. OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed artifact/configuration identities against policy v1. Model evaluation, Q35 device tuning, RAM/thermal evidence and LLUP continue independently where ownership does not conflict.

## Immediate next block

1. integrate the reconciled Play and Qwen3.5 2B physical evidence into the current `dev` baseline;
2. run a fresh release-gap review plus RELEASE/FULL validation against that exact `dev`, then execute only the remaining blocking real-environment gates required by the Harness 0.5 checklist;
3. promote reconciled `dev` to stable `main` only after those exact-candidate release gates pass; sustained public benchmarking and 4B candidate qualification continue as separate evidence work rather than being inferred from the 2B lifecycle run.

## Source links

- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Independent consumer authorization: [`adr/0018-independently-signed-consumer-authorization.md`](adr/0018-independently-signed-consumer-authorization.md)
- Play physical evidence: [`evidence/play-internal-2026-09-07.md`](evidence/play-internal-2026-09-07.md)
- Qwen3.5 2B physical evidence: [`qwen35/evidence/2026-09-07-sm-a566b-qwen35-2b-q4-k-m-physical-acceptance.md`](qwen35/evidence/2026-09-07-sm-a566b-qwen35-2b-q4-k-m-physical-acceptance.md)
- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Local inference audit: [`features/local-inference-activity-audit.md`](features/local-inference-activity-audit.md), [`adr/0017-durable-local-inference-audit.md`](adr/0017-durable-local-inference-audit.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- LLUP: [`workstreams/llama-cpp-v0-3-residency-qualification.md`](workstreams/llama-cpp-v0-3-residency-qualification.md)
