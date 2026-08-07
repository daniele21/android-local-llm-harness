# Qwen3.5 workstream state

Status: active
Document type: workstream-state
Owner: qwen35
Canonical scope: qwen35.state
Read when: determining Qwen3.5 specialization progress, blockers or the next implementation slice
Last reviewed: 2026-08-07

This ledger reports only Qwen3.5 specialization progress. Repository-wide integrated state remains owned by [`../current-state.md`](../current-state.md).

## Existing reusable baseline

The following capabilities already exist generically on `dev` and are inputs to this plan; they are not Qwen3.5-certified yet.

| Capability | State | Qwen3.5 work still required |
| --- | --- | --- |
| GGUF metadata inspection and SHA-256 identity | AVAILABLE | Add Qwen3.5 dense classification and compatibility rules. |
| Model import/install and curated catalog | AVAILABLE | Add Qwen3.5-only admission and certification metadata. |
| Generation, streaming and cancellation | AVAILABLE | Add Qwen3.5 template semantics, sampling and guard behavior. |
| Exact tokenization and Auto/manual context planning | AVAILABLE | Add Qwen3.5 context tiers and hybrid/recurrent capability rules. |
| Sampling presets and request overrides | AVAILABLE | Add `minP`, `presencePenalty` and Qwen3.5 presets. |
| Telemetry, health and benchmark persistence | AVAILABLE | Add Qwen3.5 fields, stop reasons and certification evidence keys. |
| Physical-device test path | AVAILABLE | Execute Qwen3.5 0.8B/2B evidence matrix. |

`AVAILABLE` means a reusable generic capability is present. It does not mean Qwen3.5 behavior is validated.

## Specialization progress

| ID | Workstream | State | Exit condition |
| --- | --- | --- | --- |
| Q35-0 | Scope and progressive-disclosure planning | DONE | Canonical target, architecture, roadmap and workstream owners exist. |
| Q35-1 | Model/backend compatibility | PLANNED | Only supported dense Qwen3.5 0.8B/2B artifacts can reach preparation; backend pin is proven compatible. |
| Q35-2 | Thinking/template/sampling | PLANNED | `enable_thinking` and Qwen3.5 sampler profiles resolve deterministically and are tested. |
| Q35-3 | Generation guard | PLANNED | Runaway/repetitive thinking can be interrupted with bounded, typed stop reasons. |
| Q35-4 | Runtime/context/cache capability model | PLANNED | Context and reuse paths do not assume pure KV-cache semantics. |
| Q35-5 | Android runtime tuning | PLANNED | 0.8B and 2B have evidence-backed CPU profiles on representative devices. |
| Q35-6 | Validation suite | PLANNED | Golden/integration/device gates pass for the supported matrix. |
| Q35-7 | Certification and catalog | PLANNED | Certified artifacts are exact, reproducible and surfaced distinctly from unverified imports. |

## Immediate next slice: Q35-1

Implement the compatibility boundary before changing prompting or sampling.

1. Inspect the repository's pinned `llama.cpp` revision and prove that its Qwen3.5 dense path can load the selected reference GGUFs.
2. Introduce a Qwen3.5 descriptor derived from GGUF/catalog metadata rather than filename heuristics.
3. Restrict the specialization path to dense Qwen3.5 `0.8B` and `2B`.
4. Reject Qwen3.5 MoE and every non-Qwen3.5 architecture with typed domain failures before native preparation.
5. Define backend compatibility metadata that records the validated `llama.cpp` revision/build.
6. Add deterministic unit/integration coverage for accepted and rejected artifacts.
7. Keep catalog model-tier declarations separate from architecture detection; do not infer `0.8B` or `2B` solely from display names.

Detailed tasks and acceptance criteria: [`workstreams/model-compatibility.md`](workstreams/model-compatibility.md).

## Blockers and evidence gaps

- No Qwen3.5 artifact/quantization combination is certified by this workstream yet.
- The current pinned `llama.cpp` revision must be validated against reference Qwen3.5 GGUFs before compatibility is claimed.
- Runtime tuning values must not be chosen from desktop assumptions; they require representative Android evidence.
- Prefix/session restore or reuse must remain capability-gated until the backend behavior for Qwen3.5 hybrid/recurrent state is verified.
- Production-ready compatibility claims remain blocked on physical-device evidence.

## State transition rule

Move a row from `PLANNED` to `IN PROGRESS` only when implementation starts on `dev`. Move it to `DONE` only when its owning workstream acceptance criteria and applicable repository gates pass. Do not use percentage completion.
