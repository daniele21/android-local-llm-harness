# OMBRA architecture and ownership

Status: active
Document type: architecture
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.architecture
Read when: placing OMBRA PDF, PII, inference, redaction, export or presentation logic
Last reviewed: 2026-08-13

## Deployment shape

```text
OMBRA process (`apps/local-llm-console`)
  system document picker
    -> PDF reader + text extractor
      -> normalized DocumentSegments
        -> selected PiiDefinitionSet
          -> AnalysisRequestComposer
            -> packaged SharedLocalLlmClient
              == authenticated Binder boundary ==
                -> Harness document-pii-detection policy
                  -> reviewed model + deterministic preset
                    -> JSON_SCHEMA result stream
          <- validated PiiFindings
        -> RedactionPlan + review decisions
      -> new flattened PDF through user-selected destination
```

Harness never receives the PDF URI, PDF file, export destination, reveal state or user review decisions. OMBRA never imports runtime, model-store, backend or host diagnostics implementations.

## Existing application transition

`apps/local-llm-console` currently combines Binder inference with observability, health, cache, model-inventory and raw request controls. CA-5 and this plan change its target into a pure packaged-SDK consumer.

Implementation should:

1. preserve the existing package/signing relationship needed by the same-signer service boundary;
2. isolate the current Binder composition behind one consumer-facing inference adapter;
3. remove model-store, health-engine and telemetry-store dependencies after their screens and presenters are removed;
4. remove consumer-controlled temperature, seed and raw output-budget controls;
5. migrate the Activity from programmatic Views to a Compose composition/navigation/effect root;
6. replace the generic `console-inference-playground` target only after the host authorizes `document-pii-detection` and compatibility tests exist.

The module/package rename is not part of the first slice. User-facing application label, icon and theme may become OMBRA while internal Gradle coordinates remain stable.

## Package boundaries

Keep the first vertical slice inside the application module. Create a new Gradle module only when a second consumer proves a reusable domain boundary.

| Package concept | Owns | Must not own |
| --- | --- | --- |
| `document` | picker result mapping, PDF metadata, extraction, normalization and segments | Compose, Binder, PII policy or export UI |
| `pii` | built-in/custom definitions, selection and stable identifiers | model IDs, prompt templates or Android storage |
| `analysis` | prompt payload, schema, chunk planning, finding validation and merge | Binder implementation, PDF rendering or UI |
| `inference` | narrow adapter over the packaged Consumer API and capability mapping | PII rules, model installation or generated-content persistence |
| `redaction` | occurrence resolution, decisions, placeholders and export model | LLM calls, document picker or Compose |
| `presentation` | immutable screen state, reducers, effects and Compose views | PDF parsing, file writes, Binder/AIDL or policy duplication |
| Activity/composition | lifecycle, Activity Result launchers, dependency assembly and navigation host | domain state machines or long-running work |

Interfaces are injected at the boundaries so PDF, Binder and destination I/O can be replaced with deterministic fakes.

## Domain model

Conceptual application-owned types:

```kotlin
data class DocumentDescriptor(
    val displayName: String,
    val pageCount: Int,
)

data class DocumentSegment(
    val id: SegmentId,
    val pageIndex: Int,
    val blockIndex: Int,
    val normalizedText: String,
)

data class PiiDefinition(
    val id: PiiTypeId,
    val label: String,
    val definition: String,
    val example: String? = null,
    val source: PiiDefinitionSource,
)

data class ValidatedFinding(
    val typeId: PiiTypeId,
    val surface: String,
    val occurrences: List<SourceOccurrence>,
)

data class RedactionDecision(
    val occurrenceId: OccurrenceId,
    val accepted: Boolean,
)
```

Android `Uri`, PDF parser objects, Binder parcelables and native/backend types do not enter these models.

## State ownership

One ViewModel-owned immutable state machine drives the task:

```text
Idle
 -> DocumentSelected
 -> DefinitionsReady
 -> Extracting
 -> Connecting / Preparing
 -> Analyzing(chunk n of total)
 -> Merging
 -> ReviewReady
 -> Exporting
 -> Exported
```

`Failed` and `Cancelling` are explicit states with a safe retry target. Navigation renders state; it never initiates extraction, inference or export during composition.

Sensitive fields such as segments, findings and reveal mappings remain in process-memory repositories owned by the ViewModel graph. Saved state may retain only non-sensitive route/stage hints; after process death the workflow returns to import.

