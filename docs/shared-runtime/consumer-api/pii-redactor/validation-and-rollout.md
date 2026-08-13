# OMBRA validation and rollout

Status: active
Document type: feature-specification
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.validation-rollout
Read when: adding OMBRA tests, quality fixtures, privacy review, two-APK evidence or release gates
Last reviewed: 2026-08-13

## Goal

Prove that OMBRA is a pure Consumer API application, handles hostile/invalid documents and model output safely, produces a genuinely new redacted PDF and presents the complete workflow accessibly. Validation must distinguish repository correctness, model-quality evidence and physical-device evidence.

The generic Consumer API compatibility/security gates remain owned by [`../validation-and-rollout.md`](../validation-and-rollout.md). This source adds application-specific coverage without weakening them.

## Validation layers

```text
pure domain and reducer tests
 -> PDF extraction/export fixture tests
 -> prompt/schema/result contract tests with fake clients
 -> packaged Consumer API integration tests
 -> Compose semantics + screenshot/adaptive tests
 -> synthetic PII quality corpus
 -> physical two-APK document workflow
 -> privacy, dependency, API and release review
```

No layer may store real user documents, prompts, findings or exports as repository fixtures or normal evidence.

## Domain tests

### Definitions

- built-in identifiers and versioned definitions are stable and unique;
- selection requires at least one valid definition;
- custom name/definition/example/count limits fail deterministically;
- custom IDs are normalized, bounded and collision-safe;
- control characters and duplicate IDs are rejected;
- custom definitions remain task-scoped and are cleared on reset/process recreation.

### Normalization and segments

- page/block order and IDs are deterministic;
- line ending and control-character normalization preserves source mapping;
- Unicode, combining characters and right-to-left text do not corrupt boundaries;
- empty/image-only/encrypted/unsupported documents fail typed;
- cancellation closes resources and emits one terminal state.

### Chunk planner

- stable prompt/schema overhead is included in every budget calculation;
- every serialized request remains below the advertised consumer limit;
- pages/blocks preserve order and are split only when required;
- no Unicode code point is split;
- one oversize fragment fails rather than truncating;
- completed/failed/pending chunk state merges deterministically;
- sequential execution never starts a second active generation.

### Result and redaction

- unsupported schema versions and malformed JSON fail;
- unselected `typeId`, unknown segment and nonexistent surface are discarded/failed according to policy;
- exact repeated occurrences map deterministically;
- duplicates, exact overlaps and partial overlaps follow the defined merge rules;
- placeholder numbering is stable across runs;
- accept/ignore/reveal transitions preserve the underlying source mapping correctly;
- replacement order cannot shift later ranges;
- hidden content never appears in presentation semantics.

## PDF fixture matrix

Use small repository-safe synthetic documents generated from declared fixture text:

| Fixture | Expected |
| --- | --- |
| one-page plain Italian text | stable extraction and export |
| multi-page paragraphs | page/block order preserved |
| repeated email/name | distinct source occurrences |
| accented/Unicode text | exact surface and glyph preservation |
| multi-column or ambiguous order | deterministic supported result or explicit rejection |
| image-only scan | OCR-not-supported outcome |
| password-protected PDF | encrypted-document outcome |
| malformed/truncated file | parser failure with cleanup |
| oversized page/document | bounded failure or safe chunking |
| annotations/forms/attachments | ignored or explicitly unsupported; never copied into output |

Fixtures contain invented values and a generator/readme identifying them as synthetic. Third-party sample PDFs require license/provenance review before commit.

## Export verification

For each success fixture:

1. write to a controlled destination;
2. reopen the generated PDF with an independent/test extraction path where practical;
3. assert accepted source surfaces are absent;
4. assert deterministic placeholders are present;
5. assert ignored surfaces remain;
6. assert source PDF bytes, annotations and attachments were not copied;
7. assert page count and text order match the v1 normalized-render contract;
8. verify descriptor/writer cleanup after success and injected failure.

Visual PDF renders should be captured for representative wrapping, page breaks, placeholders, Unicode and large text. Pixel comparison must tolerate renderer differences only through a reviewed strategy; functional text assertions remain mandatory.

## Prompt and structured-output tests

Freeze versioned snapshots for:

- stable instruction text;
- built-in and custom definition serialization;
- segment framing and escaping;
- fixed JSON schema;
- maximum-size boundary requests.

Test documents include prompt-injection phrases such as instructions to change output shape, reveal secrets, choose another model or ignore definitions. Expected behavior is still schema-valid selected-category findings only. This is defense in depth: consumer validation must reject invalid results even when the fake/model violates instructions.

Fake Consumer API scenarios cover:

- host ready with default deterministic selection;
- `JSON_SCHEMA` or input capability unavailable;
- model unavailable and stale capability revision;
- ordered answer deltas and valid completion;
- malformed, extra-field, unselected-type and invented-surface results;
- cancellation during every chunk;
- disconnect and host death without automatic replay;
- late callback after cancellation/new operation;
- one failed chunk among successful chunks.

Do not expose raw JSON or prompt content in normal application errors or test evidence logs.

## Consumer purity and packaging

Enforce that `apps/local-llm-console` after migration:

