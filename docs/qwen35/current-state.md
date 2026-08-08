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
| GGUF metadata inspection and SHA-256 identity | AVAILABLE | Add Qwen3.5 dense classification and compatibility rules. |
| Model import/install and curated catalog | AVAILABLE | Add Qwen3.5-only admission and certification metadata. |
| Generation, streaming and cancellation | AVAILABLE | Add Qwen3.5 template semantics, sampling and guard behavior. |
| Exact tokenization and Auto/manual context planning | AVAILABLE | Add Qwen3.5 context tiers and hybrid/recurrent capability rules. |
| Sampling presets and request overrides | AVAILABLE | Add `minP`, `presencePenalty` and Qwen3.5 presets. |
| Telemetry, health and benchmark persistence | AVAILABLE | Add Qwen3.5 fields, stop reasons and certification evidence keys. |
| Physical-device test path | AVAILABLE | Execute Qwen3.5 0.8B/2B evidence matrix. |

`AVAILABLE` means a reusable generic capability is present. It does not mean Qwen3.5 behavior is validated.

## Product-transition progress

| ID | Workstream | State | Exit condition |
| --- | --- | --- | --- |
| Q35-0 | Decision and progressive-disclosure plan | DONE | ADR 0011, target, architecture, roadmap and focused owners agree. |
| Q35-1 | Product support migration | PLANNED | Catalog, bindings and legacy inventory enforce the Qwen3.5-only envelope non-destructively. |
| Q35-2 | Model/backend compatibility | PLANNED | Only supported dense Qwen3.5 0.8B/2B artifacts can reach preparation; backend pin is proven compatible. |
| Q35-3 | Thinking/template/sampling | PLANNED | Neutral thinking intent and Qwen3.5 sampler profiles resolve deterministically. |
| Q35-4 | Generation guard | PLANNED | Runaway/repetitive thinking can be interrupted with bounded, typed stop reasons. |
| Q35-5 | Runtime/context/cache capability model | PLANNED | Context and reuse paths do not assume pure KV-cache semantics. |
| Q35-6 | Android runtime tuning | PLANNED | 0.8B and 2B have evidence-backed CPU profiles on representative devices. |
| Q35-7 | Validation suite | PLANNED | Golden/integration/device gates pass for the supported matrix. |
| Q35-8 | Certification and catalog | PLANNED | Exact evidence status is separate from availability and compatibility. |

## Immediate next slice: Q35-1

Align the current product surfaces with ADR 0011 before claiming that compatibility is Qwen3.5-only.

Begin with the shared support-envelope decision and curated eligibility. Then migrate binding and legacy-inventory behavior before updating connected presentation and fixtures. Detailed tasks and acceptance criteria belong only to [`workstreams/product-migration.md`](workstreams/product-migration.md).

## Blockers and evidence gaps

- No Qwen3.5 artifact/quantization combination is certified by this workstream yet.
- The executable catalog still exposes non-Qwen3.5, Qwen3 and Qwen3.5 4B entries; ADR 0011 is not implemented yet.
- The current pinned `llama.cpp` revision must be validated against reference Qwen3.5 GGUFs before compatibility is claimed.
- Runtime tuning values must not be chosen from desktop assumptions; they require representative Android evidence.
- Prefix/session restore or reuse must remain capability-gated until the backend behavior for Qwen3.5 hybrid/recurrent state is verified.
- Production-ready compatibility claims remain blocked on physical-device evidence.

## State transition rule

Move a row from `PLANNED` to `IN PROGRESS` only when implementation starts on `dev`. Move it to `DONE` only when its owning workstream acceptance criteria and applicable repository gates pass. A documentation decision does not make the corresponding code path complete. Do not use percentage completion.
