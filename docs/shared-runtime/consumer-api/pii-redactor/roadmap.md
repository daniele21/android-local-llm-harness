# OMBRA implementation roadmap

Status: active
Document type: roadmap
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.roadmap
Read when: selecting the next OMBRA implementation slice, dependency or exit gate
Last reviewed: 2026-08-16

This roadmap owns implementation order and milestone state. Detailed behavior stays in the focused OMBRA specifications. Repository priority belongs in [`../../../current-state.md`](../../../current-state.md); generic Consumer API milestones remain in [`../roadmap.md`](../roadmap.md).

## Sequence

```text
OMB-0 Decisions/spikes
  -> OMB-1 Domain/application state
      -> OMB-2 PDF import/extraction
      -> OMB-3 Analysis/fake inference
  -> OMB-4 Consumer API integration   [depends on CA-0..CA-4]
  -> OMB-5 Review/redaction/export

OMB-0 -> OMB-6A Design system/components
(OMB-1..OMB-5 + OMB-6A) -> OMB-7 Product flow / Console retirement
OMB-6A -> OMB-6B Final identity/launcher   [parallel; OMB-7/release gate]
OMB-3 -> OMB-8A Quality corpus             [parallel preparation]
(OMB-7 + OMB-6B + OMB-8A) -> OMB-8B Quality/device/release
```

Parallel work requires disjoint ownership. OMB-6B did not block repository-side OMB-7 implementation, but it gates OMB-7 identity closure. Corpus/policy preparation does not prove model quality or physical release readiness. Real inference must not bypass unmet Consumer API gates.

## OMB-0 — Decisions and technical spikes

State: **DONE**

Integrated through PR #106. Accepted boundaries: OMBRA/package identity for v1; text-bearing PDF only; mandatory review; permissionless isolated PdfBox parsing; flattened export; fail-closed malformed/encrypted/image-only handling; cancellation/resource cleanup; glyph/export evidence; and `document-pii-detection` using `STATELESS` + fixed `JSON_SCHEMA` with reasoning disabled and host-owned model/preset selection.

Canonical evidence: [`omb0-decisions-and-spikes.md`](omb0-decisions-and-spikes.md).

## OMB-1 — Pure domain and application state

State: **DONE**

Integrated through PR #108. Includes Android-independent document/PII/finding/decision models, immutable workflow state/effects, replaceable extractor/analysis/export ports, process-local sensitive storage, operation identity, acknowledged cancellation, reset/recreation cleanup and deterministic JVM flow coverage.

Exit gate: **PASSED**. Pure JVM import -> definitions -> fake findings -> decisions -> export works without Android UI, Binder or model code.

## OMB-2 — PDF import and extraction

State: **DONE**

Integrated through OMB-2A and PR #154. Includes isolated production extraction, opaque process-local source capabilities, deterministic segmentation, transient PDF picker access, typed fail-closed parser/input failures, cancellation cleanup and focused JVM/instrumentation coverage.

Exit gate: **PASSED**. Supported fixtures segment deterministically; unsupported/cancelled inputs release resources and reset retains neither sensitive task data nor source capability state.

## OMB-3 — Analysis composition and fake inference

State: **DONE**

Integrated through PRs #148/#202. Includes versioned prompt/schema serialization, capability-aware chunking, bounded hostile-output parsing, exact source validation, deterministic merge/dedup/overlap handling and fake-driven success/failure/disconnect/cancellation coverage.

Exit gate: **PASSED**. Invented, unselected or partial findings cannot become replacements.

## OMB-4 — Harness use case and Consumer API integration

State: **DONE**

Integrated through PRs #144/#210. Host-owned `document-pii-detection` uses packaged Binder Consumer API, public limits, fixed reviewed behavior and preset, per-operation sessions, execution-identity revalidation and typed compatibility/disconnect/cancellation mapping.

Exit gate: **PASSED for repository-side integration**. Physical same-signer two-APK evidence remains OMB-8/CA-6.

## OMB-5 — Review, redaction and PDF export

State: **DONE**

Integrated through PRs #146/#157/#218. Includes accepted/ignored decisions, conflict blocking, deterministic placeholders, hidden review with single explicit reveal, exact-source replacement, transient export capabilities, flattened PDF writing, partial-output cleanup and independent re-parse evidence. Zero-finding export remains valid.

Exit gate: **PASSED for document/review/export**. Physical end-to-end release evidence remains OMB-8.

## OMB-6 — Design system and identity

State: **IN PROGRESS**

Owner: [`ux-and-brand.md`](ux-and-brand.md)

