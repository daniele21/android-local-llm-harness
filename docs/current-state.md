# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-06

This is the operational ledger for integrated state, blockers and immediate work. Capability history belongs in [`roadmap.md`](roadmap.md); milestone detail stays in focused workstreams.

## Integration lines

- `dev` is the canonical base/target for ordinary work and Internal Testing candidates.
- `main` is the stable/release line.
- New work starts from the latest green `dev` unless explicitly hotfixed.
- The 2026-09-04 stable promotion is synchronized back into `dev` per ADR 0008.

## Integrated baseline

### Runtime, product and control plane

Harnex has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, verified GGUF installation, model/generation lifecycle, cancellation, scheduling, memory-pressure handling, model-aware planning, output constraints and presets. Product support remains curated Qwen3.5 dense 0.8B/2B; Q35-6 still needs representative-device tuning evidence.

`apps/local-llm-phone-test` exposes Overview, Playground, Activity, Applications, Performance, Models, Diagnostics and Settings. Applications control-plane work is complete through ACUX-80 and CPREC-10..70; broader representative-device UX/runtime evidence remains.

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 tooling are integrated. The currently published Consumer SDK is `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.10`, whose distribution baseline still reflects the original ADR 0012 same-signer trust shape.

ADR 0018 defines the candidate correction for independently distributed consumers. The public Harnex service is explicitly bindable with no custom bind permission so Consumer-before-Host installation cannot permanently block reachability. Authority remains fail-closed Binder UID -> exact installed package -> current signer -> Harnex Control Plane authorization -> enabled use case. Known external consumers are source-observed as `PENDING`; signer replacement becomes `SIGNATURE_CHANGED`; both require explicit user authorization. Emulator fault/control authority remains separately signature-protected.

The correction also adds reusable Consumer SDK `disconnect()` and is versioned as candidate `0.1.0-alpha.11`. It is not published until exact validated `dev` publication succeeds. Durable logical jobs and Host background/process lifecycle remain owned by ADR 0016 and the integrated background-lifecycle workstream.

### Cross-repository RedactGuard evidence

The integrated RedactGuard baseline consumes alpha.10. Earlier same-key emulator E2E was valid for that topology but did not represent separate Play App Signing identities; a physical Play Internal run exposed the mismatch when independently signed Harnex and RedactGuard were installed.

The correction therefore requires exact-candidate distinct-signer automation: build Harnex + Consumer SDK and RedactGuard from recorded revisions, install the Consumer before the Host in the install-order path, sign Host and consumer with different identities, prove Binder/Control Plane denial while RedactGuard is pending, authorize the exact source-observed identity through Harnex-owned authority, then prove connect/disconnect/reconnect and fail-closed replacement signing identity. Actual Play signer confirmation remains separate REAL_ENVIRONMENT evidence.

### Consumer API, OMBRA, evaluation and audit

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMB-6B identity approval, OMB-8 measured quality execution and physical evidence remain open. Model-evaluation work is integrated through EVAL-D-09 with later Android runner/persistence/comparison work continuing.

Local inference Activity/audit is integrated under ADR 0017: accepted inference history uses bounded encrypted app-private storage, verified Binder caller attribution and truthful restart reconciliation; normal telemetry/diagnostics stay content-free.

## Open blockers

### 1. Independent Play signing topology

Integration readiness requires exact deterministic Harnex gates plus distinct-signer RedactGuard E2E covering Consumer-before-Host reachability, pending denial, exact identity approval, reusable reconnect and signer-replacement denial. After Harnex alpha.11 publication, RedactGuard must consume that immutable artifact and pass its own exact-head validation.

Stable promotion additionally requires a focused physical Play Internal retest with the actual Harnex and RedactGuard Play App Signing identities.

### 2. Representative Android runtime evidence

LAS-07 and remaining CRV/SR/Q35/resource claims require representative physical Android evidence with exact candidate, production JNI/llama.cpp path and compatible GGUF where applicable. Memory, thermal and OEM observations remain distinct from deterministic emulator evidence.

### 3. OMBRA and follow-on work

OMB-6B remains review-gated; OMB-8 must execute reviewed artifact/configuration identities against policy v1. Model evaluation, Q35 device tuning, RAM/thermal evidence and LLUP continue independently where ownership does not conflict.

## Immediate next block

1. close ADR 0018 implementation with exact Harnex validation and distinct-signer RedactGuard E2E;
2. publish validated Consumer SDK alpha.11 from `dev`, repin RedactGuard and validate/merge its Harnex connection UX;
3. publish Internal Testing candidates and perform the focused physical Play authorization/connectivity retest before any `dev -> main` promotion;
4. continue the independent physical/runtime/evaluation workstreams.

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
