# OMB-0 decisions and technical spikes

Status: active
Document type: feature-specification
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.omb0
Read when: implementing OMBRA before OMB-0 is complete or reviewing parser/export/use-case decisions
Last reviewed: 2026-08-14

This record owns the decisions and evidence needed to close OMB-0. It does not replace the product target, architecture or detection/redaction specifications.

## Integrated prerequisite

CA-4 is integrated in `dev` through PR #104. The public Consumer API has the Binder v1.1 `consumer-api-v1` boundary, packaged client fixture, authenticated caller mapping, structured-output transport and deterministic compatibility/privacy coverage required by OMBRA.

Physical two-APK evidence remains CA-6/OMB-8 work and is not implied by this record.

## Accepted product decisions

The following decisions are frozen for OMBRA v1:

- user-facing product name is **OMBRA** while Local LLM Harness remains the host/runtime identity;
- `apps/local-llm-console` keeps its current package/signing boundary during the migration;
- v1 supports one text-bearing PDF selected through the system document picker;
- OCR, scanned/image-only documents and arbitrary original-layout preservation are out of scope;
- export creates a new normalized, flattened PDF instead of modifying the source or overlaying recoverable content;
- human review is mandatory before export;
- source PDF bytes/URI and export destination never cross the Harness Binder boundary;
- normal telemetry never persists extracted text, definitions, findings, prompts, schema payloads or revealed values.

## PII and structured-output contract

The v1 built-in identifiers are frozen:

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

Initial capability contract:

- `STATELESS` sessions;
- fixed `JSON_SCHEMA` output;
- reasoning disabled/not surfaced;
- host-selected default logical model and deterministic preset;
- no consumer model ID, artifact ID, sampler, seed, raw token budget or AIDL access.

The OMB-0 evidence does not expose a missing Consumer API or Binder primitive. OMB-4 must first implement the complete flow with the integrated CA-4 public surface. Any later protocol change requires a concrete failing consumer case.

## Parser decision

### Final selected direction

Use **PdfBox-Android `2.0.27.0` inside a permissionless Android isolated process**, not in the OMBRA application process.

The OMB-0A AndroidX PDF spike was useful to establish the trust-boundary requirements, but the final bounded runtime evidence moved the parser behind `OmbraPdfIsolatedParserSpikeService` and restored the repository-wide OMBRA/Console `minSdk` instead of requiring the AndroidX PDF API-28/SDK-extension-19 product floor.

The selected shape is:

```text
OMBRA app process
    -> opens user URI read-only
    -> passes only ParcelFileDescriptor capability
    -> isolatedProcess parser service
       -> PdfBox-Android
       -> page-by-page bounded extraction
       -> UTF-8 framed output pipe
    -> OMBRA receives page index + extracted text only
```

Security and ownership properties proven by the spike:

- the parser runs under a UID different from the OMBRA application UID;
- the isolated service owns no application permissions;
- OMBRA opens the source and transfers only a read-only file descriptor;
- document-sized extracted content flows through a pipe rather than Binder transaction payloads;
- Messenger carries only completion/error metadata;
- page count and returned character counts are bounded;
- page frames validate magic, page indices and byte bounds before materialization;
- descriptors, document objects and service bindings are closed on success, failure and cancellation paths;
- cancellation leaves the parser reusable for a subsequent request.

PdfBox-Android remains an implementation dependency of the OMBRA consumer only. It does not enter Harness runtime/public contracts.

## Runtime extraction evidence

GitHub Actions workflow **OMBRA PDF runtime spike**, exact PR #106 head gate, completed successfully on an API-35 emulator.

The focused runtime suite executed **6 instrumentation tests** and passed.

Evidence covers:

- two-page page-ordered extraction;
- visual reading order for a fragmented/two-column fixture;
- page-count and character truncation bounds;
- image-only input producing no plaintext rather than fabricated text;
- malformed input failing closed;
- password-protected input failing closed;
- cooperative cancellation and parser reuse;
- permissionless isolated-process execution with a distinct parser UID;
- normalized PDF export and parser round-trip;
- representative European Unicode round-trip and unsupported-glyph rejection.

The final OMB-2 extractor still owns production typed error mapping, stable block/segment mapping, final byte/resource policy and broader fixture coverage. Those are implementation requirements, not unresolved architecture choices.

## APK-size evidence

The automated size gate measured the PDF-enabled OMBRA/Console debug APK against the pre-PDF CA-4 baseline:

```text
baseline_apk_bytes = 1,300,694
current_apk_bytes  = 10,918,600
delta_bytes        = 9,617,906
delta_percent      = 739.44%
```

The increase is accepted for the OMBRA reference-consumer path because:

- the PDF parser is app-specific and does not inflate the Harness host or reusable client/runtime AARs;
- parser isolation is a stronger requirement than minimizing this reference APK at OMB-0;
- v1 deliberately trades application size for local-only document processing and a narrow parser trust boundary.

Release/minification optimization may reduce the distributed delta later, but OMB-0 does not depend on such an assumption.

## Export and font/glyph decision

Use framework `android.graphics.pdf.PdfDocument` for normalized, flattened export. It creates new pages and never copies source-PDF objects/bytes into the output.

The writer contract is:

- deterministic page geometry and wrapping;
- bounded page/character counts;
- resources closed on every path;
- accepted source values are absent from the newly generated output;
- placeholders survive independent parser round-trip;
- unsupported glyphs fail closed instead of silently substituting unknown characters.

For v1, the accepted font policy is **Android system sans-serif plus explicit glyph preflight/fail-closed behavior**. The runtime fixture round-tripped representative Italian/European text including accented Italian, em dash, German/Spanish/Polish characters, ordinal symbol and euro sign. A deliberately unsupported Unicode code point was rejected.

A bundled font is therefore not required by OMB-0. OMB-5/OMB-8 may revisit font packaging only if the supported-language corpus demonstrates a concrete glyph gap.

## OMB-0 evidence ledger

| Gate | Result |
| --- | --- |
| Repository/build/package validation | PASS |
| Parser trust-boundary decision | ACCEPTED — PdfBox-Android in isolated process |
| OMBRA minimum SDK | Repository consumer floor retained; AndroidX API-28 raise removed |
| Runtime representative fixtures | PASS |
| Malformed/encrypted/image-only behavior | PASS / fail-closed as specified |
| Cancellation and cleanup | PASS |
| Writer placeholder/source-value round-trip | PASS |
| Representative European glyph round-trip | PASS |
| Unsupported glyph behavior | PASS — fail closed |
| APK-size delta | MEASURED — +9,617,906 bytes (+739.44%) |
| Consumer API/wire change required | NO |

## Exit rule

The OMB-0 architectural uncertainty is closed: product, parser trust boundary, export strategy, schema, use case, font/glyph policy and Consumer API boundary are reviewable and internally consistent.

The milestone remains `IN PROGRESS` on this feature branch until PR #106 is integrated into `dev`. After integration, the next OMBRA branch must mark OMB-0 `DONE` before OMB-1 can be treated as independently mergeable.