### OMB-6A — Design system and reusable components

State: **DONE**

PRs #145/#200/#220 integrated OMBRA light/dark schemes, semantic tokens, typography/spacing/shapes, accessibility coverage and reusable import/definition/review/export components with safe hidden-content boundaries.

### OMB-6B — Final vector identity and Android launcher

State: **IN PROGRESS — REVIEW GATED**

PR #248 contains the validated tooling/candidate lane but does not approve the symbol, wordmark/lockup or production launcher.

Remaining:

- approve or revise the symbol and freeze wordmark/lockup;
- generate deterministic adaptive/monochrome launcher assets and packaging checks;
- integrate approved identity without changing package/signing boundaries.

Exit gate: **OPEN**. OMB-6B is the remaining OMB-7 closure dependency and a release dependency.

## OMB-7 — Compose product flow and Console retirement

State: **IN PROGRESS — REPOSITORY-SIDE EVIDENCE COMPLETE, IDENTITY GATED**

Owner: [`ux-and-brand.md`](ux-and-brand.md)

Integrated:

- **OMB-7A / #232** — Compose Import -> Definitions/custom definitions -> Analysis -> Review-ready flow, process-local sensitive state, production PDF capability, Binder analysis composition and Harness connection state;
- **OMB-7B / #235** — safe review/reveal, `Oscura`/`Ignora`, navigation/conflict blocking, `CreateDocument` export, zero-PII handling and retirement of legacy Console/model-store/observability surfaces;
- **OMB-7C / #250** — hidden/revealed semantics, conflict/zero-PII behavior, 200% font reachability and dedicated emulator evidence;
- **final repository-side evidence / #259** — Review reset, export-progress cancellation action, portrait/landscape matrix and privacy-safe code-owned screenshots, merged after exact-head Validate and OMBRA UI evidence were green.

Remaining before `DONE`:

- integrate the approved OMB-6B production identity and deterministic launcher packaging.

Exit gate: **OPEN ONLY ON OMB-6B IDENTITY**. Emulator evidence does not satisfy OMB-8 physical release evidence.

## OMB-8 — Quality, physical evidence and release

State: **IN PROGRESS**

Owner: [`validation-and-rollout.md`](validation-and-rollout.md)

### OMB-8A — Deterministic quality corpus

Integrated:

- **#223** — versioned SHA-256-frozen synthetic corpus and exact-occurrence TP/FP/FN, precision/recall/F1 scorer;
- **#253** — active `ombra-pii-synthetic-v2`: 32 synthetic cases, at least five positive exact occurrences for every built-in PII category plus custom, with zero-PII, repeated, overlap, near-miss, injection-like and Unicode/Italian variants;
- active corpus identity/hash and minimum per-category support are regression-tested.

### OMB-8B — Pre-registered quality policy

Integrated through **#252**, replayed directly on post-#253 `dev` before merge:

- policy v1 is pinned to exact active corpus v2 identity/hash and all seven required categories;
- aggregate gates: precision >= 0.90, recall >= 0.98, F1 >= 0.94;
- per-category gates: precision >= 0.80, recall >= 0.90, F1 >= 0.85;
- structured completion >= 0.98, invalid finding rate <= 0.02, invalid result rate = 0.00;
- identity mismatch, missing categories or threshold failure fail closed with deterministic typed reasons;
- regression asserts policy corpus identity and required type set exactly match the active loader;
- exact-head Android/repository and documentation validation passed before integration.

Remaining:

- execute corpus v2 on each reviewed Qwen3.5 artifact/configuration and evaluate policy v1 before any support claim;
- retain exact artifact, preset, corpus and policy identities; policy v1 must not be lowered to fit results;
- complete privacy/security, parser dependency, public-copy and packaged-APK reviews;
- run physical same-signer two-APK import -> analysis -> review -> export/failure scenarios;
- independently verify output on the exact distributed build and capture privacy-safe evidence;
- finalize versioning, release notes, shrinker, signing and compatibility documentation with applicable CA-6/CA-7/SR gates.

Exit gate: **OPEN**. Corpus v2 and policy v1 are acceptance infrastructure, not evidence that a reviewed model passes. The exact distributed build must satisfy policy v1, physical OMBRA validation and applicable Consumer API/SR prerequisites before support/release claims.

## State rule

Use `IN PROGRESS` only after implementation starts and `DONE` only after the stated exit gate is integrated and tested. Parallel preparation never upgrades downstream claims. Emulator screenshots, synthetic corpus, registered policy or fake-model success do not complete physical, quality or release gates.
