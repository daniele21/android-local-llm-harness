# OMBRA implementation roadmap

Status: active
Document type: roadmap
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.roadmap
Read when: selecting the next OMBRA implementation slice, dependency or exit gate
Last reviewed: 2026-08-14

This roadmap owns implementation order and milestone state. Detailed behavior stays in the focused OMBRA specifications. Repository priority belongs in [`../../../current-state.md`](../../../current-state.md); generic Consumer API milestones remain in [`../roadmap.md`](../roadmap.md).

## Sequence

```text
OMB-0 Decisions and technical spikes
  -> OMB-1 Pure domain and application state
      -> OMB-2 PDF import and extraction
      -> OMB-3 Analysis composition and fake inference
  -> OMB-4 Real Consumer API use case/integration   [depends on CA-0..CA-4]
  -> OMB-5 Review, redaction and PDF export

OMB-0 -> OMB-6 OMBRA design system and vector identity
OMB-1..OMB-6 -> OMB-7 Compose product flow / Console retirement
OMB-7 -> OMB-8 Quality, physical evidence and release
```

Work may proceed in parallel only when ownership is disjoint. The real inference path must not bypass unmet parent Consumer API gates.

## OMB-0 — Decisions and technical spikes

State: **IN PROGRESS**

Active slice: **OMB-0A — target/architecture decisions and bounded parser/export spike record**.

Goal: accept the target and remove high-risk uncertainty before product code grows.

Tasks:

- accept OMBRA naming as product label while preserving Harness host identity and current package/signing boundary;
- accept text-bearing PDF only, normalized-layout export and mandatory human review;
- run a bounded parser spike for extraction fidelity, min SDK, licensing, APK size, malformed-input behavior and cleanup;
- run a PDF writer/font/glyph/export-verification spike;
- freeze built-in category definitions, fixed result schema and placeholder rules;
- confirm `document-pii-detection`, reasoning-disabled behavior and deterministic preset direction;
- accept the OMBRA light palette and choose bundled-font review versus deterministic system fallback;
- classify any needed Consumer API/wire feature before implementation.

Exit gate: product, parser/export, schema, use-case and brand decisions are reviewable; no unresolved choice would force a different architecture.

## OMB-1 — Pure domain and application state

State: **PLANNED**

Owner: [`architecture.md`](architecture.md)

Tasks:

- add Android-independent document segment, PII definition, finding, occurrence, decision and redaction models;
- add built-in/custom definition validation;
- implement immutable workflow state, reducer, operation IDs and typed effects;
- define interfaces for extractor, analysis client, exporter and sensitive in-memory task store;
- cover cancellation, late callbacks, reset and process-recreation semantics with fakes.

Exit gate: a pure JVM test drives import metadata -> definitions -> fake candidates -> decisions -> export outcome without Android UI, Binder or model code.

## OMB-2 — PDF import and extraction

State: **PLANNED**

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Tasks:

- integrate the reviewed PDF reader behind the extractor interface;
- wire `OpenDocument` with PDF MIME filtering and least-privilege URI handling;
- implement metadata inspection, page/block normalization and stable source mapping;
- add resource/page/byte bounds and typed encrypted/image-only/malformed outcomes;
- add synthetic PDF generators/fixtures and extraction/cancellation tests.

Exit gate: supported fixtures produce deterministic page-ordered segments; unsupported and cancelled inputs close all resources and retain no sensitive task data after reset.

## OMB-3 — Analysis composition and fake inference

State: **PLANNED**

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Tasks:

- version the stable instruction and data serialization;
- implement capability-aware sequential chunk planning;
- add the fixed JSON schema and bounded parser;
- validate selected type, segment membership and exact source surface;
- implement deterministic merge, deduplication and overlap conflicts;
- test injection-like documents and malformed/hostile result fixtures;
- complete fake-client orchestration for success, partial failure, cancellation and disconnect.

Exit gate: synthetic documents round-trip through a fake structured-output client into a validated redaction plan; invented/unselected findings can never become replacements.

## OMB-4 — Harness use case and Consumer API integration

State: **PLANNED**

Dependencies: accepted/implemented parent CA-0, CA-1, CA-2, CA-3 and CA-4 slices applicable to discovery, defaults, output constraint, result and Binder mapping.

Tasks:

- add one host-owned `document-pii-detection` application/use-case policy reusing existing model resolution;
- advertise only the reviewed default logical model/preset, `STATELESS` and `JSON_SCHEMA` capabilities initially;
- keep reasoning disabled/not surfaced and enforce bounded input/schema limits;
- replace OMBRA's direct/raw `LocalLlmClient` playground control with the packaged consumer facade;
- map typed host/connection/capability failures to application outcomes;
- add in-process, Binder, compatibility and same-signer tests.

Exit gate: the packaged client completes discover -> prepare -> sequential generate -> terminal result -> close for the PII use case without consumer-provided identity, raw tuning or AIDL types.

## OMB-5 — Review, redaction and PDF export

