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

A `RuntimeMemoryBudget` expresses configured limits:

- minimum available system memory after admission;
- optional process PSS ceiling;
- safety reserve bytes;
- maximum resident contexts;
- whether an observation is mandatory for proactive admission.

The policy does not hard-code one universal percentage of device RAM. Device/profile defaults are selected from measured evidence.

### Cost estimate

A `MemoryCostEstimate` describes expected resident and peak incremental bytes for one operation. It records provenance:

- `THEORETICAL` — derived from static/model metadata or conservative analysis;
- `CANDIDATE` — calibrated but not yet approved as representative-device evidence;
- `MEASURED` — reviewed physical-device evidence for the matching identity.

Context and model-load registries bind estimates to exact model profile/digest and backend ID/revision. No registry entry is promoted to `MEASURED` from model name or GGUF file size alone.

### Residency

A `RuntimeResidencySnapshot` reports current policy-relevant state such as loaded model and resident context count. It is separate from PSS because OS/process measurements and logical resource ownership answer different questions.

## Admission reasons

Reject/downshift outcomes use typed reason codes suitable for telemetry and UI mapping without exception text. Reasons include:

- platform low-memory signal;
- available-memory floor would be crossed;
- configured process PSS ceiling would be crossed;
- resident-context limit reached;
- required cost profile unavailable under fail-closed policy;
- observation unavailable under fail-closed policy;
- no lower approved context tier satisfies both capacity and memory constraints;
- overflow-safe arithmetic cannot prove a safe bound.

## Context admission

Token planning first calculates required capacity from prompt + output + safety reserve, then exposes approved memory-eligible tiers.

Memory downshift is not token truncation. If the preferred tier is too expensive, the planner evaluates smaller approved tiers only while they still satisfy required capacity.

```text
required token capacity = 1800
preferred tier = 4096

4096 -> rejected by memory budget
2048 -> satisfies token capacity and memory budget

result = DOWNSHIFT(2048)
```

If required capacity is 3500, a 2048 tier is never offered as fallback.

Admission runs only when a new native context is required. Reusing an existing compatible context does not re-run policy because it does not allocate another context.

## Model admission

The one-model invariant remains and model switching cannot occur while sessions/requests are active.

For a cold/switch load, any previous model is released first, then model admission runs against post-unload residency before backend initialization and native `loadModel()`. A compatible already-loaded model returns through the warm fast path without re-admission.

The model cost registry does not treat GGUF file size as resident-memory truth. Representative measurements populate versioned cost records for exact compatible runtime identities.

## Safety reserve

Safety reserve protects memory not owned by the model/context estimate: Android/framework activity, allocator variation, transient prefill/workspace, Binder/UI activity and measurement error.

The reserve is configuration/evidence, not an undocumented magic number. It must be present in the effective memory profile/diagnostic output used for physical evidence.

## Observability

Configured governor decisions emit bounded `memory.admission` structured logs with:

- operation/resource kind;
- requested/effective context tier when applicable;
- ALLOW/DOWNSHIFT/REJECT outcome;
- typed decision/admission reason;
- cost-profile identity/provenance and resident/peak estimate when available.

Prompts, generated output and document content never enter memory telemetry. Policy-level admission events do not require a request identifier.

## Validation

Unit coverage includes exact boundary values, overflow-safe arithmetic, nullable observations, low-memory override, resident-context limits and profile provenance/identity handling.

Integration coverage verifies that:

- rejected context admission performs zero backend context creation/generation calls;
- reusable contexts do not re-run admission;
- rejected model admission performs zero backend initialization/native model-load calls;
- a warm compatible model does not re-run model admission;
- downshift can materialize only an approved tier that still satisfies token capacity.

Physical validation must still compare policy decisions with measured PSS/available-memory behavior on representative devices. A successful allocation that happened to survive is not evidence that a rejected policy was too conservative; tuning uses repeated controlled measurements.
