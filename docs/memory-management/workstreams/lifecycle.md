# Memory lifecycle and shutdown

Status: active
Document type: feature-specification
Owner: runtime-memory
Canonical scope: memory-management.lifecycle
Read when: changing runtime close, cancellation cleanup, context/model release or backend shutdown semantics
Last reviewed: 2026-08-16

## Goal

Make every terminal runtime path converge to one deterministic resource state even when shutdown races a running generation, queued cancellation or session close.

## Existing invariants

- native model/context resources are hidden behind backend handles;
- contexts retain their model owner until context destruction;
- model unload is rejected while native contexts are active;
- session release is idempotent;
- critical memory pressure may mark sessions closing, cancel work and defer model unload until requests terminate.

The workstream preserves these mechanics and strengthens the orchestrator state machine around them.

## Required shutdown semantics

`close()` means no new work is accepted immediately and final native cleanup is guaranteed once in-flight work reaches a cancellation/terminal boundary.

A persistent shutdown-pending state is set before cancellation begins. It remains set until:

```text
sessions == 0
queued requests == 0
active request == none
loaded model == none
backend initialized == false
```

An intermediate deferred unload must not clear shutdown intent.

## Finalization

All paths that can remove the final blocking resource call one shared finalization function. Expected callers include:

- `close()` after initiating cancellation;
- final session release;
- generation `finally` after cancellation/completion;
- critical-memory deferred release.

The function is synchronized on runtime resource ownership and is idempotent.

If shutdown is pending and the runtime is drained, it:

1. unloads the loaded model if present;
2. shuts down the backend if initialized;
3. clears deferred cleanup state;
4. leaves the runtime in its terminal non-resident state.

If ordinary critical-memory release is pending but the runtime is not closed, it unloads the model and keeps the backend reusable according to current runtime policy.

## Failure handling

Cleanup failure is observable and must not pretend resources were released. The runtime enters a degraded/failed state appropriate to the existing public contract and retains enough state for a subsequent cleanup attempt when safe.

No cleanup path catches and discards a native unload/shutdown failure while clearing the owning handle.

## Required tests

Repository tests cover at least:

- close while idle with a warm model;
- close while a session owns a context but no generation is running;
- close during a running generation whose cancellation completes later;
- close with queued work behind the running generation;
- repeated close calls;
- critical low-memory release followed by runtime reuse when the runtime itself was not closed;
- native/backend fakes assert context release -> model unload -> backend shutdown ordering for final close.

A physical-device follow-up repeats cancellation/close cycles against the pinned llama.cpp backend and checks post-release PSS behavior without assuming PSS returns exactly to the cold baseline.

## Out of scope

This slice does not add memory admission, TTL, queue capacity or cost profiling. Keeping those concerns separate allows shutdown hardening to merge without waiting for resource-governor policy.
