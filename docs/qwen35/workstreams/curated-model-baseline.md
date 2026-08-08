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

The only product model source is:

```text
built-in curated catalog
  -> reviewed Qwen3.5 0.8B / 2B release
  -> verified download
  -> SHA-256 + GGUF inspection
  -> ModelStore installation
  -> explicit supported binding
```

A consumer cannot supply a filename, URL, family, architecture or local GGUF to extend this set.

Developer validation tooling may continue to inject a known test artifact into isolated test applications when required for device evidence. That is test infrastructure, not a product model-import feature.

## What is removed

Q35-1 removes rather than models these cases:

- non-Qwen3.5 catalog releases;
- Qwen3.5 4B, 9B and MoE catalog releases;
- user-facing manual GGUF import;
- external-import inventory source/state;
- product bindings and fixtures for retired model families;
- legacy/unsupported retained-model presentation;
- fallback or substitution behavior intended to recover from retired models.

No new `LEGACY_UNSUPPORTED`, `UNSUPPORTED_FAMILY` or equivalent product state is required for these removed paths.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-BASE-01 | PLANNED | Replace the executable curated catalog with Qwen3.5 dense 0.8B/2B releases only. |
| Q35-BASE-02 | PLANNED | Remove consumer-facing manual GGUF import actions, routes and product contracts that exist only for arbitrary imports. |
| Q35-BASE-03 | PLANNED | Remove product profile/binding mappings and fixtures for non-Qwen3.5 and unsupported Qwen3.5 tiers. |
| Q35-BASE-04 | PLANNED | Simplify inventory projection and model presentation after external-import/legacy states disappear. |
| Q35-BASE-05 | PLANNED | Keep verified catalog download/install as the only product acquisition path and preserve isolated developer test injection. |
| Q35-BASE-06 | PLANNED | Update connected Models/Playground UI so only curated downloadable/installed Qwen3.5 models can be chosen. |
| Q35-BASE-07 | PLANNED | Replace product-level multi-family tests with closed-catalog tests while preserving family-neutral lifecycle contract tests. |

## Initial curated set

The initial catalog is limited to existing reviewed 0.8B and 2B Qwen3.5 releases. The first certification candidates remain:

- Qwen3.5 0.8B Q4_K_M;
- Qwen3.5 2B Q4_K_M.

Other 0.8B/2B quantizations may remain catalog candidates for experimentation, but certification is attached only to exact artifacts that pass Q35-7/Q35-8 evidence.

## Acceptance criteria

Q35-1 is complete when:

- the product catalog contains no family other than Qwen3.5 and no tier other than dense 0.8B/2B;
- there is no consumer-facing manual GGUF import path;
- product bindings can reference only entries in the curated set;
- Models and Playground expose only curated releases and their installed state;
- no legacy/unsupported model state is introduced to preserve retired product cases;
- catalog download/install still verifies exact size, SHA-256 and GGUF integrity before publication;
- isolated device-test artifact injection remains clearly outside product APIs;
- deterministic catalog, binding, inventory and connected-UI tests pass.

Artifact/backend proof begins in [`model-compatibility.md`](model-compatibility.md) after this closed model surface is established.
