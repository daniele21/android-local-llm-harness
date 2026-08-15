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
OMB-0 Decisions and technical spikes
  -> OMB-1 Pure domain and application state
      -> OMB-2 PDF import and extraction
      -> OMB-3 Analysis composition and fake inference
  -> OMB-4 Real Consumer API use case/integration   [depends on CA-0..CA-4]
  -> OMB-5 Review, redaction and PDF export

OMB-0 -> OMB-6A OMBRA design system and reusable task components
(OMB-1..OMB-5 + OMB-6A) -> OMB-7 Compose product flow / Console retirement
OMB-6A -> OMB-6B final vector identity and launcher assets   [parallel with OMB-7; release gate]
OMB-3 -> OMB-8A deterministic quality-corpus preparation     [parallel preparation]
(OMB-7 + OMB-6B + OMB-8A) -> OMB-8B quality, physical evidence and release
```

Work may proceed in parallel only when ownership is disjoint. OMB-6B visual identity does not block repository-side OMB-7 product-flow/evidence implementation, but it still gates final OMB-7 product identity and release closure. OMB-8A may prepare deterministic quality evidence once finding semantics are stable; the integrated corpus and pre-registered policy still do not satisfy supported-model quality claims, physical evidence or release gates. The real inference path must never bypass unmet parent Consumer API gates.

## OMB-0 — Decisions and technical spikes

State: **DONE**

Integrated through PR #106 after the exact-head repository/documentation gates and the dedicated OMBRA PDF runtime workflow passed.

Accepted evidence and decisions:

- OMBRA naming and package/signing boundary are fixed for v1;
- v1 accepts text-bearing PDF only, normalized flattened export and mandatory human review;
- `PdfBox-Android` runs behind a permissionless Android `isolatedProcess` parser boundary;
- malformed, encrypted and image-only inputs fail closed/non-plaintext as specified;
- cancellation, parser reuse and resource cleanup are covered by the runtime spike;
- framework `PdfDocument` export round-trips placeholders while source PII values remain absent;
- representative European glyphs round-trip and unsupported glyphs fail closed;
- the measured PDF-enabled debug APK delta is recorded and accepted for the reference-consumer path;
- `document-pii-detection`, `STATELESS`, fixed `JSON_SCHEMA`, reasoning-disabled behavior and host-owned model/preset selection require no new Consumer API/Binder primitive.

Canonical evidence: [`omb0-decisions-and-spikes.md`](omb0-decisions-and-spikes.md).

## OMB-1 — Pure domain and application state

State: **DONE**

Integrated through PR #108 after exact-head Documentation validation and repository Validate passed. The focused OMBRA JVM gate also passed Spotless, Detekt and Console unit tests on the same implementation.

Owner: [`architecture.md`](architecture.md)

Completed boundary:

- Android-independent document segment, PII definition, finding, occurrence, decision and redaction models;
- built-in/custom definition validation with bounded content-free identifiers;
- immutable workflow state, operation IDs, typed effects and focused start/completion/lifecycle transition groups;
- replaceable asynchronous extractor, analysis-client and exporter ports;
- sensitive in-memory task storage below presentation;
- separated task mutations through `OmbraTaskActions`, keeping orchestration responsibility narrow;
- cancellation remains `CANCELLING` until the active port acknowledges local termination/cleanup;
- callbacks verify active operation identity before any sensitive-store mutation;
- reset/process recreation clear sensitive task memory while monotonic operation identity prevents stale callback reuse;
- deterministic JVM coverage drives import -> definitions -> fake validated findings -> review decisions -> export, including zero-finding export, retry, reset, process recreation and late-callback rejection.

Exit gate: **PASSED**. A pure JVM test drives import metadata -> definitions -> fake candidates -> decisions -> export outcome without Android UI, Binder or model code.

## OMB-2 — PDF import and extraction

State: **DONE**

Integrated through OMB-2A and PR #154, which closed the picker, typed-failure, source-lifecycle and device-evidence exit gate.

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Completed boundary:

- production `AndroidOmbraDocumentExtractor` adapts the reviewed isolated PdfBox reader to the asynchronous application port;
- process-local `OmbraDocumentSourceRef` resolution keeps raw URI/display-name data outside reducer state and content-free debug surfaces;
- deterministic page/block segmentation produces stable `DocumentSegment` identities and rejects unsupported control characters fail-closed;
- a PDF-only `ACTION_OPEN_DOCUMENT` capability requests transient read access only, rejects non-content picker results and converts the selected URI immediately into an opaque source reference;
- no write or persistable URI permission is requested and no persistable grant is retained;
- encrypted, malformed, unreadable, truncated, blank/image-only and unexpected parser outcomes map to bounded typed application failures;
- cancellation waits for coroutine/reader termination before acknowledgement;
- reset and process recreation release all document-source capabilities and sensitive task state;
- generated, blank, encrypted, malformed, truncated, picker and cancellation paths are covered by focused JVM/instrumentation evidence.

Exit gate: **PASSED**. Supported fixtures produce deterministic page-ordered segments; unsupported and cancelled inputs close resources and reset retains neither sensitive task data nor source capability state.

## OMB-3 — Analysis composition and fake inference

State: **DONE**

Integrated through OMB-3A PR #148 and OMB-3B PR #202.

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Completed boundary:

- versioned stable instruction, deterministic data serialization and fixed JSON schema;
- capability-aware sequential chunk planning using public `ConsumerLimits`, including bounded Unicode-safe fragmentation;
- dependency-free bounded parsing of untrusted structured model output;
- validation of selected type, submitted segment membership and exact local source surface before a finding is admitted;
- deterministic merge/deduplication and explicit cross-type overlap conflicts;
- fake-driven sequential orchestration for success, malformed/hostile output, later-chunk failure, disconnect and cancellation;
- fail-closed handling prevents invented, unselected or partial findings from becoming replacements.

Exit gate: **PASSED**. Synthetic documents round-trip through a fake structured-output client into source-validated findings; invented/unselected findings cannot become replacements.

## OMB-4 — Harness use case and Consumer API integration

State: **DONE**

Integrated through OMB-4A PR #144 and final OMB-4B PR #210 on the accepted CA-0..CA-4 Consumer API boundary.

Completed boundary:

- host-owned `document-pii-detection` policy reuses curated model resolution and fixes the reviewed host-owned default preset;
- OMBRA advertises/accepts only the reviewed `STATELESS` + `JSON_SCHEMA` behavior with reasoning disabled/not surfaced;
- public `ConsumerLimits` drive bounded input/schema planning;
- `OmbraBinderAnalysisComposition` uses `BinderConsumerLocalLlmClient` through the packaged Consumer API rather than raw runtime/model identity;
- typed capability, compatibility, disconnect, execution-identity and cancellation failures map into application-owned OMBRA outcomes;
- sessions are created/closed per operation and preset/execution identity is revalidated fail-closed;
- focused coverage exercises defaults/limits, substitution attempts, incompatible reasoning, disconnect/runtime failure, cancellation and surfaced-reasoning rejection.

Exit gate: **PASSED for repository-side integration**. The packaged client completes the OMBRA Consumer/Binder path without consumer-provided model identity, raw tuning or AIDL types. Physical two-APK evidence remains OMB-8/CA-6 and is not inferred from this milestone.

## OMB-5 — Review, redaction and PDF export

State: **DONE**

Integrated through PR #146 (deterministic redaction planning), PR #157 (flattened PDF export boundary) and PR #218 (safe hidden/reveal review projection).

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Completed boundary:

- accepted/ignored decisions, unresolved-overlap blocking and deterministic placeholder numbering;
- hidden review projection that carries placeholders only, with one explicit in-memory reveal target at a time and deterministic reveal cleanup;
- exact-source validation and highest-offset-first replacement preserve ignored values while removing accepted values;
- process-local export-destination capabilities keep raw destination URIs out of workflow state;
- PDF-only `ACTION_CREATE_DOCUMENT` uses transient grants and the reviewed flattened writer to generate a new normalized PDF rather than copy source objects/bytes;
- destination/write failure and cancellation remove partial output best-effort and release the destination capability;
- independent re-parse instrumentation verifies accepted source values absent, placeholders present and ignored values retained;
- zero-finding export remains valid.

Exit gate: **PASSED for the document/review/export boundary**. Route/effect presentation wiring is owned by OMB-7; physical end-to-end release evidence remains OMB-8.

## OMB-6 — Design system and identity

State: **IN PROGRESS**

Owner: [`ux-and-brand.md`](ux-and-brand.md)

### OMB-6A — Design system and reusable product components

State: **DONE**

Integrated through PRs #145, #200 and #220:

- separate OMBRA light/dark Material 3 schemes, semantic tones, typography fallback, spacing, shapes and 48 dp target contract;
- contrast/accessibility token coverage;
- reusable task actions, status/progress, definition selection, review warning, placeholder and export primitives;
- `OmbraScaffold`, document picker, custom-definition sheet, finding inspector, decisions/navigation and light/dark previews;
- hidden shared components accept only safe placeholder/semantic inputs unless the caller explicitly reveals a value.

### OMB-6B — Final vector identity and Android launcher

State: **IN PROGRESS — REVIEW GATED**

PR #248 is the clean replay of the validated tooling/candidate lane and remains open. It deliberately does not approve the symbol, final wordmark/lockup or production launcher; the candidate remains `REVIEW REQUIRED`.

Remaining:

- complete visual review of the symbol candidate;
- freeze final wordmark/lockup decisions;
- generate deterministic adaptive/monochrome launcher assets and packaging checks from approved masters;
- integrate final identity without changing the approved package/signing boundary.

Exit gate: **OPEN** until approved vector masters and generated Android identity are deterministic and integrated. OMB-6A was sufficient for repository-side OMB-7 work; OMB-6B is now the remaining OMB-7 closure dependency and remains a release dependency.

## OMB-7 — Compose product flow and Console retirement

State: **IN PROGRESS — REPOSITORY-SIDE EVIDENCE COMPLETE, IDENTITY GATED**

Owner: [`ux-and-brand.md`](ux-and-brand.md)

Integrated OMB-7A — PR #232:

- replaces the legacy Console entry point with Compose OMBRA Import -> Definitions/custom definitions -> local Analysis -> Review-ready flow;
- keeps document/definitions/findings process-local and outside SavedState/routes;
- preserves task state across configuration changes while process recreation starts fresh;
- wires the production PDF source capability and Consumer API Binder analysis composition;
- surfaces real Harness connection state and adopts `OmbraTheme`/OMBRA app label.

Integrated OMB-7B — PR #235:

- wires safe Review projection, single-occurrence reveal, per-occurrence `Oscura`/`Ignora`, navigation and conflict blocking;
- wires `CreateDocument("application/pdf")` export, including fresh-destination behavior after a failed export;
- handles valid zero-PII documents;
- retires legacy Console presenter/cache/health/inference/inventory surfaces and their tests;
- removes direct `models:model-store` and `observability:*` dependencies from `apps/local-llm-console`, leaving the reference app on public contracts, Binder Consumer API, document/PDF dependencies and the shared design system;
- keeps cancellation/reset/process-local cleanup semantics and the OMBRA PDF runtime gate intact.

Integrated OMB-7C review/privacy/accessibility evidence — PR #250:

- clean replay from the post-OMB-7B `dev` baseline;
- asserts hidden review semantics do not expose source PII and explicit reveal exposes only the selected occurrence;
- verifies unresolved overlap/conflict keeps export blocked and zero-PII review remains exportable;
- verifies the accepted-review primary action remains reachable at 200% font scale;
- adds a dedicated OMBRA UI emulator evidence workflow;
- narrows the PDF runtime workflow trigger so UI-only instrumentation does not unnecessarily invoke the heavyweight document/PDF evidence lane.

Integrated final repository-side product evidence — PR #259:

- extends the Compose instrumentation matrix with explicit Review reset coverage;
- verifies the export-progress cancellation action remains reachable and invokes the cancellation boundary;
- runs the privacy/review/accessibility matrix in portrait and landscape on the API 35 emulator;
- captures representative code-owned Import-screen screenshots in portrait and landscape;
- uploads only privacy-safe synthetic/empty UI evidence;
- merged only after exact-head repository validation and OMBRA UI state evidence were both green.

Remaining before OMB-7 can be `DONE`:

- integrate the final OMB-6B approved production identity, including deterministic adaptive/monochrome launcher assets and packaging checks.

Exit gate: **OPEN ONLY ON OMB-6B IDENTITY**. The pure OMBRA Consumer API app flow and repository-side product state/evidence matrix are integrated through PR #259. Emulator evidence is not physical OMB-8 release evidence, and OMB-7 must not be marked `DONE` until OMB-6B is approved and integrated.

## OMB-8 — Quality, physical evidence and release

State: **IN PROGRESS**

Owner: [`validation-and-rollout.md`](validation-and-rollout.md)

Integrated quality-corpus preparation — OMB-8A:

- PR #223 introduced the versioned SHA-256-frozen synthetic quality corpus and deterministic exact-occurrence scorer;
- PR #253 preserves the historical v1 corpus and makes `ombra-pii-synthetic-v2` the active corpus;
- corpus v2 contains 32 fully synthetic cases and guarantees at least five positive exact occurrences for every built-in PII category plus the custom category;
- v2 includes positive, zero-PII, repeated, overlap, near-miss, injection-like, Unicode/Italian punctuation and category-focused variations;
- the active corpus identity is SHA-256-pinned and regression-tested together with the minimum per-category support floor.

Integrated pre-registered quality policy — OMB-8B / PR #252:

- policy v1 is pinned to the exact active `ombra-pii-synthetic-v2` identity/hash and all seven required categories;
- aggregate gates are precision >= 0.90, recall >= 0.98 and F1 >= 0.94;
- per-category gates are precision >= 0.80, recall >= 0.90 and F1 >= 0.85;
- structured completion must be >= 0.98, invalid finding rate <= 0.02 and invalid result rate = 0.00;
- the gate fails closed on corpus identity mismatch, missing required categories or threshold failures and returns deterministic typed failure reasons;
- replay regression proves `policy.corpusIdentity` equals the active corpus loader identity and the policy required-type set equals the active built-in-plus-custom type set;
- the policy was replayed on the exact post-#253 `dev` baseline and merged only after exact-head Android/repository and documentation validation passed.

Remaining:

- execute the exact active corpus on each reviewed supported Qwen3.5 artifact/configuration and evaluate results against policy v1 before any model/category support claim;
- preserve exact artifact, preset, corpus and policy identity in quality evidence and do not lower policy v1 to fit observed results;
- complete privacy/security, parser dependency, public-copy and packaged-APK reviews;
- execute physical same-signer two-APK import/analysis/review/export and failure scenarios;
- verify output independently on the exact distributed build and capture privacy-safe evidence;
- finalize API/app version, release notes, shrinker, signing and compatibility documentation together with applicable CA-6/CA-7/SR prerequisites.

Exit gate: **OPEN**. Corpus v2 and policy v1 are deterministic preparation/acceptance infrastructure, not proof that a reviewed model passes them. The exact distributed build must meet the pre-registered quality policy, OMBRA physical validation completion criteria and applicable Consumer API/SR prerequisites before support/release claims.

## State rule

Mark a milestone `IN PROGRESS` only after its first implementation slice begins and `DONE` only when the stated exit gate is integrated and tested. Parallel preparation may start when ownership is disjoint, but it never upgrades a downstream physical/release claim. A generated visual board, emulator screenshot, synthetic corpus, registered policy or successful fake model does not complete a physical, quality or release gate.
