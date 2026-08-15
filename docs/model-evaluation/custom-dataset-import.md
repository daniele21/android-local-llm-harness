# Custom evaluation dataset import

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.datasets.custom-import
Read when: preparing, importing, troubleshooting or deleting a user-provided evaluation dataset
Last reviewed: 2026-08-15

This document defines the supported v1 custom-dataset import boundary. The authoritative wire format remains [`dataset-schema-v1.md`](dataset-schema-v1.md); this document explains how an Android-selected canonical JSONL document is accepted, bounded, installed, rejected and later deleted.

## Supported input

V1 custom import accepts **canonical case JSONL only**. A selected document contains one complete `EvaluationDatasetCaseV1` object per line. The importer generates the local manifest from user-supplied dataset metadata and the parsed cases; users do not import a separate `manifest.json` through this surface.

The generated manifest is always `USER_IMPORTED`. Its case count, canonical content digest and category list are derived locally from the parsed cases. Categories are deduplicated and ordered deterministically by category ID. The importer then delegates to the same validator, digest and atomic installer used by installed dataset packs.

CSV, Excel, arbitrary JSON mappings, scripts, executable evaluators, class names, templates and network-backed dataset references are not supported in v1.

## Required import metadata

The import caller supplies:

- `datasetId` — stable local dataset identity;
- `version` — stable local version identity;
- `displayName` — bounded human-readable name;
- optional `description`.

Invalid metadata fails before publication with `INVALID_METADATA`. Import metadata does not override case IDs, evaluator declarations, categories or expected answers contained in the canonical JSONL records.

## Canonical JSONL requirements

The selected document follows [`dataset-schema-v1.md`](dataset-schema-v1.md). In particular:

- UTF-8 only, without a BOM;
- `LF` line endings only;
- every record must end with `LF`, including the last record;
- no blank records;
- exactly one JSON object per non-empty line;
- schema version `1` only;
- unknown fields, duplicate object keys, malformed JSON and unsupported value shapes fail closed;
- each case contains at least one message and at least one `USER` message;
- message order, stop-sequence order and evaluator semantics remain significant.

The import path does not silently repair CRLF, missing final newlines, malformed UTF-8, unknown fields or unsupported schema versions. Producers should emit canonical JSONL rather than depending on tolerant parsing.

## Hard limits

Default parser limits are deliberately bounded:

| Limit | V1 value |
| --- | ---: |
| Maximum cases per imported document | 10,000 |
| Maximum bytes per JSONL record | 1,048,576 |
| Maximum dataset display/category display text | 160 characters |
| Maximum dataset description | 2,048 characters |
| Maximum message content | 131,072 characters |
| Maximum expected-answer value | 65,536 characters |
| Maximum stop sequences per case | 16 |
| Maximum stop-sequence text | 256 characters |
| Maximum metadata entries per case | 32 |
| Maximum metadata key | 64 characters |
| Maximum metadata value | 1,024 characters |

Dataset/preset identifiers are bounded by the contract layer; evaluator parameter bounds remain owned by the versioned evaluator contracts. A line can therefore be rejected by either the byte-level parser limits or the stricter typed field contracts.

## Import result and failure semantics

Import is fail-closed and returns one typed top-level rejection code:

| Code | Meaning |
| --- | --- |
| `DOCUMENT_UNAVAILABLE` | The selected document cannot be opened/read, or document access raises an unexpected read failure. |
| `EMPTY_DATASET` | Parsing succeeds but produces zero cases. |
| `INVALID_METADATA` | The generated `USER_IMPORTED` manifest cannot be constructed from the supplied metadata/cases. |
| `PARSE_FAILURE` | JSONL parsing fails. The result may include the exact line number and `DatasetParseErrorCode`. |
| `INSTALL_REJECTED` | Parsing and local manifest construction succeed, but the shared installer rejects validation, digest/publication or storage. |

Parser detail codes are:

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

When the top-level result is `INSTALL_REJECTED`, the nested installer code can be `ALREADY_INSTALLED`, `PARSE_FAILURE`, `VALIDATION_FAILURE`, `DIGEST_MISMATCH`, `ATOMIC_PUBLICATION_UNAVAILABLE` or `IO_FAILURE`. Validation failures may also include typed validation issues.

No rejected import becomes discoverable as an installed dataset. Staging content is cleaned on publication failure; discovery exposes only fully published packs that can be strictly decoded and whose persisted identity matches their canonical content.

## Privacy and telemetry boundary

Custom dataset content is evaluation data, not ordinary telemetry. Imported messages, expected answers, evaluator parameters and metadata:

- remain in app-private dataset storage after successful installation;
- are used in memory by the evaluation path only as required to execute/score the selected cases;
- are not copied into ordinary generation telemetry metadata;
- are excluded from normal diagnostic export;
- are not persisted in evaluation result history, which keeps identities, typed outcomes and privacy-safe metrics instead;
- cannot trigger network fetches or execute user-provided code.

A custom dataset may contain sensitive material. Import therefore does not make the content safe to share; the boundary only prevents the evaluation subsystem from intentionally duplicating that content into its ordinary telemetry and result stores.

## Installation identity and repeatability

Successful import canonicalizes the parsed cases before installation. The generated manifest records:

- the caller-provided dataset ID/version/display metadata;
- origin `USER_IMPORTED`;
- derived case count;
- deterministic category declarations;
- the canonical ordered content digest.

Re-importing equivalent canonical cases with the same metadata yields the same content identity, but attempting to install the same dataset ID/version when its final directory already exists is rejected as `ALREADY_INSTALLED`. Replacing an installed version is therefore explicit: delete the installed pack first or import under a new version.

## Deletion behavior

Dataset deletion is explicit and registry-bound. The deleter resolves the exact installed dataset identity before touching storage and refuses deletion when an active evaluation run references that exact dataset identity, including its digest.

Deletion returns typed outcomes for `DELETED`, `NOT_FOUND`, `ACTIVE_RUN` and `IO_FAILURE`. Only registry-resolved paths inside the application-owned dataset root may be removed; path resolution that escapes that root fails closed.

Deleting a dataset pack does not delete evaluation-history rows or ordinary generation telemetry. Conversely, deleting evaluation history does not delete installed datasets.

## Author checklist

Before importing a custom dataset:

1. emit one canonical v1 case object per line using the frozen field names in [`dataset-schema-v1.md`](dataset-schema-v1.md);
2. use UTF-8, `LF`, no BOM and a final newline;
3. keep every record and field inside the documented bounds;
4. use only evaluator types/versions/parameters frozen in [`evaluator-semantics-v1.md`](evaluator-semantics-v1.md);
5. ensure case IDs are unique and category/evaluator relationships validate as a complete pack;
6. choose a stable dataset ID/version because that pair is the local installation address;
7. treat prompts, expected answers and metadata as potentially sensitive evaluation content.

## Compatibility rule

This document describes the v1 import surface only. A future alternative format may translate into the same canonical contract, but it must not weaken canonical validation, privacy, identity, evaluator or atomic-publication semantics. Any change that alters accepted v1 wire meaning requires a deliberate schema/compatibility change rather than silent coercion.
