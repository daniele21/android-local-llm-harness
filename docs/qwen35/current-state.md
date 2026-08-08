# Qwen3.5 workstream state

Status: active
Document type: workstream-state
Owner: qwen35
Canonical scope: qwen35.state
Read when: determining Qwen3.5-only product progress, blockers or the next implementation slice
Last reviewed: 2026-08-08

This ledger reports only Qwen3.5-only product progress. Repository-wide integrated state remains owned by [`../current-state.md`](../current-state.md).

## Existing reusable baseline

The following capabilities already exist generically on `dev` and are inputs to this plan; they are not Qwen3.5-certified yet.

| Capability | State | Qwen3.5 work still required |
| --- | --- | --- |
| GGUF metadata inspection and SHA-256 identity | AVAILABLE | Validate the exact curated Qwen3.5 artifacts and backend pin. |
| Model download/install and curated catalog | AVAILABLE | Remove multi-family entries and arbitrary user import from the product path. |
| Generation, streaming and cancellation | AVAILABLE | Add Qwen3.5 template semantics, sampling and guard behavior. |
| Exact tokenization and Auto/manual context planning | AVAILABLE | Add Qwen3.5 context tiers and hybrid/recurrent capability rules. |
| Sampling presets and request overrides | AVAILABLE | Add `minP`, `presencePenalty` and Qwen3.5 presets. |
| Telemetry, health and benchmark persistence | AVAILABLE | Add Qwen3.5 fields, stop reasons and certification evidence keys. |
| Physical-device test path | AVAILABLE | Execute Qwen3.5 0.8B/2B evidence matrix. |

`AVAILABLE` means a reusable generic capability is present. It does not mean Qwen3.5 behavior is validated.

## Product progress

| ID | Workstream | State | Exit condition |
| --- | --- | --- | --- |
| Q35-0 | Decision and progressive-disclosure plan | DONE | ADR 0011, target, architecture, roadmap and focused owners agree. |
| Q35-1 | Curated model baseline | PLANNED | Product model selection is closed to curated Qwen3.5 dense 0.8B/2B releases; manual import and multi-family product paths are removed. |
| Q35-2 | Model/backend compatibility | PLANNED | Exact curated 0.8B/2B artifacts are proven against the pinned backend. |
| Q35-3 | Thinking/template/sampling | PLANNED | Neutral thinking intent and Qwen3.5 sampler profiles resolve deterministically. |
| Q35-4 | Generation guard | PLANNED | Runaway/repetitive thinking can be interrupted with bounded, typed stop reasons. |
| Q35-5 | Runtime/context/cache capability model | PLANNED | Context and reuse paths do not assume pure KV-cache semantics. |
| Q35-6 | Android runtime tuning | PLANNED | 0.8B and 2B have evidence-backed CPU profiles on representative devices. |
| Q35-7 | Validation suite | PLANNED | Golden/integration/device gates pass for the supported matrix. |
| Q35-8 | Certification | PLANNED | Exact curated artifacts receive evidence-backed certification independently of catalog availability. |

## Immediate next slice: Q35-1

Delete generic product choices before adding Qwen3.5-specific execution behavior.

Start by replacing the executable catalog with Qwen3.5 dense 0.8B/2B entries only. Then remove consumer-facing manual GGUF import and the inventory/UI states that exist only to represent arbitrary external models. Keep verified catalog download/install and isolated developer test injection.

Detailed tasks and acceptance criteria belong only to [`workstreams/curated-model-baseline.md`](workstreams/curated-model-baseline.md).

## Blockers and evidence gaps

- The executable catalog still contains non-Qwen3.5, Qwen3 and Qwen3.5 4B entries.
- Consumer-facing manual GGUF import still exists in the current implementation and must be removed from the product path.
- No Qwen3.5 artifact/quantization combination is certified by this workstream yet.
- The current pinned `llama.cpp` revision must be validated against the exact curated Qwen3.5 GGUFs before compatibility is claimed.
- Runtime tuning values require representative Android evidence.
- Prefix/session restore or reuse remains capability-gated until the backend behavior for Qwen3.5 hybrid/recurrent state is verified.
- Production-ready compatibility claims remain blocked on physical-device evidence.

## State transition rule

Move a row from `PLANNED` to `IN PROGRESS` only when implementation starts on `dev`. Move it to `DONE` only when its owning workstream acceptance criteria and applicable repository gates pass. A documentation decision does not make the corresponding code path complete. Do not use percentage completion.
