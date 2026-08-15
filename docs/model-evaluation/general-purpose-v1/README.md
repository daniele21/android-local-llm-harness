# General Purpose v1 — Harness-owned source fragments

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.general-purpose-harness-cases
Read when: reviewing or changing the project-authored General Purpose v1 structured-output and context-retrieval cases
Last reviewed: 2026-08-15

This directory contains the two synthetic Harness-owned authoring lanes that can be completed before the public-derived benchmark subset and final 200-case pack are frozen.

- `harness-structured-output.jsonl`: 20 GP-05 cases using `JSON_FIELDS` v1 with deterministic expected JSON objects.
- `harness-context-retrieval.jsonl`: 20 GP-06 cases using `EXACT_MATCH` v1 with synthetic distractor context and target positions distributed across beginning, middle and end.

These files use the canonical case JSONL wire schema v1, but they are **not yet the installable `general-purpose-v1` pack**. GP-07 owns final assembly with the 160 reviewed public-derived cases; GP-08 owns frozen 20/50/100/200 preset membership and the final pack digest.

All content in these two fragments is project-authored and synthetic. It contains no copied public benchmark cases, personal data or proprietary source material.

## Scoring boundary

Structured-output cases require the declared top-level fields and structurally compare their expected values through `JSON_FIELDS` v1. The current evaluator intentionally ignores unrelated extra generated fields, so these GP-05 cases do **not** claim strict additional-property rejection. That behavior would require an explicit evaluator-version decision rather than silently changing v1 semantics.

Context-retrieval cases test answer recovery from provided context, not maximum-context claims. `targetPosition` records beginning/middle/end placement only; token-tier metadata remains intentionally absent until supported tokenizer behavior is validated.

## Validation

Run:

```bash
python3 scripts/validate-general-purpose-harness-cases.py
```

The validator enforces exact case counts, global ID uniqueness, canonical category/evaluator choices, structured expected-field consistency, answer presence in retrieval context and the 7/6/7 target-position distribution.
