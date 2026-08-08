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
| Model download/install and curated catalog | AVAILABLE | Closed catalog, catalog-only binding/persistence and consumer-import removal are implemented; complete repository validation before closing Q35-1. |
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
| Q35-1 | Curated model baseline | IN PROGRESS | Closed Qwen3.5-only product surface is implemented and applicable repository validation passes. |
| Q35-2 | Model/backend compatibility | PLANNED | Exact curated 0.8B/2B artifacts are proven against the pinned backend. |
| Q35-3 | Thinking/template/sampling | PLANNED | Neutral thinking intent and Qwen3.5 sampler profiles resolve deterministically. |
| Q35-4 | Generation guard | PLANNED | Runaway/repetitive thinking can be interrupted with bounded, typed stop reasons. |
| Q35-5 | Runtime/context/cache capability model | PLANNED | Context and reuse paths do not assume pure KV-cache semantics. |
| Q35-6 | Android runtime tuning | PLANNED | 0.8B and 2B have evidence-backed CPU profiles on representative devices. |
| Q35-7 | Validation suite | PLANNED | Golden/integration/device gates pass for the supported matrix. |
| Q35-8 | Certification | PLANNED | Exact curated artifacts receive evidence-backed certification independently of catalog availability. |

## Implemented in Q35-1

- `CuratedModelCatalog` exposes only the seven reviewed Qwen3.5 dense 0.8B/2B releases.
- Qwen3 and other-family curated bootstrap files are removed; Qwen3.5 4B releases are removed from the executable catalog.
- The consumer Android document picker and manual GGUF import actions/effects are removed.
- The dead consumer import implementation in `PhoneTestController` is removed; selection starts from verified installed catalog models.
- Product runtime binding is catalog-anchored: selected model metadata must match a curated release rather than an arbitrary architecture/family string.
- Runtime profile identity is derived from the curated release profile key instead of a generic imported-model profile identifier.
- Product artifact provenance uses the curated download path instead of the former storage-access-framework/import provenance.
- Product-level fixtures that represented retired Qwen generations or other model families have been migrated to Qwen3.5 while family-neutral lifecycle tests remain generic.
- `HarnessModelOrigin.IMPORTED` and the external-selection inventory projection are removed; selections outside the current catalog are not synthesized as product inventory items.
- Installed-model metadata persistence is catalog-only: entries that do not correspond to a current curated release are ignored rather than surfaced through a legacy state.
- Overview, Models and Playground direct users to the Qwen3.5 catalog rather than arbitrary local files.
- Developer/device-test artifact injection remains isolated outside the consumer acquisition path.
- Catalog, binding, persistence, inventory and connected-UI tests were updated for the closed Qwen3.5 surface.

These changes are implemented on `dev`, but Q35-1 remains `IN PROGRESS` until the applicable Android/package validation gates pass on the current implementation head.

## Immediate next slice: Q35-1 validation closure

Do not add more compatibility or legacy handling. The immediate work is to close the Q35-1 validation gate:

1. resolve any remaining Android/package validation failure on the current implementation;
2. rerun the scoped repository validation for catalog, binding, persistence, inventory and connected UI;
3. perform a final search for consumer-facing multi-family/import-only product paths;
4. mark Q35-1 `DONE` only after the owning acceptance criteria and applicable repository gates pass.

Q35-2 model/backend compatibility starts only after this closure. Detailed tasks and acceptance criteria belong only to [`workstreams/curated-model-baseline.md`](workstreams/curated-model-baseline.md).

## Blockers and evidence gaps

- Q35-1 implementation still needs a green applicable Android/package validation result before milestone closure.
- No Qwen3.5 artifact/quantization combination is certified by this workstream yet.
- The current pinned `llama.cpp` revision must be validated against the exact curated Qwen3.5 GGUFs before compatibility is claimed.
- Runtime tuning values require representative Android evidence.
- Prefix/session restore or reuse remains capability-gated until the backend behavior for Qwen3.5 hybrid/recurrent state is verified.
- Production-ready compatibility claims remain blocked on physical-device evidence.

## State transition rule

Move a row from `PLANNED` to `IN PROGRESS` only when implementation starts on `dev`. Move it to `DONE` only when its owning workstream acceptance criteria and applicable repository gates pass. A documentation decision does not make the corresponding code path complete. Do not use percentage completion.
