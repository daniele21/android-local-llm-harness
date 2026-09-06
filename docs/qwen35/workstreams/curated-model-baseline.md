# Qwen3.5 curated model baseline

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.curated-model-baseline
Read when: removing generic model-selection paths or defining the product-visible Qwen3.5 model set
Last reviewed: 2026-09-06

## Goal

Keep the product model surface closed, deliberate and exact.

Users do not import arbitrary GGUF files. The product exposes only repository-reviewed Qwen3.5 dense 0.8B, 2B and ADR-0019 4B 4-bit releases through the curated catalog. Model binaries are downloaded and verified at runtime; they are not bundled in source control.

The 4B extension does not reopen generic model management. It adds one explicitly reviewed tier from `unsloth/Qwen3.5-4B-GGUF`, restricted to the seven 4-bit variants listed below.

## Product model source

```text
built-in curated catalog
  -> reviewed Qwen3.5 0.8B / 2B / 4B-4bit release
  -> verified download
  -> SHA-256 + GGUF inspection
  -> ModelStore installation
  -> catalog-anchored binding
```

A consumer cannot supply a filename, URL, family, architecture or local GGUF to extend this set. Developer validation tooling may continue to inject a known artifact in isolated test applications; that is test infrastructure, not a product import feature.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-BASE-01 | DONE | Executable curated catalog is closed to reviewed Qwen3.5 releases. |
| Q35-BASE-02 | DONE | Consumer GGUF document-picker/import actions, effects and controller path are removed. |
| Q35-BASE-03 | DONE | Product binding/profile identity is anchored to exact curated releases and product fixtures use Qwen3.5. |
| Q35-BASE-04 | DONE | External-import inventory origin/projection is removed and out-of-catalog selections are not synthesized. |
| Q35-BASE-05 | DONE | Verified catalog download/install is the only consumer acquisition/persistence path; developer injection remains isolated. |
| Q35-BASE-06 | DONE | Models/Overview/Playground expose only the curated Qwen3.5 product path and no consumer import CTA. |
| Q35-BASE-07 | DONE | Catalog, binding, persistence, inventory and connected-UI validation passes for the closed surface. |
| Q35-BASE-08 | IN PROGRESS | Admit and validate the exact Unsloth Qwen3.5 4B 4-bit set without granting certification before physical-device evidence. |

## Implemented closed catalog

The executable catalog contains fourteen reviewed releases.

### Qwen3.5 0.8B

- `qwen35-08b-q4-k-m`;
- `qwen35-08b-q5-k-m`;
- `qwen35-08b-q8-0`;
- `qwen35-08b-ud-iq2-xxs`.

### Qwen3.5 2B

- `qwen35-2b-q4-k-m`;
- `qwen35-2b-q5-k-m`;
- `qwen35-2b-ud-iq2-xxs`.

### Qwen3.5 4B — 4-bit only

Pinned source: `unsloth/Qwen3.5-4B-GGUF` revision `e87f176479d0855a907a41277aca2f8ee7a09523`.

- `qwen35-4b-ud-q4-k-xl` — `UD-Q4_K_XL`;
- `qwen35-4b-q4-k-m` — `Q4_K_M`;
- `qwen35-4b-q4-k-s` — `Q4_K_S`;
- `qwen35-4b-iq4-xs` — `IQ4_XS`;
- `qwen35-4b-iq4-nl` — `IQ4_NL`;
- `qwen35-4b-q4-0` — `Q4_0`;
- `qwen35-4b-q4-1` — `Q4_1`.

`UD-Q4_K_XL` is the preferred initial validation artifact because Unsloth uses it in its `llama.cpp` examples. Preference is not certification and is not represented as a generic fallback rule.

The established certification candidates remain Qwen3.5 0.8B Q4_K_M and Qwen3.5 2B Q4_K_M. Every 4B release is a catalog `CANDIDATE` until its exact digest passes the required physical-device evidence matrix. Certification is never inherited across digests or quantizations.

## 4B resource and generation policy

Unsloth estimates approximately 5.5 GB combined memory for Qwen3.5 4B at 4-bit. Harnex therefore starts with a deliberately conservative catalog compatibility policy of 8 GB minimum total device RAM and 12 GB recommended RAM, while keeping runtime tuning in `CANDIDATE` state.

Harnex maps the 4B preset vocabulary onto Unsloth's published Qwen3.5 sampler guidance: `top_k=20`, `min_p=0`, `repeat_penalty=1`, with tier-specific temperature/top-p/presence-penalty values for non-thinking general, non-thinking reasoning, thinking and precise/coding intents. Existing 0.8B/2B defaults are unchanged.

## Product-boundary result

- runtime binding accepts only metadata matching a current curated release;
- runtime profile identity is anchored to release `profileKey`;
- consumer provenance is catalog-download provenance;
- `HarnessModelOrigin.IMPORTED` and external-selection synthesis are removed;
- persisted metadata outside the current catalog is ignored rather than represented as legacy/unsupported;
- the Models UI reconciles catalog, installation, selection and runtime state into one product view;
- explicit runtime unload is separate from destructive model removal;
- developer-only artifact injection remains isolated from consumer APIs;
- 4B model selection resolves a distinct `B4` generation/runtime tier rather than silently falling back to 2B.

## Validation evidence

The original Q35-1 acceptance remains satisfied for the established 0.8B/2B surface. The 4B extension requires its own deterministic repository validation plus exact-artifact physical-device evidence before any 4B certification claim.

Repository-side acceptance for the 4B extension includes:

- exact curated digests, byte sizes and pinned source revision;
- a guard proving the 4B catalog contains only the seven reviewed 4-bit quantizations;
- tier-aware generation tests for the Unsloth sampler baselines;
- phone UI/model-selection tests proving 4B resolves as `B4`;
- selected preset re-resolution so model changes do not retain stale lower-tier sampler defaults;
- repository-selected Android/static/unit/package validation on the exact branch HEAD.

## Acceptance criteria

The closed product catalog is correct when 0.8B/2B behavior remains intact, only the exact reviewed 4B 4-bit artifacts are added, no arbitrary-import/family fallback is reopened, the 4B tier owns distinct generation/resource policy, deterministic repository validation passes, and 4B remains explicitly uncertified until representative physical-device evidence exists.

Artifact/backend proof is owned by [`model-compatibility.md`](model-compatibility.md). The durable tier decision is [ADR 0019](../../adr/0019-qwen35-4b-four-bit-product-support.md).
