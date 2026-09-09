# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-09

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

### GitHub release productization

The active [`github-release-productization.md`](workstreams/github-release-productization.md) workstream targets `v0.5.0-rc.1` as the first public GitHub prerelease. ADR 0020 defines GitHub Releases as the primary direct-download Host channel and Google Play as an optional independent channel. Both use the same Host package/service contract, but the GitHub APK uses a dedicated signing lineage rather than the Play upload/App-Signing identity.

The release design is fail-closed and exact-artifact-based: prepare signs and freezes one candidate APK with manifest/checksums/provenance; applicable REAL_ENVIRONMENT evidence must exercise that exact APK; publish downloads and verifies the same bytes rather than rebuilding them.

`v0.5.0-rc.1` is **not published**. GitHub Releases remain empty until setup, signing, exact-source promotion and applicable release evidence close.

### Repository protection state

A live repository ruleset named `Harnex protected branches` is active for both `dev` and `main`. It currently blocks deletion and non-fast-forward updates, requires pull requests, requires the `Repository validation` status check and requires branches to be current with their targets.

Two settings still differ from the durable contract in [`../BRANCHING.md`](../BRANCHING.md): resolved review conversations are not currently required, and `main` does not currently require the documented additional approval. This is now a **partial configuration mismatch**, not an absence of branch protection. It must be reconciled deliberately before treating the first public release path as fully aligned with repository governance; release automation must not be used to bypass the discrepancy.

### Pre-release stable promotion

The focused Play install-order/authorization/runtime evidence is complete for the current pre-release topology. The same-signer first-party Play pair is explicitly recorded and is not used as the Binder authorization mechanism. Distinct-signer support remains proven deterministically. Therefore signer topology is **not a blocker for an ordinary pre-release `dev -> main` repository promotion** once the resulting exact HEAD/base passes required RELEASE/FULL automated validation.

### Public-release signer obligation

The first public GitHub Host release introduces a dedicated Host signing identity and makes independently signed external Consumer behavior part of the intended public distribution surface. The exact GitHub-signed Host candidate therefore needs claim-matched physical evidence with a genuinely distinct external Consumer identity before the public cross-app distribution claim can be made. The earlier same-signer Play physical run cannot satisfy or be relabelled as that proof.

A later claim specifically about distinct-signer Play distribution still requires actual Play-delivered applications with distinct observed Play App Signing identities.

### Representative Android runtime evidence

LAS-07 and remaining CRV/SR/Q35/resource claims require representative physical Android evidence with exact candidate, production JNI/llama.cpp path and compatible GGUF where applicable. The 4B 4-bit candidate tier is explicitly part of this evidence gap; catalog admission does not certify runtime suitability. Memory, thermal and OEM observations remain distinct from deterministic emulator evidence. These claim-specific gaps do not become proven merely because the stable repository line advances.

For the first GitHub APK, representative evidence must be bound to the exact prepared GitHub-signed APK rather than a locally rebuilt or differently signed release-like binary.

### OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed artifact/configuration identities against policy v1. Model evaluation, Q35 device tuning, RAM/thermal evidence and LLUP continue independently where ownership does not conflict.

## Immediate next block

1. integrate the release-productization setup into the latest `dev` with FULL automated validation and no accidental overlap with concurrent product work;
2. reconcile the remaining live ruleset mismatch and complete the dedicated protected GitHub Release signing environment/key custody plus native GitHub release immutability;
3. reconcile the intended `0.5.0-rc.1` scope on current `dev`, run RELEASE/FULL against live `main`, and promote the exact candidate;
4. prepare the immutable GitHub-signed APK from exact `main` and run claim-matched physical ARM64/JNI/GGUF and distinct-Consumer signer evidence against those exact bytes;
5. publish the same candidate as GitHub prerelease only when the blocking release evidence closes; keep Play optional unless a Play-specific claim is being made;
6. continue independent model/runtime/resource/evaluation workstreams without relabelling emulator or focused Play evidence as broader physical proof.

## Source links

- Product strategy / decision boundaries: [`product.md`](product.md)
- Release productization: [`workstreams/github-release-productization.md`](workstreams/github-release-productization.md)
- GitHub release runbook: [`github-releases.md`](github-releases.md)
- Versioning/distribution policy: [`versioning.md`](versioning.md)
- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Independent consumer authorization: [`adr/0018-independently-signed-consumer-authorization.md`](adr/0018-independently-signed-consumer-authorization.md)
- GitHub/Play distribution decision: [`adr/0020-official-github-and-play-distribution.md`](adr/0020-official-github-and-play-distribution.md)
- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Local inference audit: [`features/local-inference-activity-audit.md`](features/local-inference-activity-audit.md), [`adr/0017-durable-local-inference-audit.md`](adr/0017-durable-local-inference-audit.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- LLUP: [`workstreams/llama-cpp-v0-3-residency-qualification.md`](workstreams/llama-cpp-v0-3-residency-qualification.md)
