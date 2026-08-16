# Shared-runtime residency

Status: active
Document type: feature-specification
Owner: runtime-memory
Canonical scope: memory-management.shared-runtime-residency
Read when: changing Binder host warm residency, last-consumer disconnect handling or idle model TTL behavior
Last reviewed: 2026-08-16

## Goal

Keep the shared runtime warm long enough to avoid unnecessary reloads while guaranteeing that process liveness cannot retain a model indefinitely after demand disappears.

## Policy model

The host tracks consumer demand separately from process lifetime.

```text
ACTIVE
  |
last consumer disconnects
  v
WARM_IDLE(deadline)
  |                  \
reconnect             deadline expires
  |                     |
  v                     v
ACTIVE               RELEASE_IDLE
```

Android critical memory pressure may transition directly from either state to immediate release behavior.

## Requirements

- Binding/observing the host without an explicit prepare/inference action does not load a model.
- The last consumer disconnect starts an idle deadline only when runtime resources are resident.
- A reconnect before the deadline cancels pending expiry and preserves the runtime.
- Expiry attempts `unloadIdleModel()`; active sessions/work defer release rather than being killed solely because a normal TTL expired.
- Critical low-memory handling remains owned by `RuntimeMemoryPolicy` and may cancel/release active work.
- TTL state is process-scoped and bounded; repeated bind/unbind cycles do not create multiple outstanding timers.
- Host destruction cancels timer infrastructure and closes owned host composition safely.

## Configuration

TTL is explicit configuration with a conservative default selected from product evidence. Tests use an injected clock/scheduler rather than wall-clock sleeps.

A value meaning "retain forever" is not a supported production default. A zero TTL may be useful for deterministic tests or an explicit no-warm-cache mode.

## Metrics

Safe diagnostic fields may include:

- transition into/out of warm idle;
- configured TTL duration;
- idle release attempted/succeeded/deferred;
- reconnect-before-expiry count;
- current loaded-model presence.

No consumer prompt/content is recorded.

## Validation

Tests cover:

- no model load on service creation/bind alone;
- last disconnect schedules exactly one expiry;
- reconnect cancels expiry;
- expiry unloads an idle model;
- expiry defers while a session/request is active;
- low-memory pressure overrides normal TTL behavior;
- closing the graph/service cancels pending expiry.

Physical SR-6 evidence later verifies process death/reconnect behavior and ensures the TTL does not break the same-signer Binder lifecycle.
