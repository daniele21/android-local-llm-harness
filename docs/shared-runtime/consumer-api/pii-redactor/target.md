# OMBRA product target

Status: active
Document type: target-specification
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.target
Read when: deciding the OMBRA user outcome, MVP scope, trust boundary or completion criteria
Last reviewed: 2026-08-13

## Problem

A user should be able to inspect a PDF for personally identifiable information without uploading the document to a cloud service. The first Consumer API application must demonstrate a useful cross-APK local-inference workflow while remaining small enough to validate Binder lifecycle, structured output and privacy boundaries deterministically.

Generic prompt playgrounds prove transport but not product usefulness. OMBRA supplies a bounded document task with visible inputs, reviewable model candidates and an exported artifact whose safety properties can be tested.

## Product identity

- Product name: **OMBRA**.
- Positioning: calm local privacy with explicit human control.
- Primary promise: “I tuoi dati restano tuoi.”
- Supporting language: “Privacy locale, controllo umano.”

OMBRA is a separately installed consumer APK. Local LLM Harness remains the host/runtime product and retains its existing name, model-management interface and engineering brand.

## Target user journey

```text
open OMBRA
  -> confirm Harness is locally available
  -> select one text-bearing PDF through the system picker
  -> inspect/select built-in PII definitions
  -> optionally add bounded custom definitions
  -> start foreground analysis
  -> review validated candidate occurrences in an anonymized preview
  -> reveal, accept or ignore individual candidates
  -> choose an export destination
  -> receive a new PDF containing placeholders instead of accepted PII
```

The app never silently starts inference, persists source content for convenience or exports before the user has reached the review step.

## Responsibility split

| Concern | OMBRA consumer | Harness host |
| --- | --- | --- |
| PDF access | Uses the Android document picker and reads the granted URI | Never receives a URI or PDF bytes |
| Text extraction | Extracts, normalizes and segments locally | Receives only bounded text/messages |
| PII taxonomy | Owns built-in categories and user-defined definitions | Does not expose PII-specific types |
| Prompt payload | Encodes selected definitions and source segments as untrusted data | Applies approved chat/template/runtime policy |
| Output | Requests `JSON_SCHEMA` and validates semantics | Enforces authorized output capability and grammar |
| Model/preset | Accepts host defaults in the minimal UI | Resolves exact reviewed model and deterministic preset |
| Review | Shows candidates, original values and user decisions | Does not own document presentation |
| Export | Builds a new redacted PDF and writes to a user-selected destination | Never writes consumer documents |
| Metrics | May show safe request detail behind progressive disclosure | Produces authoritative inference metrics |
| Sensitive state | Keeps working content in memory and explicit export only | Keeps prompts/output out of normal telemetry and persistence |

## MVP functional scope

### Import and extraction

- Accept one PDF from the Storage Access Framework.
- Reject inaccessible, malformed, password-protected or unsupported documents with actionable copy.
- Support PDFs with an extractable text layer.
- Preserve stable page/block segment identity through normalization.
- Show the filename and page count without exposing the private URI.

### PII definitions

The first built-in set is:

- full name;
- email address;
- telephone number;
- postal address;
- Italian tax code;
- IBAN.

Each definition has a stable identifier, localized label and concise semantic definition. The user may add a custom name, definition and optional example for the current analysis. Custom definitions do not mutate Harness capabilities or inference presets.

### Analysis

- Use the explicit `document-pii-detection` use case.
- Discover output and input limits before submitting work.
- Accept the default logical model and deterministic host preset.
- Disable surfaced reasoning.
- Split oversized content into deterministic sequential chunks.
- Request one fixed result schema and merge validated findings.
- Support cancellation during extraction and inference.

### Review and export

- Present every accepted candidate as a typed placeholder such as `[EMAIL_1]`.
- Let the user reveal the original value only in the review surface.
- Let the user accept or ignore candidates before export.
- Warn when one or more chunks failed or results are incomplete.
- Export a newly rendered, flattened PDF containing only accepted substitutions.
- Use a deterministic default filename ending in `_anonimizzato.pdf` while allowing the system destination flow to rename it.

## UX principles

- One task, one primary next action; no bottom navigation or generic dashboard.
- No model picker, sampler controls, chain-of-thought view or host diagnostics in the primary workflow.
- Analysis results are “candidates” until source validation and user review.
- Status uses text and iconography in addition to color.
- Sensitive values are hidden by default and never copied automatically.
- Process recreation clears document content and asks the user to import again.

## Privacy and safety claims

OMBRA may state that the configured workflow is local and that normal app telemetry excludes document content only after two-APK tests confirm the exact build. It must not claim legal compliance, complete PII detection or guaranteed anonymization.

False negatives are the highest product risk. Human review is mandatory and the exported document must be described as user-reviewed, not automatically certified. The app must not expose model-generated confidence as a calibrated probability.

## Non-goals for the first slice

- OCR, handwriting or image-only/scanned documents;
- preserving arbitrary original PDF typography, forms, annotations or layout;
- modifying the original PDF in place;
- background analysis after the app disconnects;
- batch processing multiple documents;
- cloud fallback, remote analytics or server-side document storage;
- organization-managed PII catalogs or cross-device synchronization;
- automatic legal/compliance certification;
- arbitrary user-provided system prompts or JSON schemas;
- a PII-specific method in the public Harness SDK or AIDL protocol.

## Failure experience

The user receives distinct, recoverable states for:

- Harness not installed, denied, incompatible or disconnected;
- authorized model unavailable;
- PDF inaccessible, encrypted, malformed or without extractable text;
- no PII definition selected or invalid custom definition;
- input beyond current capability after chunking overhead;
- extraction, inference, result-validation or export failure;
- cancellation and partial analysis.

Errors never show private paths, prompt text, document excerpts, certificate information or backend exception strings.

## Product-level success

The target is met when a physical Android device can run this same-signer flow:

```text
pick text PDF
  -> select built-in + custom PII
  -> discover/prepare document-pii-detection
  -> analyze all bounded chunks
  -> review validated candidates
  -> export a new PDF
  -> verify accepted source values are absent from exported text/content
```

Success also requires cancellation, host death, invalid model output, unsupported PDF and export failure to converge to deterministic UI states without content leakage or orphaned files.
