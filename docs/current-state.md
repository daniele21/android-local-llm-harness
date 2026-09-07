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

Harnex has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, verified GGUF installation, model/generation lifecycle, cancellation, scheduling, memory-pressure handling, model-aware planning, output constraints and presets. Product support is curated Qwen3.5 dense 0.8B/2B plus the reviewed 4B **4-bit-only** candidate tier; the 4B artifacts remain `CANDIDATE` pending exact-artifact representative-device runtime, memory, thermal and output-quality evidence. Q35-6 still needs representative-device tuning evidence.

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

The tested Harnex source candidate is tree-equivalent to the integrated Harnex `dev` merge commit. RedactGuard's normal FULL validation also resolves the public alpha.11 artifact rather than relying on a source-candidate override.

Both current Harnex and RedactGuard candidates have been published successfully to Google Play Internal Testing. A focused physical Play Internal retest on representative Android hardware confirmed the Consumer-first install order with the Play-delivered builds: RedactGuard was installed first, Harnex was installed later without reinstalling RedactGuard, the consumer appeared `PENDING`, explicit Harnex authorization succeeded, Connect / Disconnect / Reconnect succeeded, and real consumer inference completed successfully.

The actual Play App Signing SHA-256 digest reported for both installed applications is the same identity: `D6:2D:3C:C8:51:D5:72:05:C3:42:C1:7F:86:26:40:58:E3:FE:29:6A:AE:1B:0E:43:FD:AC:58:82:24:44:1A:BD`. This means the physical Play run closes install-order, pending/authorization, connectivity and real-inference behavior for the current Play deployment, but it does **not** constitute physical evidence of distinct Play signing identities. Independent-signer semantics remain proven by deterministic cross-app automation; a physical Play claim for independently signed Host and consumer remains pending until a Play-delivered consumer with a distinct App Signing digest is exercised.

### Consumer API, OMBRA, evaluation and audit

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMB-6B identity approval, OMB-8 measured quality execution and physical evidence remain open. Model-evaluation work is integrated through EVAL-D-09 with later Android runner/persistence/comparison work continuing.

Local inference Activity/audit is integrated under ADR 0017: accepted inference history uses bounded encrypted app-private storage, verified Binder caller attribution and truthful restart reconciliation; normal telemetry/diagnostics stay content-free.

## Open blockers

### 1. Representative Android runtime evidence

LAS-07 and remaining CRV/SR/Q35/resource claims require representative physical Android evidence with exact candidate, production JNI/llama.cpp path and compatible GGUF where applicable. The new 4B 4-bit candidate tier is explicitly part of this evidence gap; catalog admission does not certify runtime suitability. Memory, thermal and OEM observations remain distinct from deterministic emulator evidence.

### 2. Physical Play independent-signer evidence

The current Harnex and RedactGuard Internal Testing builds report the same Play App Signing SHA-256 identity, so the physical run cannot close ADR 0018's distinct-Play-signer distribution proof. The current Play deployment has passed Consumer-first installation, `PENDING` discovery, explicit authorization, Connect / Disconnect / Reconnect and real inference. A future Play-delivered consumer signed with a different App Signing identity is required only for a physical claim that Harnex has been demonstrated with independently signed Play applications.

### 3. Harness 0.5 physical release evidence

The focused Play install-order/authorization/connectivity retest is complete for the actual current Play signing topology, but Harness 0.5 still requires the applicable physical lifecycle and SR-6 evidence from [`releases/harness-0.5.md`](releases/harness-0.5.md): exact release identity, real JNI/GGUF execution, cancellation, repeated lifecycle/memory behavior, cold/warm performance, thermal/resource snapshots and the remaining shared-runtime physical evidence. These gates must be recorded rather than inferred from the successful product smoke test.

### 4. OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed artifact/configuration identities against policy v1. Model evaluation, Q35 device tuning, RAM/thermal evidence and LLUP continue independently where ownership does not conflict.

## Immediate next block

1. capture representative physical ARM64/GGUF/runtime/resource evidence for the exact current candidate, starting with the release/Q35/SR-6 measurements that can share one controlled device run;
2. record exact model/runtime/device identities plus cold/warm TTFT, throughput, memory, thermal and lifecycle/cancellation evidence without promoting broader claims beyond the captured proof;
3. decide separately whether Harness 0.5 requires a physical distinct-Play-signer proof or whether that remains an explicit later compatibility gate beyond the current same-signer Play deployment;
4. once the applicable Harness 0.5 release gates are complete, run RELEASE/FULL promotion validation and promote the reconciled `dev` candidate to stable `main`;
5. continue OMBRA/model-evaluation work independently where ownership does not conflict.

## Source links

- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Independent consumer authorization: [`adr/0018-independently-signed-consumer-authorization.md`](adr/0018-independently-signed-consumer-authorization.md)
- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Local inference audit: [`features/local-inference-activity-audit.md`](features/local-inference-activity-audit.md), [`adr/0017-durable-local-inference-audit.md`](adr/0017-durable-local-inference-audit.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- LLUP: [`workstreams/llama-cpp-v0-3-residency-qualification.md`](workstreams/llama-cpp-v0-3-residency-qualification.md)
