# Qwen3.5 validation and certification

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.validation-certification
Read when: adding Qwen3.5 tests, defining physical-device evidence or deciding whether an artifact can be marked certified
Last reviewed: 2026-08-07

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

## Certification states

| State | Meaning |
| --- | --- |
| `CERTIFIED` | Exact artifact/backend/device envelope passed all mandatory semantic and physical-device gates. |
| `TESTED` | Meaningful automated/device evidence exists, but at least one certification gate is incomplete. |
| `EXPERIMENTAL` | Allowed for controlled testing with known evidence gaps. |
| `UNVERIFIED` | Compatible imported artifact without exact certification evidence. |
| `UNSUPPORTED` | Outside the active Qwen3.5 0.8B/2B dense target or incompatible with the runtime. |

A status attaches to exact evidence identity, not a display name such as “Qwen3.5 2B Q4”.

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

For each candidate certified artifact/quantization:

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
| Q35-VAL-07 | PLANNED | Define exact certification matrix schema and status transitions. |
| Q35-VAL-08 | PLANNED | Run 0.8B physical-device matrix for candidate quantizations. |
| Q35-VAL-09 | PLANNED | Run 2B physical-device matrix for candidate quantizations. |
| Q35-VAL-10 | PLANNED | Feed certified artifact metadata into the curated catalog. |
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

## Upstream references

- https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/main/README.md
- https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/README.md
- https://github.com/ggml-org/llama.cpp/blob/master/src/models/qwen35.cpp
