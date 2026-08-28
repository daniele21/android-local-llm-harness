# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, blockers or next repository work block
Last reviewed: 2026-08-27

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

Consumer execution-readiness convergence is now tracked in [`workstreams/consumer-runtime-readiness-visibility.md`](workstreams/consumer-runtime-readiness-visibility.md): Harness must make preset model/generation configuration effective and inspectable, automatically prepare the exact activated installed model for consumer inference, and expose transport/configuration/runtime state truthfully to both Harness and consumer UI without moving model/runtime ownership into RedactGuard. The first RedactGuard readiness slice is integrated through PR #100: product readiness now requires side-effect-free Host assignment/preset discovery rather than raw Binder connectivity, while model/runtime identity remains Harness-owned.

Persisted control-plane reconciliation is integrated repository-side through the unambiguous Harness v30 candidate: the pure reconciler, Room partial-state regression, startup cutover, upgrade matrix, cross-surface consistency proof and main-thread startup fix are in `dev`. Remaining CPREC work is representative physical upgrade-repair and clean two-APK evidence; ACUX-90 remains gated on that exact-candidate device proof rather than on further repository implementation.

General phone work still includes process/back-stack evidence, representative TalkBack/large-font/layout/screenshots, RAM warm-idle policy/controls and signed physical-GGUF evidence.

### Shared Android runtime

SR-0 through SR-5 are integrated. SR-6 repository-side release-evidence tooling is integrated, including packaged-client, same-signer/invalid-signer and process-death/reconnect fixtures. Production/release readiness still requires representative physical SR-6 evidence. See [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md) and [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

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

### 3. CPREC physical evidence

Repository-side control-plane reconciliation is integrated. Closure still requires the exact signed candidate to prove non-destructive upgrade repair without uninstall/clear-data and then a clean same-signer two-APK path where Applications and the external consumer observe and use the same application/use-case/binding/preset identity. Stale/invalid identities must fail closed and valid persisted custom/default/disabled state must survive repair.

Because CRV materially changes consumer activation/runtime readiness, CPREC closure evidence should be collected on the post-CRV exact integrated candidate rather than spent on the earlier v30 candidate and immediately invalidated.

### 4. Physical Android evidence

Hardware sessions may combine phone UX, ACUX-90, Q35-6, SR-6, CPREC and OMB-8 runs, but each exit gate stays independent. ACUX-90 specifically requires persisted default after Harness restart, real consumer discovery/activation/use of the exact app/use-case/binding/preset identity and a stale/invalid fail-closed path.

Do not claim representative-device UX, external-consumer Applications effectiveness, `MEASURED` Q35 profiles, publish-ready Binder client AAR or production-ready OMBRA/shared-host transport from CI/emulator evidence alone.

### 5. Follow-on validation and product hardening

Repository-side UX/UI, including Applications through ACUX-80, is complete. Remaining phone work is device/restoration evidence plus the separately scoped RAM warm-idle policy. After Q35-6, Q35-7 covers semantic/golden, context-boundary, cancellation, lifecycle, memory and thermal validation.

## Immediate next block

1. complete the three remaining parallel CRV foundation lanes: CRV-10 exact execution semantics, CRV-20 consumer-safe readiness/progress contract and CRV-50 Harness configuration/runtime UX; CRV-60 is already integrated in RedactGuard through PR #100;
2. after exact execution identity settles, connect activation to automatic runtime preparation/residency in CRV-30 and then establish source-backed runtime state in CRV-40;
3. implement Harness and RedactGuard readiness UI in parallel as CRV-70/80, then run the cross-layer regression matrix and exact-head automated preflight;
4. continue OMB-6B/OMB-8 and other non-conflicting repository work independently, but defer CPREC-80/90 closure evidence until the material CRV path is integrated;
5. produce new exact Harness and RedactGuard candidates after CRV automated gates, then use one representative two-APK session for compatible CRV/CPREC/ACUX/RG-HCP evidence while preserving each gate independently;
6. complete Q35-6, SR-6 and broader phone UX evidence plus release privacy/security, packaging, versioning/signing and documentation checks on the relevant exact builds.

## Source links

- Capability roadmap: [`roadmap.md`](roadmap.md)
- Consumer runtime readiness: [`workstreams/consumer-runtime-readiness-visibility.md`](workstreams/consumer-runtime-readiness-visibility.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Applications UX: [`features/application-control-plane-ux.md`](features/application-control-plane-ux.md), [`workstreams/application-control-plane-ux.md`](workstreams/application-control-plane-ux.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md), [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md), [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- Harness 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)
