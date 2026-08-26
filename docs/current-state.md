# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-08-26

This is the single operational ledger for the integrated baseline, blockers and immediate next work. Capability history belongs in [`roadmap.md`](roadmap.md); milestone detail belongs in its workstream/specification; release gates belong in [`releases/harness-0.5.md`](releases/harness-0.5.md).

## Integration lines

- `dev` is the canonical base and target for ordinary feature, fix, UX/UI and documentation work.
- `main` is the protected stable/release line.
- New work starts from the latest green `dev` unless it is an explicit emergency hotfix.

## Integrated baseline

### Embedded runtime and models

- pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging and opaque native ownership;
- GGUF inspection, SHA-256 content-addressed storage, verified curated installation and explicit model lifecycle;
- load, context creation, generation, streaming, cancellation, single-decode scheduling and memory-pressure handling;
- model-aware prompt/context planning, output constraints, versioned presets and privacy-safe failures;
- product catalog restricted to curated Qwen3.5 dense 0.8B/2B artifacts; exact artifact choice remains Harness-owned.

Q35-1 through Q35-5 are complete. Q35-6 remains active because the 0.8B/2B candidate profiles still require representative physical-device tuning evidence. See [`qwen35/README.md`](qwen35/README.md).

### Android application and observability

`apps/local-llm-phone-test` has connected Overview, Playground, Applications, Performance, Models, Diagnostics and Settings with real model/runtime/evaluation/observability/control-plane sources. Repository-side product-experience realignment is complete, including task-first navigation, progressive disclosure, evidence-backed Diagnostics/Performance behavior, adaptive/accessibility rules and ViewModel-owned async state/effects.

Applications control-plane implementation is complete through ACUX-80: Apps primary navigation; source-backed Application -> Assigned use case -> Preset drill-down; Suggested/Custom/default semantics; custom preset creation; revision-safe supported mutations with canonical re-read; Advanced/Technical disclosure; and medium/expanded master-detail with compact/large-font single-pane fallback. PR #449 passed Repository health, Validate and Package Android Artifacts on exact head `625747bcc6ef28a9cd0966a693550444fd4db1ed` before squash merge into `dev` as `d8caa3454c51c9c8e53ff3da95d31f7c3df6f1ed`.

A persisted control-plane startup/upgrade regression is now active: current Binder discovery/activation can seed only an exactly empty store, so a partially populated but valid Room state may remain permanently incomplete. The bounded repair is tracked in [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md). ACUX-90 is blocked until the reconciled exact-head repository candidate reaches CPREC-70.

General phone work still includes process/back-stack evidence, representative TalkBack/large-font/layout/screenshots, RAM warm-idle policy/controls and signed physical-GGUF evidence.

### Shared Android runtime

