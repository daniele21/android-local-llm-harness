# Memory-management target

Status: active
Document type: target-specification
Owner: runtime-memory
Canonical scope: memory-management.target
Read when: changing the intended memory-safety, residency, admission or recovery behavior of the Harness
Last reviewed: 2026-08-16

## Goal

The Harness must keep model inference usable on constrained Android devices by making memory use explicit, bounded, observable and recoverable. A request must not be admitted solely because llama.cpp can attempt the allocation; the runtime owns whether the operation is safe for the current device state and configured policy.

The target is a resource governor around the existing runtime, not a replacement allocator or a fork of llama.cpp memory management.

## Required properties

### Explicit ownership and deterministic release

Every model, context, request and long-lived runtime resource has one owner and one terminal release path. Cancellation, app/service shutdown, model switch, memory pressure and partial failures converge to the same cleanup invariants.

After final shutdown completes:

- no session owns a native context;
- no queued or active generation remains;
- no native model handle remains registered;
- the llama.cpp backend is shut down;
- repeated close/shutdown calls are harmless.

### Bounded residency

The runtime has explicit upper bounds for resident model/context state and outstanding work. Limits are policy, not incidental consequences of device OOM behavior.

At minimum the runtime can bound:

- loaded models;
- resident contexts;
- queued generation requests;
- per-consumer outstanding work for shared-runtime use;
- warm-idle lifetime of shared runtime resources.

### Proactive admission

Before model or context materialization, the runtime evaluates an admission request against:

- current resource observation when available;
- configured safety reserve;
- resident resource count and policy limits;
- a conservative cost estimate for the requested operation.

The decision is typed and observable. The minimum decision set is:

- `ALLOW` — requested operation is within policy;
- `DOWNSHIFT` — a smaller approved context tier is required and still satisfies prompt/output capacity;
- `REJECT` — safe headroom cannot be established.

Admission failures are controlled configuration/resource failures, not crashes or generic native OOM failures.

### Conservative uncertainty

The runtime does not invent measurements. Missing Android resource signals remain unknown. Memory-cost estimates carry provenance and confidence so measured evidence cannot be confused with a theoretical or candidate estimate.

Once proactive admission is enabled for a production profile, unsupported or materially incomplete cost/observation state must follow an explicit fallback policy; it must not silently claim safety.

### Reactive pressure handling remains

Android trim/low-memory callbacks remain a second line of defense. Proactive admission reduces avoidable pressure but does not replace platform-driven cleanup.

Normal background/UI-hidden pressure may release idle resources. Critical low-memory pressure may cancel work and release all runtime resources. Cleanup remains idempotent and safe during concurrent cancellation.

### Memory-aware performance

Performance evaluation treats memory as a first-class trade-off. A candidate configuration is not an improvement merely because latency or throughput improves if its peak or residual memory violates policy.

Representative evidence should capture, where the platform exposes it:

- process PSS;
- native heap;
- Java heap;
- available system memory;
- low-memory state;
- thermal status;
- resident model/context counts;
- queue depth;
- memory-pressure actions and admission outcomes.

### Recovery and reuse

A runtime that releases memory must remain reusable unless it is explicitly closed. Tests and physical evidence cover prepare -> generate -> cancel/close/pressure -> prepare/generate again.

Memory after a workload is allowed to plateau because allocators and the OS may retain pages. Validation therefore distinguishes stable retained memory from unbounded growth by measuring repeated-cycle trends and post-release residuals.

## LLM-specific constraints

The Harness does not assume every context cost is pure transformer KV cache. Qwen3.5 may include recurrent/linear-attention state, and llama.cpp owns backend-specific allocation. Memory planning therefore uses measured deltas where available and treats model/context/batch configuration as part of cost identity.

A memory-cost profile is tied to enough identity to prevent unsafe reuse across incompatible configurations. At minimum this includes exact model artifact, backend revision, runtime profile/version and context tier; physical evidence additionally records device/runtime dimensions already owned by Q35 tuning.

## Non-goals

This workstream does not:

- implement a custom native allocator;
- duplicate llama.cpp paging, mmap or kernel-level allocation policy;
- enable multi-model residency or multi-decode concurrency without separate evidence;
- infer exact physical RAM use from GGUF file size alone;
- convert PSS into a precise ownership accounting primitive;
- introduce device-specific JNI branches when runtime policy can remain backend-neutral;
- certify memory behavior from emulator or JVM-only tests.

## Acceptance summary

Memory management is considered strong only when lifecycle correctness, bounded admission, backpressure, shared-runtime residency, observability and physical recovery evidence all agree. A green unit-test suite without representative device evidence is repository correctness, not memory certification.
