# OMB-0 decisions and technical spikes

Status: active
Document type: feature-specification
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.omb0
Read when: implementing OMBRA before OMB-0 is complete or reviewing parser/export/use-case decisions
Last reviewed: 2026-08-14

This record owns the decisions and evidence needed to close OMB-0. It does not replace the product target, architecture or detection/redaction specifications.

## Integrated prerequisite

CA-4 is integrated in `dev` through PR #104. The public Consumer API now has the Binder v1.1 `consumer-api-v1` boundary, packaged client fixture, authenticated caller mapping, structured-output transport and deterministic compatibility/privacy coverage required to begin the OMBRA consumer implementation.

Physical two-APK evidence remains CA-6/OMB-8 work and is not implied by this record.

## Accepted product decisions

The following decisions were already specified by the OMBRA target and are now treated as OMB-0 inputs rather than open alternatives:

- user-facing product name is **OMBRA** while Local LLM Harness remains the host/runtime identity;
- `apps/local-llm-console` keeps its current package/signing boundary during the migration;
- v1 supports one text-bearing PDF selected through the system document picker;
- OCR, scanned/image-only documents and arbitrary original-layout preservation are out of scope;
- export creates a new normalized, flattened PDF instead of modifying the source or overlaying recoverable content;
- human review is mandatory before export;
- source PDF bytes/URI and export destination never cross the Binder boundary;
- normal telemetry never persists extracted text, definitions, findings, prompts, schema payloads or revealed values.

## PII and structured-output contract

The v1 built-in identifiers remain frozen for the first implementation slice:

```text
full-name
email
telephone
postal-address
italian-tax-code
iban
```

The fixed schema remains the v1 schema defined in [`detection-and-redaction.md`](detection-and-redaction.md): top-level `schemaVersion = 1`, bounded `findings`, and per-finding `typeId`, exact `surface` and stable `segmentId`. OMBRA does not request model offsets or model-generated confidence.

Placeholder numbering remains deterministic per accepted occurrence order, for example `[EMAIL_1]`, `[EMAIL_2]`. Custom placeholder keys are sanitized, bounded and collision-safe.

## Harness use-case decision

OMBRA uses exactly one host-authorized use case:

```text
document-pii-detection
```

Initial capability direction:

- `STATELESS` sessions;
- fixed `JSON_SCHEMA` output;
- reasoning disabled/not surfaced;
- host-selected default logical model and deterministic preset;
- no consumer model ID, artifact ID, sampler, seed, raw token budget or AIDL access.

No new Consumer API or Binder primitive is currently required. OMB-4 should first attempt the complete flow using the integrated CA-4 public surface. Any future transport gap must be demonstrated by a failing OMBRA use case before changing the shared protocol.

## Parser spike — selected direction

### Candidate selected for bounded implementation spike

Use AndroidX PDF `1.0.0-alpha19` through:

```text
androidx.pdf:pdf-core
androidx.pdf:pdf-document-service
SandboxedPdfLoader
```

Rationale:

- AndroidX exposes page content including ordered text objects and bounds;
- `SandboxedPdfLoader` processes PDFs through a sandboxed Android service instead of parsing an untrusted PDF directly inside the OMBRA application process;
- the API owns/communicates descriptor cleanup explicitly and provides typed password/open failures;
- it avoids introducing a second third-party PDF parser stack into a privacy/security-sensitive application.

Primary references:

- https://developer.android.com/jetpack/androidx/releases/pdf
- https://developer.android.com/reference/kotlin/androidx/pdf/SandboxedPdfLoader
- https://developer.android.com/reference/kotlin/androidx/pdf/PdfDocument

### Minimum Android version

AndroidX PDF read/render support is backported to `minSdk = 28`. The repository-wide Harness floor remains API 26, but the OMBRA consumer APK is allowed to use API 28 as its own product floor.

This is an intentional consumer-only compatibility trade-off. It avoids using framework `PdfRenderer.Page.getTextContents()`, whose text-content API starts at API 35, and avoids taking the older PdfBox-Android dependency solely to preserve Android 8 support.

### Alternative considered: PdfBox-Android

`com.tom-roush:pdfbox-android:2.0.27.0` supports API 19+ and is Apache-2.0 licensed, but its public repository remains based on Apache PDFBox 2.0.27 and would execute a larger third-party parser surface in the app process unless OMBRA introduced an additional isolation boundary.

Reference: https://github.com/TomRoush/PdfBox-Android

It remains a fallback only if the AndroidX runtime/size/fidelity spike fails.

### Code spike now present

`OmbraPdfParserSpike`:

- opens the user document with `SandboxedPdfLoader`;
- exposes only page index + extracted text to the spike result;
- bounds page count and total extracted characters;
- closes the opened `PdfDocument` deterministically;
- deliberately does not become the final OMBRA domain API.

The final OMB-2 extractor must additionally prove stable block segmentation, cancellation, encrypted/malformed/image-only outcomes, byte/resource bounds and representative reading-order behavior.

## Export spike — selected direction

Use framework `android.graphics.pdf.PdfDocument` for normalized export. It is available from API 19 and creates a new PDF by drawing each new page onto a Canvas and writing the completed document to an `OutputStream`.

Primary reference: https://developer.android.com/reference/android/graphics/pdf/PdfDocument

`OmbraPdfWriterSpike` now proves the intended ownership shape:

- no source-PDF objects or bytes are copied into output;
- page geometry and line wrapping are deterministic inputs;
- input page/character counts are bounded;
- writer/page resources are closed on every path;
- unsupported glyphs fail closed instead of silently substituting unknown content.

The spike currently uses the Android system sans-serif typeface only to exercise the writer path. The final font policy is **not yet accepted** because exported glyph coverage must be verified across representative Italian/European text fixtures. A bundled reviewed font is preferred if system fallback cannot provide deterministic coverage without hidden substitution.

## Remaining OMB-0 evidence

OMB-0 is intentionally still `IN PROGRESS`. Before marking it `DONE`, the branch must record:

1. repository validation that the AndroidX PDF dependencies, API 28 app floor, parser spike and writer spike compile/package cleanly;
2. packaged APK size delta for the PDF artifacts;
3. runtime fixture evidence for representative text PDFs, multi-column/fragmented text, empty-text/image-only input, malformed input and encrypted input;
4. cancellation/resource-cleanup evidence for parser operations;
5. writer round-trip evidence that exported text contains expected placeholders and omits accepted source values;
6. font/glyph policy decision after representative Unicode fixtures;
7. final confirmation that no shared Consumer API/wire change is needed.

If one of these results changes the parser/export architecture, update this record before starting OMB-1/OMB-2 implementation that depends on it.

## Exit rule

OMB-0 can become `DONE` only when all remaining evidence above is reviewable and no unresolved parser, export, schema, use-case or brand decision could force a different package or trust boundary.