State: **PLANNED**

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Tasks:

- implement accepted/ignored/conflict decisions and deterministic placeholder numbering;
- build normalized hidden/reveal preview models with no hidden semantics leakage;
- implement highest-offset-first replacement;
- integrate the reviewed flattened-PDF writer and `CreateDocument` destination flow;
- verify accepted values absent, placeholders present and ignored values retained in generated fixtures;
- cover destination denial, partial write, cancellation and cleanup.

Exit gate: a synthetic source becomes a newly generated, independently verifiable PDF; no accepted value or source attachment remains recoverable from output.

## OMB-6 — Design system and identity

State: **PLANNED**

Owner: [`ux-and-brand.md`](ux-and-brand.md)

Tasks:

- add separate OMBRA light/dark color schemes, typography, shapes and semantic tones in `ui/design-system`;
- verify contrast, 48 dp targets and offline font policy;
- implement reusable OMBRA task, definition, progress, redaction, review and export primitives;
- recreate symbol, wordmark and lockup as reviewed vector masters;
- add deterministic adaptive/monochrome launcher generation and packaging checks;
- add component previews aligned with the brand board.

Exit gate: screens need no local palette/type/shape constants; vector masters and generated Android identity are deterministic; accessibility token tests and light/dark previews pass.

## OMB-7 — Compose product flow and Console retirement

State: **PLANNED**

Owner: [`ux-and-brand.md`](ux-and-brand.md)

Tasks:

- implement Import, Definitions, custom sheet, Analysis, Review and Export routes from immutable state;
- wire picker/inference/export as exactly-once lifecycle-aware effects;
- implement Back, cancellation, reset and process-death behavior;
- remove Console dashboard, health, caches, inventory, logs, benchmarks and raw playground surfaces;
- remove forbidden runtime/model/observability dependencies;
- add semantics, navigation, adaptive, large-font and screenshot coverage for the full state matrix;
- update app label/theme/icon to OMBRA while preserving approved package/signing identity.

Exit gate: `apps/local-llm-console` is a pure OMBRA reference consumer using only packaged inference contracts, document-domain dependencies and the shared design-system module.

## OMB-8 — Quality, physical evidence and release

State: **PLANNED**

Owner: [`validation-and-rollout.md`](validation-and-rollout.md)

Tasks:

- complete synthetic per-category/custom PII quality evaluation for supported Qwen3.5 artifacts;
- accept quality thresholds and supported model/category claims;
- rely on parent contract/Binder/package fixtures for optional model, preset and reasoning capabilities intentionally absent from OMBRA;
- run privacy/security, parser dependency, public copy and packaged-APK reviews;
- execute physical same-signer two-APK import/analysis/review/export and failure scenarios;
- verify output content independently and capture privacy-safe evidence;
- finalize API/app version, release notes, shrinker, signing and compatibility documentation.

Exit gate: the exact distributed build meets the OMBRA validation completion criteria and applicable Consumer API/SR prerequisites; the OMB-8 release portion closes jointly with CA-7. No legal compliance or guaranteed-detection claim is introduced.

## Recommended pull-request slices

| Slice | Deliverable |
| --- | --- |
| OMB-0A | Target/architecture decisions and parser/export spike report in owning docs |
| OMB-1A | Pure models, definitions and validation |
| OMB-1B | Reducer, effects and fake application orchestrator |
| OMB-2A | PDF picker/extractor and fixture generator |
| OMB-3A | Prompt/schema/chunk planner |
| OMB-3B | Result validation, merge and fake analysis flow |
| OMB-4A | Host use-case policy after parent capability slice |
| OMB-4B | Packaged Consumer API adapter and Binder coverage |
| OMB-5A | Review decisions, occurrences and placeholders |
| OMB-5B | New-PDF export and independent verification |
| OMB-6A | OMBRA themes, tokens, component previews and accessibility tests |
| OMB-6B | Vector masters, launcher generator and package checks |
| OMB-7A | Import/definitions/custom/analysis Compose flow |
| OMB-7B | Review/export flow and old Console removal |
| OMB-8A | Quality corpus and thresholds |
| OMB-8B | Physical/security/release evidence |

Each PR implements one coherent vertical boundary, updates only the canonical state/specification that changed and runs the narrowest relevant gate plus downstream consumers.

## Validation by slice

- Pure domain: JVM unit and property/boundary tests.
- PDF/import/export: module tests, generated fixture round-trips and resource cleanup.
- Consumer API/host: contract, Binder mapping, integration, compatibility and packaged-AAR checks.
- Design system/UI: token contrast, component tests, semantics, screenshots, Lint and assembly.
- Shared contracts, Gradle, manifests or multiple apps: repository-wide Android gate.
- Distribution claim: exact physical-device evidence.

## State rule

Mark a milestone `IN PROGRESS` only after its first implementation slice begins and `DONE` only when the stated exit gate is integrated and tested. A generated visual board, emulator screenshot or successful fake model does not complete a physical, quality or release gate.
