# Evaluation dataset wire schema v1

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.datasets.schema-v1
Read when: authoring, parsing, validating or importing evaluation dataset packs
Last reviewed: 2026-08-15

This document freezes the wire representation for `EvaluationDatasetManifestV1` and `EvaluationDatasetCaseV1`. Kotlin value semantics remain owned by `evaluation/contracts`; this file defines how those contracts are represented in `manifest.json` and `cases.jsonl`.

## Pack layout

```text
<datasetId>/<version>/
  manifest.json
  cases.jsonl
  optional source-license metadata
```

`manifest.json` contains exactly one manifest object. `cases.jsonl` contains one complete case object per non-empty line.

V1 files use UTF-8 without a BOM and `LF` line endings. Blank JSONL records are invalid. Dataset content is untrusted data: it cannot name executable classes, scripts, templates or network resources.

## Manifest schema

Canonical field names and value kinds are:

| Field | Required | V1 representation |
| --- | --- | --- |
| `schemaVersion` | yes | integer `1` |
| `caseSchemaVersion` | yes | integer `1` |
| `datasetId` | yes | stable string mapped to `EvaluationDatasetId` |
| `version` | yes | stable string mapped to `EvaluationDatasetVersion` |
| `displayName` | yes | bounded string |
| `description` | no | bounded string; omitted when absent |
| `origin` | yes | `BUILT_IN` or `USER_IMPORTED` |
| `caseCount` | yes | positive integer |
| `contentDigest` | yes | lowercase 64-character SHA-256 string |
| `categories` | yes | non-empty array of category definitions |
| `presets` | no | ordered array; omitted when empty |

A category definition is:

```json
{"id":"reasoning","displayName":"Reasoning","weight":0.3}
```

`weight` is optional, finite and strictly positive. Category IDs are unique inside one manifest.

A preset definition is:

```json
{"id":"standard-100","orderedCaseIds":["case-001","case-002"]}
```

Preset case IDs are non-empty, unique inside the preset and order-sensitive. Preset membership must refer to cases in the same pack; cross-record validation is owned by EVAL-D-03.

Example manifest:

```json
{
  "schemaVersion": 1,
  "caseSchemaVersion": 1,
  "datasetId": "fixture-pack",
  "version": "1.0.0",
  "displayName": "Fixture Pack",
  "origin": "BUILT_IN",
  "caseCount": 1,
  "contentDigest": "1111111111111111111111111111111111111111111111111111111111111111",
  "categories": [
    {"id": "reasoning", "displayName": "Reasoning", "weight": 1.0}
  ]
}
```

## Case JSONL schema

Each JSONL line maps to one `EvaluationDatasetCaseV1` and uses:

| Field | Required | V1 representation |
| --- | --- | --- |
| `schemaVersion` | yes | integer `1` |
| `id` | yes | stable case ID string |
| `categoryId` | yes | category ID string |
| `messages` | yes | non-empty ordered array of messages containing at least one `USER` role |
| `expected` | yes | expected-answer object |
| `evaluator` | yes | evaluator spec object |
| `output` | no | case output contract; omitted means contract defaults |
| `metadata` | no | bounded string-to-string object; omitted means empty |

A message object is:

```json
{"role":"USER","content":"Which option is correct?"}
```

Allowed roles are `SYSTEM`, `USER` and `ASSISTANT`. Message order is semantic and must be preserved.

An expected-answer object is:

```json
{"kind":"LABEL","value":"B"}
```

Allowed kinds are `TEXT`, `NUMBER`, `LABEL` and `JSON`. Expected-answer content is evaluation data and must not be copied into ordinary telemetry.

An evaluator spec is:

```json
{
  "type": "MULTIPLE_CHOICE",
  "version": 1,
  "parameters": {"labels":"A,B,C,D","case":"sensitive"}
}
```

Allowed evaluator types and parameter semantics are versioned by the evaluator compatibility freeze in `target.md`. Unknown evaluator types, versions or parameter keys fail closed.

An output contract is:

```json
{
  "responseFormat": "TEXT",
  "maxOutputTokens": 64,
  "stopSequences": []
}
```

Allowed response formats are `TEXT` and `JSON`. `maxOutputTokens` is optional and positive when present. Stop-sequence order is preserved and duplicate values are invalid.

Example JSONL record:

```json
{"schemaVersion":1,"id":"case-001","categoryId":"reasoning","messages":[{"role":"USER","content":"Which option is correct?"}],"expected":{"kind":"LABEL","value":"B"},"evaluator":{"type":"MULTIPLE_CHOICE","version":1,"parameters":{"labels":"A,B,C,D","case":"sensitive"}},"output":{"responseFormat":"TEXT","maxOutputTokens":64,"stopSequences":[]},"metadata":{"sourceId":"fixture-001"}}
```

## Parsing and canonical writing rules

Parsers must not rely on JSON object field order. Array order is semantic for messages, stop sequences and preset case IDs.

V1 readers reject:

- unsupported `schemaVersion` or `caseSchemaVersion`;
- duplicate JSON object keys;
- unknown top-level or nested schema fields;
- wrong scalar/container types;
- values outside the bounds enforced by `DatasetSchemaContracts.kt`;
- non-object JSONL records or trailing non-whitespace content;
- blank JSONL lines.

The canonical writer emits fields in the order shown by the tables above, emits enum values using their uppercase wire names, uses JSON escaping, and omits optional fields only when they equal the documented absent/default state. EVAL-D-04 owns the canonical ordered content digest and must not redefine the schema.

## Compatibility rule

Any wire-level behavior change that can alter accepted content, default interpretation or serialized meaning requires a new manifest/case schema version. A dataset pack identifies both schema versions explicitly; readers never silently coerce unknown versions.

## Privacy boundary

`messages`, `expected`, evaluator parameters and metadata remain inside app-private dataset storage. The ordinary telemetry pipeline must never persist them. Evaluation result persistence stores case identity, typed outcome and privacy-safe metrics only.
