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

ADR 0018 is the active trust boundary for consumer applications regardless of whether a particular first-party pair currently shares a signing lineage. The public Harnex service is explicitly bindable with no custom bind permission so Consumer-before-Host installation cannot permanently block reachability. Reachability is not authorization: authority remains fail-closed Binder UID -> exact installed package -> current signer -> Harnex Control Plane authorization -> enabled use case. Known external consumers are source-observed as `PENDING`; signer replacement becomes `SIGNATURE_CHANGED`; both require explicit user authorization.

Consumer SDK `disconnect()` is part of alpha.11 and supports reversible Settings-owned disconnect/reconnect without weakening Harnex authority. Emulator-only fault/control authority remains separate from production inference authority: the ordinary fault receiver is signature protected and the bounded shell bridge exists only in the Harnex `emulatorE2e` Host process.

### Cross-repository RedactGuard evidence

RedactGuard now consumes immutable Consumer SDK `0.1.0-alpha.11` and exposes explicit Connect / Disconnect / Retry behavior in Settings while leaving authorization Harnex-owned.

Exact automated evidence is green for both cross-app paths:

- distinct-signer Consumer-first install -> Host absent -> later Harnex install without RedactGuard reinstall -> `PENDING` -> exact Harnex authorization -> Connect / Disconnect / Reconnect -> replacement signer denied as `SIGNATURE_CHANGED`;
- the complete Two-APK product/lifecycle/fault matrix, including ViewModel/Home continuity and Binder cancellation/process-loss/critical-pressure handling.

The focused physical Play Internal run confirms the real current pre-release distribution journey: RedactGuard-first, Harnex-later without reinstall, source-observed `PENDING`, exact authorization, Connect / Disconnect / Reconnect and representative production Consumer SDK/Binder/local-inference execution all work on device.

The Play signing metadata collected for that physical run reports the **same current Play App Signing SHA-256 digest for Harnex and RedactGuard**. Under ADR 0018 this is acceptable for the current pre-release first-party stable repository promotion because the topology is recorded truthfully and distinct-signer authorization remains covered by deterministic release-identity evidence. This physical run must not be described as distinct-signer Play qualification.

The commits added after the published/runtime-qualified candidates are limited to documentation, repository governance, verification scripts and workflow-policy surfaces; no Harnex app/runtime source or Android build configuration changed. The physical journey therefore remains applicable to the current runtime/product tree.

### Consumer API, OMBRA, evaluation and audit

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMB-6B identity approval, OMB-8 measured quality execution and physical evidence remain open. Model-evaluation work is integrated through EVAL-D-09 with later Android runner/persistence/comparison work continuing.

Local inference Activity/audit is integrated under ADR 0017: accepted inference history uses bounded encrypted app-private storage, verified Binder caller attribution and truthful restart reconciliation; normal telemetry/diagnostics stay content-free.

## Release evidence state

### Pre-release stable promotion

The focused Play install-order/authorization/runtime evidence is complete for the current pre-release topology. The same-signer first-party Play pair is explicitly recorded and is not used as the Binder authorization mechanism. Distinct-signer support remains proven deterministically. Therefore signer topology is **not a blocker for this pre-release `dev -> main` promotion** once the resulting exact HEAD/base passes required RELEASE/FULL automated validation.

### Deferred public-release signer obligation

Before the first public release that depends on independently signed application distribution, or before claiming physical distinct-signer Play qualification, rerun the focused physical journey with distinct observed Play signing identities. This obligation is deferred to that public-release milestone; it must not be silently dropped or misrepresented as already proven.

### Representative Android runtime evidence

LAS-07 and remaining CRV/SR/Q35/resource claims require representative physical Android evidence with exact candidate, production JNI/llama.cpp path and compatible GGUF where applicable. The new 4B 4-bit candidate tier is explicitly part of this evidence gap; catalog admission does not certify runtime suitability. Memory, thermal and OEM observations remain distinct from deterministic emulator evidence. These claim-specific gaps do not become proven merely because the stable repository line advances.

### OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed artifact/configuration identities against policy v1. Model evaluation, Q35 device tuning, RAM/thermal evidence and LLUP continue independently where ownership does not conflict.

## Immediate next block

1. run RELEASE/FULL on the exact current `dev` HEAD against live `main` after the evidence-contract amendment;
2. promote reconciled `dev` to stable `main` when those required automated gates are green;
3. retain the distinct-signer physical Play confirmation as a blocking obligation for the first applicable public release / physical distinct-signer readiness claim;
4. continue the independent ARM64/GGUF/runtime/resource/evaluation evidence workstreams without relabeling emulator or focused Play evidence as broader physical proof.

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