- depends on the packaged public client/contracts and `ui/design-system`;
- contains no model-store, runtime-core, llama.cpp/JNI, health engine or Harness telemetry database dependency;
- contains no generated AIDL usage in application code;
- contains no model download/import/remove or raw sampler UI;
- packages no GGUF/GGML, source PDF or generated quality fixture outside test artifacts;
- retains required consumer R8/ProGuard rules;
- preserves exact debug/release host permission and same-signer policy.

A repository guard should fail when forbidden modules or model/document artifacts enter the application package.

## Compose and accessibility tests

Cover the screen/state matrix in [`ux-and-brand.md`](ux-and-brand.md) with:

- reducer and ViewModel/effect tests;
- Compose semantics for happy, loading, unavailable, failure, cancellation and partial states;
- navigation and Back confirmation behavior;
- exactly-once picker, inference and export effects;
- 48 dp targets, labels, focus order and live-region announcements;
- hidden PII absent from semantics and revealed PII present only while requested;
- compact portrait, landscape/expanded and 200% font layouts;
- light and accepted dark-theme screenshots;
- reduced-motion behavior and color-independent statuses.

Screenshot fixtures use synthetic document data. The generated mockup board is a design reference, not a golden screenshot; code-owned screenshots become the regression baseline.

## Model-quality corpus

Structured output can be syntactically valid and semantically wrong. Maintain a separate, synthetic and versioned evaluation corpus with:

- positive/negative examples for every built-in category;
- repeated and overlapping values;
- Italian punctuation, diacritics and common document phrasing;
- near-misses such as organization names, generic addresses and invalid tax/IBAN-like strings;
- selected custom definitions;
- injection-like document text;
- chunks with no PII.

Record per category and aggregate:

- exact-occurrence true positives, false positives and false negatives;
- precision, recall and F1;
- invalid finding/result rate;
- structured-output completion rate;
- latency/token metrics separately from quality.

Thresholds must be accepted from measured Qwen3.5 0.8B/2B results before a supported-model claim. Do not choose thresholds after looking only at the passing subset. A fallback regex/rule engine is not introduced silently; adding one changes product behavior and requires a focused design decision.

The UI never exposes these evaluation scores as per-finding confidence.

## Privacy and security review

Review at least:

- Storage Access Framework access is least-privilege and no broad storage permission is added;
- URI, filename, extracted text, definitions, prompt, schema, findings, reveal map and output content are absent from normal logs/telemetry/crash breadcrumbs;
- sensitive state is absent from saved state, clipboard and accessibility semantics while hidden;
- scratch and partial output cleanup is deterministic;
- malformed/oversized PDFs cannot cause unbounded memory, page, recursion or processing work under application policy;
- parser/export dependencies have reviewed licenses, maintenance posture and known-vulnerability handling;
- document text cannot select a model, use case, schema, destination or runtime option;
- another Binder caller cannot access OMBRA sessions/results;
- exported content contains no recoverable accepted source text layer or source attachment;
- backup/exported-component configuration does not expose private working state.

An independent security/privacy review is required before language implying privacy protection is used in a distributed build.

## Physical two-APK scenarios

On representative physical Android hardware and exact supported Qwen3.5 artifacts, capture privacy-safe evidence for:

1. host absent and subsequent recovery;
2. import of a small text PDF and capability discovery;
3. built-in definitions with valid structured result;
4. one custom definition;
5. multi-chunk sequential analysis;
6. cancellation during extraction and during inference;
7. host process death/disconnect and explicit restart;
8. malformed model output through a controlled fixture path where applicable;
9. hidden/revealed review semantics;
10. successful export and independent absence/presence assertions;
11. destination/write failure cleanup;
12. process recreation clearing sensitive task state;
13. client-observed wall time versus host metrics without content capture.

Evidence stores fixture IDs, counts, versions, hashes where safe and typed outcomes. It excludes screenshots showing readable source PII unless the entire document is synthetic and the bundle is explicitly classified as test evidence.

## Rollout gates

### Experimental/internal

- parser/export spike and licenses accepted;
- fake-driven end-to-end application flow passes;
- packaged Consumer API prerequisites are implemented;
- no production/privacy/compliance claim;
- known unsupported PDF classes are explicit.

### Same-publisher reference release

- Consumer API CA-0 through CA-6 applicable gates complete;
- pure-consumer dependency and packaged-AAR checks pass;
- quality corpus meets accepted model/category thresholds;
- accessibility/adaptive/screenshot matrix passes;
- security/privacy and public-copy review complete;
- physical two-APK document evidence complete;
- launcher/vector assets, signing, versioning and shrinker packaging verified.

The OMB-8 release portion and Consumer API CA-7 close together after these shared prerequisites pass; neither is a prerequisite for the other.

### Broader distribution

Arbitrary third-party access, OCR, compliance certification, organization-managed definitions or guaranteed anonymization require separate product, trust, threat-model and evidence decisions. They are not incremental UI rollout flags.

## Completion criteria

OMBRA is ready only when the exact packaged consumer proves import, bounded analysis, structured validation, human review and irreversible new-PDF export on physical hardware; automated tests cover all sensitive failure/cleanup paths; measured quality supports the stated built-in categories; and user-facing privacy language matches the evidence without implying complete detection or legal compliance.
