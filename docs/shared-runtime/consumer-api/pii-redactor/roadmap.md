# OMBRA implementation roadmap

Status: active
Document type: roadmap
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.roadmap
Read when: selecting the next OMBRA implementation slice, dependency or exit gate
Last reviewed: 2026-08-15

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

State: **DONE**

Integrated through OMB-2A / PR #131 and OMB-2B / PR #154. PR #154 passed exact-head Documentation validation, repository Validate and the dedicated OMBRA PDF runtime emulator workflow before squash integration into `dev`.

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Completed boundary:

- production `AndroidOmbraDocumentExtractor` adapts the reviewed isolated PdfBox reader to the asynchronous application port;
- process-local `OmbraDocumentSourceRef` resolution keeps raw URI/display-name data outside reducer state and content-free debug surfaces;
- deterministic page/block segmentation produces stable `DocumentSegment` identities and rejects unsupported control characters fail-closed;
- a PDF-only `ACTION_OPEN_DOCUMENT` capability requests transient read access only, rejects non-content picker results and converts the selected URI immediately into an opaque process-local source reference;
- no write or persistable source-URI permission is requested or retained;
- production failures map encrypted, malformed, unreadable, image-only, empty and bounded/truncated inputs to typed content-free outcomes;
- cancellation waits for coroutine/reader termination before acknowledgement;
- workflow reset and process recreation release document source capabilities and clear sensitive task state;
- generated, blank/image-only, encrypted, malformed, truncated/limit and cancellation paths are covered through the production adapter and runtime emulator suite.

Exit gate: **PASSED**. Supported fixtures produce deterministic page-ordered segments; unsupported and cancelled inputs close resources and no sensitive task data or source capability survives reset/process recreation.

## OMB-3 — Analysis composition and fake inference

State: **IN PROGRESS**

Active slice: **OMB-3B — bounded result parsing, source validation, deterministic merge and fake analysis flow**.

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Integrated OMB-3A baseline through PR #148:

- versioned application-owned instruction and fixed JSON Schema;
- deterministic definition/document serialization;
- capability-aware sequential chunk planning using public `ConsumerLimits`;
- whole-block preservation and Unicode code-point-safe fragmentation with stable fragment IDs;
- fail-closed schema/input-overhead handling;
- focused ordering, fragmentation, Unicode, limit and deterministic-serialization JVM coverage.

Current OMB-3B work:

- PR #156 is the clean replay from current `dev` for the result-validation portion;
- bounded dependency-free JSON parsing rejects duplicate keys, trailing prose, unsupported shapes/versions and unsafe Unicode;
- submitted fragment IDs are deterministically mapped back to normalized source offsets;
- returned type IDs must be selected, segment IDs must belong to the actual submitted chunk and surfaces must match exact source substrings before `ValidatedFinding` creation;
- exact occurrences are derived locally rather than trusted from model output;
- exact duplicates are collapsed and cross-type overlaps become explicit conflicts rather than automatic replacements;
- hostile JSON, invented/unselected findings, fragment mapping, deduplication and overlap fixtures are covered in focused JVM tests.

Remaining before OMB-3 can be `DONE`:

- exact-head repository validation for the clean OMB-3B replay;
- sequential fake-client orchestration across all planned chunks;
- explicit success, incomplete/invalid-result, partial failure, cancellation and disconnect orchestration coverage;
- prove the OMB-3 exit gate end-to-end from synthetic document + definitions through fake structured outputs into a validated redaction plan.

Exit gate: synthetic documents round-trip through a fake structured-output client into a validated redaction plan; invented/unselected findings can never become replacements.

## OMB-4 — Harness use case and Consumer API integration

State: **IN PROGRESS**

Dependencies: accepted/implemented parent CA-0, CA-1, CA-2, CA-3 and CA-4 slices applicable to discovery, defaults, output constraint, result and Binder mapping.

Integrated OMB-4A baseline through PR #144:

