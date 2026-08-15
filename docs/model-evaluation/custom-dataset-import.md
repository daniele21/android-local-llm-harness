# Custom evaluation dataset import

Status: active
Document type: user-and-developer-guide
Owner: model-evaluation
Canonical scope: model-evaluation.datasets.custom-import
Read when: authoring or importing a custom evaluation dataset
Last reviewed: 2026-08-15

## Purpose

The custom import surface accepts a local document containing the canonical Evaluation Dataset **case JSONL v1** representation. It is deliberately narrower than a full pack import: the user supplies dataset metadata separately, while the Harness parses the cases, derives the immutable content digest and category manifest, validates the complete pack and publishes it into app-private dataset storage.

CSV, Excel, arbitrary JSON arrays, executable import plugins and network-backed dataset URLs are not supported by v1.

The authoritative wire schema remains [`dataset-schema-v1.md`](dataset-schema-v1.md). This guide documents the Android custom-import behavior, operational limits, privacy boundary and failure semantics implemented by EVAL-D-10 through EVAL-D-12.

## Input contract

The selected document is a UTF-8 JSONL file with exactly one `EvaluationDatasetCaseV1` object per line.

V1 document rules are strict:

- UTF-8 only;
- no UTF-8 BOM;
- `LF` line endings only; `CR`/CRLF are rejected;
- every record is non-empty;
- every record ends with `LF`, including the final record;
- one complete JSON object per line;
- unknown fields, duplicate JSON keys, malformed UTF-8/JSON and unsupported schema versions fail closed.

A minimal valid record shape is:

```json
{"schemaVersion":1,"id":"case-001","categoryId":"general","messages":[{"role":"USER","content":"Return the capital of France."}],"expected":{"kind":"TEXT","value":"Paris"},"evaluator":{"type":"EXACT_MATCH","version":1,"parameters":{"case":"sensitive","whitespace":"exact"}}}
```

The supported top-level case fields are:

| Field | Required | Meaning |
| --- | --- | --- |
| `schemaVersion` | yes | Must be integer `1`. |
| `id` | yes | Stable case identifier. |
| `categoryId` | yes | Category identifier used for sampling/aggregation. |
| `messages` | yes | Ordered message array with at least one `USER` message. |
| `expected` | yes | Deterministic expected-answer object. |
| `evaluator` | yes | Versioned deterministic evaluator spec. |
| `output` | no | Response format, output-token cap and stop-sequence contract. |
| `metadata` | no | Bounded string-to-string evaluation metadata. |

Allowed message roles are `SYSTEM`, `USER` and `ASSISTANT`. Allowed expected-answer kinds are `TEXT`, `NUMBER`, `LABEL` and `JSON`. Allowed evaluator types and their exact parameter semantics are frozen in [`evaluator-semantics-v1.md`](evaluator-semantics-v1.md).

## Import metadata and generated manifest

The Android import action supplies these values separately from JSONL content:

- dataset ID;
- dataset version;
- display name;
- optional description.

For a successfully parsed document, the importer deterministically creates a v1 manifest with:

- `origin = USER_IMPORTED`;
- `caseCount` equal to the parsed record count;
- `contentDigest` calculated from the canonical ordered case representation;
- one category entry per distinct case `categoryId`, sorted by category ID;
- category display names equal to their category IDs for v1 custom import;
- no generated presets.

The importer then re-encodes the parsed cases canonically and delegates validation, digest verification, staged installation and atomic publication to the same dataset installer used by ordinary packs. Custom import therefore has no alternate validation or storage path.

## Operational limits

The default parser limits are part of the v1 operational boundary:

| Limit | Value |
| --- | ---: |
| Maximum cases per imported document | 10,000 |
| Maximum bytes in one JSONL line | 1,048,576 bytes (1 MiB) |
| Message content | 131,072 characters per message |
| Expected-answer value | 65,536 characters |
| Stop sequences | 16 per case |
| Stop-sequence length | 256 characters |
| Metadata entries | 32 per case |
| Metadata key | 64 characters |
| Metadata value | 1,024 characters |
| Evaluator parameters | 32 per evaluator spec |
| Evaluator parameter key | 64 characters |
| Evaluator parameter value | 512 characters |

