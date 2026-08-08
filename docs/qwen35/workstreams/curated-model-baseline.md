# Qwen3.5 curated model baseline

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.curated-model-baseline
Read when: removing generic model-selection paths or defining the product-visible Qwen3.5 model set
Last reviewed: 2026-08-08

## Goal

Make the product model surface closed and deliberate before Qwen3.5-specific execution work begins.

Users do not import arbitrary GGUF files. The product exposes only repository-reviewed Qwen3.5 dense 0.8B and 2B releases through the curated catalog. Model binaries are still downloaded and verified at runtime; they are not bundled in source control.

This workstream is deletion-oriented. It does not introduce legacy states, unsupported-family states, migration compatibility or generic fallback behavior.

## Product model source

```text
built-in curated catalog
  -> reviewed Qwen3.5 0.8B / 2B release
  -> verified download
  -> SHA-256 + GGUF inspection
  -> ModelStore installation
  -> catalog-anchored binding
```

A consumer cannot supply a filename, URL, family, architecture or local GGUF to extend this set. Developer validation tooling may continue to inject a known artifact in isolated test applications; that is test infrastructure, not a product import feature.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-BASE-01 | DONE | Executable curated catalog contains only Qwen3.5 dense 0.8B/2B releases. |
| Q35-BASE-02 | DONE | Consumer GGUF document-picker/import actions, effects and controller path are removed. |
| Q35-BASE-03 | DONE | Product binding/profile identity is anchored to exact curated releases and product fixtures use Qwen3.5. |
| Q35-BASE-04 | DONE | External-import inventory origin/projection is removed and out-of-catalog selections are not synthesized. |
| Q35-BASE-05 | DONE | Verified catalog download/install is the only consumer acquisition/persistence path; developer injection remains isolated. |
| Q35-BASE-06 | DONE | Models/Overview/Playground expose only the curated Qwen3.5 product path and no consumer import CTA. |
| Q35-BASE-07 | DONE | Catalog, binding, persistence, inventory and connected-UI validation passes for the closed surface. |

## Implemented closed catalog

The executable catalog contains exactly seven reviewed releases:

### Qwen3.5 0.8B

- `qwen35-08b-q4-k-m`;
- `qwen35-08b-q5-k-m`;
- `qwen35-08b-q8-0`;
- `qwen35-08b-ud-iq2-xxs`.

### Qwen3.5 2B

- `qwen35-2b-q4-k-m`;
- `qwen35-2b-q5-k-m`;
- `qwen35-2b-ud-iq2-xxs`.

The first certification candidates remain Qwen3.5 0.8B Q4_K_M and Qwen3.5 2B Q4_K_M. Other listed quantizations are catalog candidates, not automatically certified artifacts.

## Product-boundary result

- runtime binding accepts only metadata matching a current curated release;
- runtime profile identity is anchored to release `profileKey`;
- consumer provenance is catalog-download provenance;
- `HarnessModelOrigin.IMPORTED` and external-selection synthesis are removed;
- persisted metadata outside the current catalog is ignored rather than represented as legacy/unsupported;
- the Models UI reconciles catalog, installation, selection and runtime state into one product view;
- explicit runtime unload is separate from destructive model removal;
- developer-only artifact injection remains isolated from consumer APIs.

## Validation evidence

Q35-1 acceptance is satisfied on `dev`:

- the user manually validated curated Models, download/install/select/generate, restart persistence and non-destructive unload;
- repository guards, native tests, Android validation and connected tests are green;
- Android artifact packaging is green with the exact runtime native-library set;
- final source scans found no retained consumer multi-family/import-only fallback path.

## Acceptance criteria

Q35-1 is complete because the product catalog is Qwen3.5 0.8B/2B-only, manual consumer GGUF import is absent, bindings are catalog anchored, no retired-family compatibility state is retained, verified catalog installation remains the product acquisition path, isolated developer injection remains outside product APIs and the applicable deterministic/Android/package gates pass.

Artifact/backend proof is owned by [`model-compatibility.md`](model-compatibility.md).