- host-owned `document-pii-detection` use-case identity;
- host-selected curated Qwen3.5 artifact rather than consumer model identity;
- fixed reviewed `qwen35-json` preset;
- `JSON_SCHEMA`, `STATELESS` and reasoning-not-supported policy;
- conservative public input/schema/message limits;
- least-privilege package authorization and pure JVM policy coverage.

Remaining OMB-4B work:

- implement the packaged Consumer API adapter behind `OmbraAnalysisClient` after the accepted OMB-3 validation boundary;
- run capability discovery -> prepare -> stateless session -> sequential generate -> terminal result -> close;
- map unavailable/disconnected/incompatible/cancelled host outcomes to typed OMBRA application failures;
- add in-process, Binder, compatibility and same-signer PII use-case coverage;
- keep raw tuning, consumer model identity and AIDL types out of OMBRA.

Exit gate: the packaged client completes discover -> prepare -> sequential generate -> terminal result -> close for the PII use case without consumer-provided identity, raw tuning or AIDL types.

## OMB-5 — Review, redaction and PDF export

State: **IN PROGRESS**

Active slice: **OMB-5B — normalized PDF export and independent verification**.

Owner: [`detection-and-redaction.md`](detection-and-redaction.md)

Integrated OMB-5A baseline through PR #146:

- review decisions are validated against normalized source before replacement;
- pending decisions, unknown sources/definitions, duplicate occurrences and source mismatch fail closed;
- accepted exact/partial overlaps become explicit conflicts;
- placeholder keys are bounded, sanitized, collision-safe and numbered deterministically by accepted source order;
- replacement is applied highest-offset-first while ignored values are preserved;
- zero-finding export remains valid;
- focused JVM coverage proves ordering, offset stability, conflicts, mismatch, pending review, key collisions and zero findings.

Current OMB-5B work:

- PR #157 adds an opaque process-local export destination capability and PDF-only `ACTION_CREATE_DOCUMENT` boundary;
- export requests carry active definitions needed by the deterministic OMB-5A planner;
- the production exporter rebuilds normalized page text and writes a newly generated flattened PDF through the reviewed writer rather than copying source-PDF objects/bytes;
- incomplete review, conflicts/source mismatch, destination failures and writer failures map to content-free typed outcomes;
- failure/cancellation paths delete partial output best-effort and release destination capabilities;
- instrumentation independently re-parses generated output to assert accepted values absent, placeholders present and ignored values retained.

Remaining before OMB-5 can be `DONE`:

- exact-head repository and PDF instrumentation validation for OMB-5B;
- complete cancellation/partial-output cleanup evidence on the production exporter boundary;
- integrate the validated slice into `dev`.

Exit gate: a synthetic source becomes a newly generated, independently verifiable PDF; no accepted value or source attachment remains recoverable from output.

## OMB-6 — Design system and identity

State: **IN PROGRESS**

Owner: [`ux-and-brand.md`](ux-and-brand.md)

Integrated OMB-6A baseline through PR #145:

- dedicated OMBRA light/dark Material 3 color schemes;
- semantic status tones;
- deterministic offline typography fallback, reviewed spacing/shapes and 48 dp minimum target token;
- standalone `OmbraTheme` without changing the existing Harness theme;
- contrast and token contract JVM coverage.

Remaining OMB-6 work:

- reusable OMBRA task, definition, progress, redaction, review and export primitives/previews;
- reviewed vector masters for symbol, wordmark and lockup;
- deterministic adaptive/monochrome launcher generation and packaging checks;
- final component preview/accessibility matrix.

The repository currently retains the reviewed brand kit as a raster reference board, not an approved vector master. OMB-6B must therefore recreate/approve the vector source deliberately rather than infer a release identity silently from the PNG board.

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
| OMB-2A | Production PDF extractor and deterministic segmentation |
| OMB-2B | PDF picker, typed failure mapping and source lifecycle cleanup |
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