Dataset/display metadata is bounded by the shared contracts: dataset/category display text is at most 160 characters and dataset description at most 2,048 characters. Stable IDs are independently validated by their value-semantic identifier contracts.

The 1 MiB line limit is a byte limit before UTF-8 decoding; the field limits above are contract-level character/count limits after decoding.

## Failure semantics

Import failures are typed. The UI/caller should surface the high-level rejection and, when present, its nested parse/install detail rather than replacing it with a generic success/failure boolean.

### Document/import rejection

| Code | Meaning | Useful detail |
| --- | --- | --- |
| `DOCUMENT_UNAVAILABLE` | The selected document cannot be opened/read, or an unexpected source-read exception occurs. | No case content is published. |
| `EMPTY_DATASET` | The document contains zero cases. | No manifest is created. |
| `INVALID_METADATA` | Dataset ID/version/display metadata violates the shared manifest contracts. | Correct metadata and retry. |
| `PARSE_FAILURE` | A JSONL record violates parser/wire rules. | `parseLineNumber` and `parseCode` identify the exact failure. |
| `INSTALL_REJECTED` | Parsing succeeded but validation/digest/publication failed. | `installCode` and validation issues are preserved. |

### Parse rejection detail

`PARSE_FAILURE` can report:

- `UTF8_BOM`;
- `CR_LINE_ENDING`;
- `MISSING_LF_TERMINATOR`;
- `EMPTY_LINE`;
- `LINE_TOO_LONG`;
- `TOO_MANY_CASES`;
- `MALFORMED_UTF8`;
- `MALFORMED_JSON`;
- `UNKNOWN_FIELD`;
- `MISSING_FIELD`;
- `INVALID_FIELD`;
- `UNSUPPORTED_SCHEMA`.

Parse line numbers are one-based and point at the rejected record.

### Installation rejection detail

`INSTALL_REJECTED` can report:

- `ALREADY_INSTALLED` for an existing dataset ID/version target;
- `PARSE_FAILURE` if canonical installer parsing fails;
- `VALIDATION_FAILURE` for cross-record/manifest/evaluator validation problems;
- `DIGEST_MISMATCH` when canonical cases do not match the manifest digest;
- `ATOMIC_PUBLICATION_UNAVAILABLE` when the filesystem cannot perform the required atomic publish;
- `IO_FAILURE` for other staging/publication I/O failures.

A rejected install never becomes visible through the active dataset registry. Staging data is cleaned on publication failure.

## Privacy and trust boundary

Custom dataset documents are **untrusted evaluation data**. They may contain personal, proprietary or otherwise sensitive prompts/expected answers, so the Harness applies the following boundary:

- the Android document URI is read locally through `ContentResolver`; import does not fetch network resources;
- imported cases are stored only in the app-private dataset area;
- messages, expected answers, evaluator parameters and case metadata are not copied into ordinary generation telemetry;
- evaluation result persistence stores only case identity, typed outcome/failure and privacy-safe runtime/resource metrics;
- dataset content is excluded from normal diagnostic export;
- JSONL cannot name executable classes, scripts, arbitrary templates or network hooks;
- malformed/unknown schema content fails closed rather than being coerced;
- the original external document is not required after successful canonical installation.

## Deletion semantics

Deletion is explicit. The registry resolves the exact installed dataset ID/version and the deleter refuses to remove a pack when an active evaluation run references the exact dataset identity, including digest.

Only registry-resolved canonical paths owned by the app-private dataset root may be deleted. A path that resolves outside that root fails closed with `IO_FAILURE`. Successful deletion removes the published pack and cleans an empty encoded dataset parent directory; it does not delete historical evaluation results or generation telemetry.

## Reproducibility checklist

Before relying on a custom pack for model comparison:

1. Freeze dataset ID and version for the intended content.
2. Keep case IDs stable and unique.
3. Use only frozen evaluator versions and documented parameters.
4. Preserve message/stop-sequence order exactly.
5. Re-import changed content under a new dataset version rather than overwriting an installed version.
6. Record the resulting dataset digest and sampled `SampleSetDigest` with benchmark evidence.
7. Compare quality only when the Harness compatibility service says the relevant identities are compatible.

Changing JSON formatting without changing case meaning is normalized by canonical re-encoding, but changing any semantic case field changes the canonical dataset content digest.