SR-0 through SR-5 are integrated. SR-6 repository-side release-evidence tooling is integrated, including packaged-client, same-signer/invalid-signer and process-death/reconnect fixtures. Production/release readiness still requires representative physical SR-6 evidence. See [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md) and [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

A physical RedactGuard release run now exposes a packaged Consumer Control Plane Binder/Parcel compatibility regression before inference: the signed/granted/bound two-APK pair keeps both processes alive while `assignedUseCases()` coincides with `libbinder.Parcel` protected-data errors at the following Binder callback boundary. The release consumer is R8-minified while the current packaged consumer fixture is not. Root-cause isolation and the SDK-owned compatibility fix are tracked in [`workstreams/shared-runtime-binder-ipc-compatibility.md`](workstreams/shared-runtime-binder-ipc-compatibility.md); R8 remains a leading hypothesis until the minified fixture reproduces or falsifies it.

### Public Consumer API and OMBRA

CA-0 through CA-4 are integrated in `dev`; PR #104 completed the Binder v1.1 `consumer-api-v1` boundary with consumer AIDL/wire contracts, authenticated host mapping, lifecycle/generation adapters, privacy/compatibility coverage and packaged release-AAR compilation evidence.

CA-5 is active through OMBRA. Repository-side preparation includes:

- **OMB-0** — PdfBox parser/export decision and runtime evidence through PR #106;
- **OMB-1** — pure domain/application workflow through PRs #107/#108;
- **OMB-2** — PDF picker, extraction, typed failures and cleanup through PR #154;
- **OMB-3** — deterministic prompt/schema/chunk planning and finding validation/orchestration through PRs #148/#202;
- **OMB-4** — host-owned `document-pii-detection` policy and Binder Consumer API adapter through PRs #144/#210;
- **OMB-5** — deterministic redaction, flattened export and safe hidden/reveal projection through PRs #146/#157/#218;
- **OMB-6A** — OMBRA themes/tokens and reusable components through PRs #145/#200/#220;
- **OMB-7A/B/C** — Import -> Definitions -> Analysis -> Review/export flow plus privacy/accessibility/state evidence through PRs #232/#235/#250/#259;
- **OMB-8A** — active `ombra-pii-synthetic-v2` corpus with 32 cases and at least five positive exact occurrences per supported category through PRs #223/#253;
- **OMB-8B** — pre-registered support-policy v1 through PR #252, pinned to corpus v2 identity/hash and required type set with fail-closed checks.

`apps/local-llm-console` no longer owns retired model-management, observability, health, cache or raw inference surfaces. OMB-7 still depends on approved OMB-6B production identity. OMB-6B remains review-gated in PR #248; OMB-8 has corpus/policy integrated but no model/category support claim until exact reviewed Qwen3.5 artifacts pass policy. Physical two-APK/device and release evidence remain open. Canonical state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

### Model evaluation

EVAL-0, EVAL-1 and EVAL-3 are complete. Contracts/evaluators provide backend-independent value semantics, deterministic identity, compatibility failures and six deterministic v1 scorer families without an external judge. Dataset work has integrated schema, parsing, validation, digest, atomic installation, registry/discovery, stratified sampling, preset resolution and regression fixtures (`EVAL-D-01` through `D-09`); Android import `D-10` is next. Runner/persistence/comparison/Performance work continues, with Performance failing closed until compatible aggregated evidence supports comparison. See [`model-evaluation/README.md`](model-evaluation/README.md).

## Open blockers

### 1. OMB-6B final identity review

PR #248 contains a review-gated symbol candidate, not approved production identity. Closure requires approved symbol/wordmark/lockup, deterministic adaptive/monochrome launcher assets and packaging checks without changing package/signing boundaries. Do not infer visual approval from a green candidate workflow.

### 2. OMB-8 quality execution

Corpus v2 and policy v1 are integrated and identity-bound. Before any Qwen3.5 support claim, execute each reviewed artifact/configuration, evaluate aggregate/per-type precision/recall/F1 plus structured-completion and invalid-result/finding rates, preserve exact identities and fail closed on threshold/category/identity failure. Policy v1 must not be lowered to fit observed results.

### 3. Persisted control-plane reconciliation

The current host bootstrap mutates persistent state from Binder-path discovery/activation and only seeds when the entire `HostControlPlaneState` is empty. Partial valid state can therefore survive upgrades/restarts without mandatory built-in application/use-case/preset/binding/exposure data. Repair must be atomic, conservative and idempotent; preserve unrelated/custom/disabled/default state; fail closed on conflicting built-in identity; and complete before UI or Binder observation. CPREC-10 and CPREC-20 are the first parallel implementation slices; physical Applications/HCP proof waits for CPREC-70.

### 4. Packaged Binder IPC compatibility

The RedactGuard release consumer reaches the shared-runtime connection but fails at Consumer Control Plane discovery with protected-Parcel evidence before inference. Source-level DTO/AIDL identity matches the consumed SDK lineage, so the immediate work is to make the packaged consumer fixture production-like with R8, isolate the first wire divergence, then fix the owning Binder/package boundary. Do not add RedactGuard-local Harness keep rules or change protocol semantics without the SR-BIPC root-cause gate.

### 5. Physical Android evidence

Hardware sessions may combine phone UX, ACUX-90, Q35-6, SR-6, SR-BIPC-90 and OMB-8 runs, but each exit gate stays independent. ACUX-90 specifically requires persisted default after Harness restart, real consumer discovery/activation/use of the exact app/use-case/binding/preset identity and a stale/invalid fail-closed path. ACUX-90 cannot start until the reconciled CPREC-70 candidate exists. SR-BIPC-90 additionally requires a release/minified external consumer proving the protected-Parcel regression is absent on exact artifacts.

Do not claim representative-device UX, external-consumer Applications effectiveness, `MEASURED` Q35 profiles, publish-ready Binder client AAR or production-ready OMBRA/shared-host transport from CI/emulator evidence alone.

### 6. Follow-on validation and product hardening

Repository-side UX/UI, including Applications through ACUX-80, is complete. Remaining phone work is device/restoration evidence plus the separately scoped RAM warm-idle policy. After Q35-6, Q35-7 covers semantic/golden, context-boundary, cancellation, lifecycle, memory and thermal validation.

## Immediate next block

1. execute SR-BIPC-10 minified packaged-consumer reproduction, SR-BIPC-20 wire/package isolation and the RedactGuard diagnostic correction in parallel; stop at the SR-BIPC-30 root-cause gate before choosing a compatibility fix;
2. continue CPREC reconciliation independently where ownership does not conflict, then run its exact-head upgrade/two-APK evidence before ACUX-90;
3. after SR-BIPC-30, implement the minimal owner-level Binder/package fix, prove the minified fixture plus Maven-only consumer and publish the next Consumer SDK prerelease before updating RedactGuard;
4. complete OMB-6B identity review and deterministic launcher assets, and continue OMBRA corpus v2 quality execution independently where ownership does not conflict;
5. run Q35-6, SR-6, SR-BIPC-90 and broader phone UX evidence in parallel where hardware can be shared;
6. complete release privacy/security, packaging, versioning/signing and documentation checks on the exact build.

## Source links

- Capability roadmap: [`roadmap.md`](roadmap.md)
- Applications UX: [`features/application-control-plane-ux.md`](features/application-control-plane-ux.md), [`workstreams/application-control-plane-ux.md`](workstreams/application-control-plane-ux.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Binder IPC compatibility: [`workstreams/shared-runtime-binder-ipc-compatibility.md`](workstreams/shared-runtime-binder-ipc-compatibility.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md), [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md), [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- Harness 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)
