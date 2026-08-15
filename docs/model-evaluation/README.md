# Model evaluation and dataset benchmarking

Status: active
Document type: feature-index
Owner: model-evaluation
Canonical scope: model-evaluation.routing
Read when: locating the model-quality evaluation plan, implementation status, dependencies or owning workstream
Last reviewed: 2026-08-15

This is the single entry point for model-quality evaluation in the Android Local LLM Harness. The capability answers a different question from [`../benchmark-engine.md`](../benchmark-engine.md): the existing benchmark engine detects runtime-performance regressions from completed telemetry, while model evaluation actively executes a versioned test set against a selected supported model and scores task quality together with runtime cost.

## Product objective

The Performance surface must let a developer answer:

> Which supported local model and execution profile is the best fit for this use case on this Android device?

A run therefore combines four independent result families:

- **quality** — task correctness and instruction compliance;
- **runtime** — TTFT, prefill/decode latency and throughput;
- **resources** — memory and thermal observations;
- **reliability** — completion, timeout, invalid-output and runtime-failure rates.

The system must not collapse these dimensions into one opaque universal score. Quality may expose a transparent suite score plus category scores; runtime and resource metrics remain separate. A future scenario-specific score may be added only with explicit user-selected weights.

## Scope

Initial scope includes:

- one built-in versioned general-purpose evaluation pack;
- deterministic nested sample presets and custom sample counts;
- deterministic evaluators with no external LLM judge;
- exact model, dataset, sample-set and execution identity;
- active execution through the normal harness runtime path;
- telemetry correlation for latency, throughput, memory and thermal metrics;
- privacy-safe persistent run summaries and sample outcomes;
- custom canonical JSONL dataset import;
- Performance UI for running, inspecting and comparing models;
- comparability rules that prevent misleading quality or device-performance comparisons.

The initial product support envelope remains the repository-reviewed Qwen3.5 dense 0.8B and 2B catalog. Model evaluation does not create an arbitrary-model import path and must not bypass curated model installation, explicit model identity or runtime lifecycle policy.

## Milestones

| Milestone | State | Exit meaning |
| --- | --- | --- |
| EVAL-0 Plan and architecture | DONE | Scope, ownership, dependencies and completion gates are canonical. |
| EVAL-1 Contracts and identity | DONE | `evaluation/contracts` freezes deterministic identity, scoring, run, compatibility and failure contracts. |
| EVAL-2 Dataset system | IN PROGRESS | Manifest/case wire schema v1 is frozen; parser, validation, digest, installation, sampling and import remain. |
| EVAL-3 Deterministic evaluators | DONE | Registry, six deterministic scorer families, suite aggregation, golden/adversarial coverage and v1 compatibility semantics are frozen. |
| EVAL-4 Evaluation runner | READY | Fake-driven runner and controlled model-binding work can start independently. |
| EVAL-5 Persistence and comparison | READY | Repository/query/retention contract is frozen; in-memory and Room persistence can start in parallel. |
| EVAL-6 General Purpose v1 | READY | Exact source/license inventory can start after the dataset schema freeze while pack assembly remains later-gated. |
| EVAL-7 Performance UI and custom import | READY | Performance UDF/navigation shell can start against fakes. |
| EVAL-8 Validation and device evidence | PLANNED | Incremental deterministic evidence can progress; final Android/device gates remain late. |

Milestone state is owned by [`current-state.md`](current-state.md). Dependency order and parallel work are owned by [`roadmap.md`](roadmap.md).

## What to read

| Need | Read |
| --- | --- |
| Current state, blockers and next ready tasks | [`current-state.md`](current-state.md) |
| Milestone order, task dependencies and parallel lanes | [`roadmap.md`](roadmap.md) |
| Product behavior, scoring principles and non-goals | [`target.md`](target.md) |
| Frozen evaluator type/version/parameter/score semantics | [`evaluator-semantics-v1.md`](evaluator-semantics-v1.md) |
| Frozen manifest and canonical JSONL wire schema | [`dataset-schema-v1.md`](dataset-schema-v1.md) |
| Harness-owned General Purpose v1 source fragments | [`general-purpose-v1/README.md`](general-purpose-v1/README.md) |
| Target module boundaries and execution flow | [`architecture.md`](architecture.md) |
| Contracts, evaluators, runner and persistence tasks | [`workstreams/evaluation-core.md`](workstreams/evaluation-core.md) |
| Dataset packs, sampling, import and General Purpose v1 | [`workstreams/datasets.md`](workstreams/datasets.md) |
| Performance UI, run inspection and comparison | [`workstreams/performance-ui.md`](workstreams/performance-ui.md) |
| Test matrix, evidence and release gates | [`workstreams/validation.md`](workstreams/validation.md) |

## Status vocabulary

Task ledgers use only:

- `PLANNED` — defined but prerequisites are not all satisfied;
- `READY` — all dependencies are complete and work may start;
- `IN PROGRESS` — implementation is active;
- `BLOCKED` — an external or unresolved dependency prevents progress;
- `DONE` — implementation, tests and required documentation are merged;
- `DEFERRED` — intentionally outside the active milestone.

A task becomes `READY` only when every dependency named in the owning ledger is `DONE`.

## Plan maintenance rule

Every pull request that changes this capability must update the same change set's owning ledger before merge:

1. move completed tasks to `DONE` only after tests and documentation are complete;
2. recompute downstream tasks that have become `READY`;
3. record new blockers explicitly rather than leaving tasks ambiguously `PLANNED`;
4. update [`current-state.md`](current-state.md) with the active tasks and blockers;
5. update [`roadmap.md`](roadmap.md) only when dependency order, milestone scope or parallelization changes;
6. update the repository [`../current-state.md`](../current-state.md) and [`../roadmap.md`](../roadmap.md) when repository-level sequencing changes.

This plan is the operational source of truth for the feature; issue/PR descriptions may link to task IDs but must not become a competing status ledger.
