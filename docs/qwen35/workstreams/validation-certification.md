# Qwen3.5 validation and certification

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.validation-certification
Read when: adding Qwen3.5 tests, defining physical-device evidence or deciding whether an artifact can be marked certified
Last reviewed: 2026-08-08

## Goal

Turn “the GGUF loads” into a reproducible certification claim for exact Qwen3.5 0.8B/2B artifacts, quantizations and backend revisions.

Compatibility and certification are intentionally different:

```text
compatible artifact
  -> semantic validation
  -> runtime/device validation
  -> evidence review
  -> certification status
```

## Independent decision axes

Catalog lifecycle, runtime compatibility and evidence status must not share one enum or overwrite one another.

| Axis | Values | Meaning |
| --- | --- | --- |
| Catalog availability | existing `ACTIVE`, `CANDIDATE`, `DEPRECATED`, `REVOKED`, `UNAVAILABLE` | Administrator distribution lifecycle only. |
| Compatibility decision | compatible or typed incompatible reason | Whether this artifact can run with the current backend/device policy. |
| Evidence status | `CERTIFIED`, `TESTED`, `EXPERIMENTAL`, `UNVERIFIED` | Strength of proof for one exact evidence identity. |

`UNSUPPORTED` is a compatibility outcome, not an evidence status. An evidence status attaches to an exact artifact/backend/profile/device envelope, not a display name such as “Qwen3.5 2B Q4”.

The catalog schema must add evidence references or status as a separate field and preserve availability semantics during migration. A document without that field projects `UNVERIFIED`; it never derives evidence from `CANDIDATE`. Unknown future schema versions fail closed, and any last-good document remains subject to the Qwen3.5 support boundary.

## Golden semantic suite

Required deterministic coverage:

- GGUF metadata classification;
- tokenizer parity on representative text;
- Qwen special tokens;
- UTF-8 and Italian text;
- emoji and newline handling;
- chat template rendering;
- thinking disabled;
- thinking enabled;
- sampler-profile resolution;
- request override precedence;
- fixed-seed repeatability where backend semantics permit;
- `TEXT`;
- `JSON`;
- `JSON_SCHEMA`;
- stop sequences;
- streaming versus aggregate result consistency;
- user cancellation;
- generation-guard termination;
- malformed/unsupported configuration failures.

Golden template/tokenization fixtures should be generated from a trusted Qwen/Qwen3.5 reference path and reviewed before being committed. They must not contain private user content.

## Physical-device suite

Run the initial suite for exactly:

| Catalog model ID | Tier / quantization | SHA-256 |
| --- | --- | --- |
| `qwen35-08b-q4-k-m` | 0.8B Q4_K_M | `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517` |
| `qwen35-2b-q4-k-m` | 2B Q4_K_M | `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223` |

Each additional artifact or quantization is a separate later candidate and repeats the applicable suite:

1. clean install/import and integrity verification;
2. cold model load;
3. warm model reuse;
4. non-thinking short generation;
5. thinking generation;
6. representative longer prefill;
7. cancellation during prefill;
8. cancellation during decode;
9. repeated runs for memory stability;
10. approved context-tier boundary checks;
11. model unload/reload and model-switch lifecycle;
12. memory-pressure behavior where the test harness can exercise it;
13. PSS/available-memory snapshots;
14. TTFT, prefill throughput and decode throughput;
15. thermal status over repeated runs.

Evidence must remain privacy-safe and must not commit GGUF binaries, prompts, generated content, private paths or signed URLs.

## Certification matrix key

At minimum:

```text
artifact SHA-256
model tier (0.8B / 2B)
quantization
GGUF metadata fingerprint
llama.cpp revision/build
Harness version/commit
Android ABI
device model/class
Android version
runtime tuning profile version
generation profile version
context tier
```

Device-family grouping may be added later only if measured evidence justifies it.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-VAL-01 | PLANNED | Define trusted Qwen3.5 tokenizer/template golden-fixture generation process. |
| Q35-VAL-02 | PLANNED | Add 0.8B semantic golden fixtures and tests. |
| Q35-VAL-03 | PLANNED | Add 2B semantic golden fixtures and tests. |
| Q35-VAL-04 | PLANNED | Add thinking enabled/disabled and sampler-resolution coverage. |
| Q35-VAL-05 | PLANNED | Add streaming, aggregate, cancellation and guard coverage. |
| Q35-VAL-06 | PLANNED | Add output-constraint coverage for `TEXT`, `JSON`, `JSON_SCHEMA`. |
| Q35-VAL-07 | PLANNED | Define exact certification-record schema and transitions without changing catalog availability semantics. |
| Q35-VAL-08 | PLANNED | Run the 0.8B Q4_K_M physical-device matrix. |
| Q35-VAL-09 | PLANNED | Run the 2B Q4_K_M physical-device matrix on the same device classes. |
| Q35-VAL-10 | PLANNED | Add separate evidence status/reference fields to the curated catalog schema and migrate decoding tests. |
| Q35-VAL-11 | PLANNED | Surface `UNVERIFIED` for arbitrary compatible imports and prevent inherited certification. |
| Q35-VAL-12 | PLANNED | Add release evidence links and regression baselines for certified combinations. |

## Certification gate

An artifact may become `CERTIFIED` only when:

- architecture/tier admission passes;
- exact SHA-256 and quantization are recorded;
- pinned backend compatibility is proven;
- semantic golden suite passes;
- thinking/non-thinking and guard behavior pass;
- selected runtime tuning profile passed representative physical-device tests;
- memory/thermal behavior stays within the accepted device envelope;
- benchmark/evidence identity is complete;
- applicable repository clean-checkout and release gates pass.

Changing the model artifact, quantization, backend revision, template semantics, sampler implementation or runtime tuning profile invalidates the affected certification evidence until revalidated.

Q35-7 completes when the semantic and physical suites produce reviewable evidence for both initial candidates. Q35-8 completes when the separate evidence records and catalog projection enforce these status rules end to end.

## Upstream references

- https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/main/README.md
- https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/README.md
- https://github.com/ggml-org/llama.cpp/blob/master/src/models/qwen35.cpp
