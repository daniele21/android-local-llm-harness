# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-01

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
- product catalog restricted to curated Qwen3.5 dense 0.8B/2B artifacts; exact artifact choice remains Harnex-owned.

Q35-1 through Q35-5 are complete. Q35-6 remains active because the 0.8B/2B candidate profiles still require representative physical-device tuning evidence. See [`qwen35/README.md`](qwen35/README.md).

### Android application and observability

`apps/local-llm-phone-test` exposes connected Overview, Playground, Applications, Performance, Models, Diagnostics and Settings backed by real model/runtime/evaluation/observability/control-plane sources. Task-first navigation, progressive disclosure, evidence-backed Diagnostics/Performance behavior and adaptive/accessibility rules are integrated.

Public identity is **Harnex** — **“Your local AI harness for Android.”** Launcher, shell, Settings/About, previews and brand generators use it; `Harness*`, `harness_launcher_*`, package/Binder IDs and historical filenames remain compatibility identifiers. Exact-head `phone-cold-start` owns Harnex screenshot/video UI evidence, not physical-runtime claims.

Applications control-plane work is complete through ACUX-80 and CPREC-10 through CPREC-70 (#455, #459, #457, #458, #460, #461, #464). Startup atomically reconciles mandatory built-ins before UI/Binder readers, preserves valid custom/default/disabled state and stays off the main thread; CPREC-80/90 remain physical evidence gates. See [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md).

General phone work still includes process/back-stack evidence, representative TalkBack/large-font/layout/screenshots, RAM warm-idle policy/controls and signed physical-GGUF evidence.

### Shared Android runtime and Consumer readiness convergence

SR-0 through SR-5 are integrated. SR-6 repository-side release-evidence tooling is integrated, including packaged-client, same-signer/invalid-signer and process-death/reconnect fixtures. Production/release readiness still requires representative physical SR-6 evidence. See [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md) and [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

CRV is repository-complete through the automated candidate gate: exact activated identity/custom-preset policy (#467/#468), Consumer SDK `0.1.0-alpha.6` Binder readiness contract/protocol minor 4 (#472), automatic preparation with fail-closed fallback (#474), source-backed readiness/residency observation (#475), Harnex Applications presentation (#476), focused failure regressions (#477), and RedactGuard readiness consumption (#100/#101).

CRV-100 froze the automated physical candidate identities as Harness v31 source `a30f67b21e24adc6efea838e9a9d65cc78446f28` and RedactGuard v11 source `4679c23a9a22e5242761fe52af97f4eb7432aec7`. Both have exact-source automated package evidence. Later CI/documentation-only descendants do not replace those frozen APK source identities. CRV-110 remains the real-device same-signer + real-GGUF gate; CRV-120 cleanup follows only after that evidence passes.

### Public Consumer API and OMBRA

CA-0 through CA-4 are integrated in `dev`; the published consumer boundary has continued through the current Consumer SDK line used by CRV. RedactGuard remains a pure Consumer SDK client: concrete model/config/runtime/residency ownership stays in Harnex and consumer surfaces receive only safe published state.

CA-5 remains active through the OMBRA/RedactGuard quality and evidence program. Repository-side preparation includes:

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

`apps/local-llm-console` no longer owns retired model-management, observability, health, cache or raw inference surfaces. OMB-6B remains review-gated and OMB-8 has no model/category support claim until exact reviewed Qwen3.5 artifacts pass policy. Physical two-APK/device and release evidence remain open. Canonical state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

### Model evaluation

EVAL-0, EVAL-1 and EVAL-3 are complete. Contracts/evaluators provide backend-independent value semantics, deterministic identity, compatibility failures and six deterministic v1 scorer families without an external judge. Dataset work has integrated schema, parsing, validation, digest, atomic installation, registry/discovery, stratified sampling, preset resolution and regression fixtures (`EVAL-D-01` through `D-09`); Android import `D-10` is next. Runner/persistence/comparison/Performance work continues, with Performance failing closed until compatible aggregated evidence supports comparison. See [`model-evaluation/README.md`](model-evaluation/README.md).

## Open blockers

### 1. OMB-6B final identity review

PR #248 contains a review-gated symbol candidate, not approved production identity. Closure requires approved symbol/wordmark/lockup, deterministic adaptive/monochrome launcher assets and packaging checks without changing package/signing boundaries. Do not infer visual approval from a green candidate workflow.

### 2. OMB-8 quality execution

Corpus v2 and policy v1 are integrated and identity-bound. Before any Qwen3.5 support claim, execute each reviewed artifact/configuration, evaluate aggregate/per-type precision/recall/F1 plus structured-completion and invalid-result/finding rates, preserve exact identities and fail closed on threshold/category/identity failure. Policy v1 must not be lowered to fit observed results.

### 3. Physical control-plane / CRV evidence

The persisted control-plane repair and Consumer runtime-readiness convergence are implemented and automatically validated. What remains is representative-device evidence, not repository implementation.

A physical session must use same-signer release APKs built from the frozen Harness v31 and RedactGuard v11 source identities and a real GGUF. CRV-110 must prove Host absence/reconnect, exact assignment/preset activation, automatic cold preparation, source-backed ready/generating state, cancellation/recovery, Host restart, relevant fail-closed paths, review/export and privacy-safe evidence. CPREC-80 must additionally prove upgrade repair without uninstall/clear-data; CPREC-90/ACUX-90 may share the clean two-APK session where their independent acceptance criteria are all recorded.

CI, packaging and emulator evidence must not be reported as this physical gate.

### 4. Other representative Android evidence

Hardware sessions may also combine phone UX, Q35-6, SR-6 and OMB-8 execution where practical, but each exit gate remains independent. Do not claim representative-device UX, `MEASURED` Q35 profiles, production-ready OMBRA/shared-host transport or physical Consumer readiness from CI/emulator evidence alone.

### 5. Follow-on validation and product hardening

Repository-side UX/UI and CRV runtime visibility are complete. Remaining phone work is device/restoration evidence, RAM warm-idle policy and Q35-7 semantic/lifecycle/memory/thermal validation. Parallel upgrade plan: [`LLUP`](workstreams/llama-cpp-v0-3-residency-qualification.md).

## Immediate next block

1. build same-signer release APKs from the frozen Harness v31 and RedactGuard v11 source revisions using their repository release helpers;
2. execute CRV-110 / RG-HCP-8 on a representative ARM64 Android device with a real GGUF, recording the exact candidate identity and privacy-safe evidence;
3. in the same hardware window where appropriate, execute CPREC-80 upgrade-repair first and then the clean CPREC-90/ACUX-90 path without collapsing their independent acceptance criteria;
4. keep OMB-6B review, OMB-8 quality execution, Q35-6, SR-6 and broader phone UX evidence parallel where their ownership does not conflict;
5. after CRV-110 passes, perform CRV-120 durable handoff/cleanup and close the temporary CRV coordinator rather than merging its stale planning branch;
6. complete release privacy/security, packaging, versioning/signing and documentation checks on the exact promoted build when a release promotion is intentionally requested.

## Source links

- Capability roadmap: [`roadmap.md`](roadmap.md)
- Applications UX: [`features/application-control-plane-ux.md`](features/application-control-plane-ux.md), [`workstreams/application-control-plane-ux.md`](workstreams/application-control-plane-ux.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md), [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md), [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- Harnex 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)
