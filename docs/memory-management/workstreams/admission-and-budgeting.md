# Memory admission and budgeting

Status: active
Document type: feature-specification
Owner: runtime-memory
Canonical scope: memory-management.admission-budgeting
Read when: changing memory budgets, cost profiles, model/context admission or context downshift policy
Last reviewed: 2026-08-16

## Goal

Prevent avoidable OOM/low-memory events by deciding whether an expensive model/context operation is safe before JNI allocation begins.

The policy is backend-neutral. llama.cpp remains responsible for allocation mechanics; the Harness owns product-level admission and safety headroom.

## Neutral inputs

### Observation

A `RuntimeMemoryObservation` carries optional process/system signals such as process PSS, native heap, Java heap, available system memory and low-memory state. Android adapters may populate these fields; unit tests can provide deterministic values.

Unknown is distinct from zero.

### Budget

A `RuntimeMemoryBudget` expresses configured limits. Candidate fields include:

- minimum available system memory after admission;
- process PSS ceiling when a device/profile uses one;
- safety reserve bytes;
- maximum resident contexts;
- whether an observation is mandatory for proactive admission.

The policy does not hard-code one universal percentage of device RAM. Device-class/profile defaults are selected from measured evidence.

### Cost estimate

A `MemoryCostEstimate` describes expected resident and peak incremental bytes for one operation. It records provenance:

- `THEORETICAL` — derived from static/model metadata or conservative formula;
- `CANDIDATE` — calibrated but not yet approved as representative device evidence;
- `MEASURED` — reviewed physical-device evidence for the matching identity.

An estimate never claims byte-perfect allocator ownership.

### Residency

A `RuntimeResidencySnapshot` reports current policy-relevant state such as loaded model and resident context count. It is separate from PSS because OS/process measurements and logical resource ownership answer different questions.

## Admission reasons

Reject/downshift outcomes use typed reason codes suitable for telemetry and UI mapping without exception text. Expected reasons include:

- platform reports low memory;
- available-memory floor would be crossed;
- configured process-resident ceiling would be crossed;
- resident-context limit reached;
- required cost profile unavailable under fail-closed policy;
- observation unavailable under fail-closed policy;
- no lower approved context tier satisfies both capacity and memory constraints.

## Context admission

Context planning first calculates required token capacity using the existing prompt/output/safety-reserve rule. It then evaluates approved tiers from smallest sufficient to larger candidates.

Memory downshift is therefore not token truncation. If the requested/preferred tier is too expensive, the runtime may choose a smaller approved tier only when that tier still satisfies required capacity.

Example:

```text
required token capacity = 1800
preferred tier = 4096

4096 -> rejected by memory budget
2048 -> satisfies token capacity and memory budget

result = DOWNSHIFT(2048)
```

If required capacity is 3500, a 2048 tier is never offered as a memory fallback.

## Model admission

The model path is evaluated before loading. The one-model invariant remains and model switching cannot occur while sessions/requests are active.

Model file size may be used as one conservative signal but is not treated as resident-memory truth. Reviewed physical measurements can replace coarse candidate estimates for supported profiles.

## Safety reserve

Safety reserve protects memory not owned by the model/context estimate: Android/framework activity, allocator variation, transient prefill/workspace, Binder/UI activity and measurement error.

The reserve is configuration/evidence, not an undocumented magic number. It must be present in the effective memory profile/diagnostic output used for evidence.

## Observability

Admission emits only bounded, non-sensitive fields:

- operation/resource kind;
- requested/effective context tier when applicable;
- decision;
- reason code;
- observed byte counters that are already safe resource metrics;
- cost-profile identity/provenance.

Prompts, generated output and document content never enter memory telemetry.

## Validation

Unit coverage includes exact boundary values, overflow-safe byte arithmetic, nullable observations, low-memory override, resident-context limits and measured/candidate profile handling.

Integration coverage later verifies that a rejected admission does not call backend `loadModel`/`createContext`, and that a downshift invokes backend creation exactly once with the effective tier.

Physical validation checks admission against measured PSS/available-memory behavior on representative devices. A successful allocation that happened to survive is not evidence that a rejected policy was too conservative; policy tuning uses repeated controlled measurements.
