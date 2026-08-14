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

State: **IN PROGRESS**

Active slice: **OMB-2A — PDF source capability, production extractor adapter and deterministic segment mapping**.

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Tasks:

- integrate the reviewed isolated PDF reader behind the `OmbraDocumentExtractor` application port;
- wire `OpenDocument` with PDF MIME filtering and least-privilege URI handling;
- keep raw Android `Uri` values behind process-local source capabilities rather than workflow state;
- implement metadata inspection, page/block normalization and stable source mapping;
- add resource/page/byte bounds and typed encrypted/image-only/malformed outcomes;
- add synthetic PDF generators/fixtures and extraction/cancellation tests.

Current OMB-2A progress:

- production `AndroidOmbraDocumentExtractor` adapts the reviewed isolated PdfBox reader to the asynchronous application port;
- process-local `OmbraDocumentSourceRef` resolution keeps raw URI/display-name data outside reducer state and content-free debug surfaces;
- deterministic page/block segmentation produces stable `DocumentSegment` identities and rejects unsupported control characters fail-closed;
- extraction failures are mapped to typed, content-free application outcomes;
- cancellation waits for coroutine/reader termination before acknowledging the operation;
- the existing OMBRA PDF emulator suite now includes production-extractor device coverage in addition to parser isolation/runtime evidence.

Remaining before OMB-2 can be `DONE`:

- exact-head repository/documentation validation for OMB-2A;
- emulator evidence for the production extractor path, including generated PDF extraction, blank/image-only handling and cancellation cleanup;
- complete picker wiring/least-privilege content-URI ownership needed by the OMB-2 exit gate;
- verify malformed/encrypted/limit mapping through the production adapter rather than only the underlying parser spike;
- prove reset releases source capabilities and retains no sensitive task data.

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