## Effects

The presentation layer emits typed effects for:

- opening the PDF picker;
- opening the create-document destination picker;
- reading a granted URI;
- connecting/preparing/generating/cancelling through the Consumer API;
- exporting and optionally opening/sharing the completed document;
- announcing important accessibility state changes.

Every effect has one operation ID. Late callbacks are ignored after cancellation or a newer operation becomes active. Terminal state is accepted exactly once.

## Consumer API mapping

OMBRA uses a high-level SDK facade only:

```text
connect/negotiate
 -> capabilities(document-pii-detection)
 -> verify JSON_SCHEMA + limits + default readiness
 -> prepare(default model, deterministic preset, reasoning disabled)
 -> create STATELESS session
 -> generate one chunk
 -> close session
```

Repeated chunks reuse only lifecycle behavior explicitly supported by the host. OMBRA does not assume context reuse, parallel decode or automatic request replay after reconnect. A disconnect invalidates active sessions; the user may restart analysis from a clear partial state.

The host derives `ApplicationId` from the verified caller. Consumer code contains a `UseCaseId` but never supplies identity as authorization evidence.

## PDF and storage boundary

- Use Activity Result contracts and the Storage Access Framework; do not request broad filesystem access.
- Read the source through a bounded stream/file-descriptor adapter.
- Do not request persistable URI permission by default for a one-shot task.
- Close descriptors and parser resources on success, failure and cancellation.
- Keep extraction scratch data in bounded memory or a private ephemeral file with deterministic cleanup.
- Write output only after the user chooses a destination.
- Never overwrite the input URI or export an opaque overlay that preserves recoverable original text.

Parser selection is an implementation spike because Android's renderer is not a complete text-extraction API. The selected dependency must support the repository min SDK, have an acceptable license and size, close native/Java resources deterministically and pass representative text-PDF fixtures. OCR dependencies remain out of scope.

## Prompt-injection boundary

PDF text and custom definitions are untrusted content. The request composer:

- uses stable instructions separate from serialized data blocks;
- labels document content as data that cannot change the task;
- serializes definitions and segments rather than interpolating ad hoc prose;
- validates length and control characters before inference;
- never accepts a document-provided schema, use case, model or runtime option;
- asks only for findings allowed by the selected definition IDs.

These measures reduce instruction confusion but do not make model output authoritative. Source validation and human review remain mandatory.

## OMBRA design-system boundary

The existing `HarnessTheme` and purple/teal engineering identity stay unchanged. OMBRA adds a separate token namespace in `ui/design-system`, for example `OmbraTheme`, `OmbraColors`, `OmbraTypography` and `OmbraShapes`.

Shared interaction primitives may be reused when they consume `MaterialTheme` without hardcoded Harness semantics. Brand-specific components remain named and scoped explicitly. Do not copy palette constants into individual OMBRA screens or recolor existing Harness tokens globally.

## Telemetry and diagnostics

OMBRA may record only privacy-safe lifecycle data needed to validate the SDK integration, such as:

- app/SDK/host/protocol versions;
- use-case identifier;
- operation phase and typed outcome;
- page/segment/chunk counts without text;
- host-provided token/timing metrics;
- export byte/page count without URI or filename when safe.

It must not log or persist PDF names when they may contain PII, URIs, definitions, examples, prompts, schemas, extracted text, findings, revealed values or output document content. Harness-side privacy rules remain unchanged.

## Dependency direction

```text
presentation -> application orchestrators -> document/pii/analysis/redaction interfaces
                                   -> inference adapter -> packaged Consumer API

document implementation -> Android document/PDF APIs or reviewed parser
export implementation   -> Android PDF/destination APIs
OMBRA Compose views      -> ui/design-system
```

No dependency points from the Consumer API, transport or Harness host back into OMBRA domain packages.

## Architectural gates

- The app compiles without model-store, runtime-core, llama.cpp/JNI and host observability implementations.
- PDF and custom-PII behavior is testable with no Android UI and no model.
- Screens contain no Binder, URI-stream, parser or PDF-writer logic.
- Harness contains no OMBRA prompt builder, PII type or redaction/export class.
- One owner changes each rule: consumer policy in OMBRA, inference policy in Harness, transport semantics in the Consumer API.
- Process death, cancellation and partial failure leave no exported or scratch artifact unless the user completed the destination write.
