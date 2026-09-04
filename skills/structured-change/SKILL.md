---
name: structured-change
description: Preserve ownership, contracts, resources, failure, privacy and UX invariants while keeping iteration proportional and deferring publication ceremony to integration readiness.
---

# Structured Change

Use for meaningful behavior, architecture, persistence, API, lifecycle/resource, security, build/runtime or user-facing changes.

## Before editing

- Find the canonical owner and inspect direct consumers/fakes/tests before changing a shared boundary.
- Resolve material ambiguity from repository evidence; ask only when alternatives materially change behavior/contracts/data/security/lifecycle/compatibility/UX.
- Prefer the smallest direct solution; do not add speculative abstractions, dependencies or duplicate state/policy.
- For `product-ui`, preserve the task/journey, hierarchy, progressive disclosure, states/recovery, accessibility/adaptive behavior and canonical component/token owner.

## During implementation

- Define owner, lifetime, bounds, concurrency/backpressure, cancellation, cleanup and failure behavior for significant resources.
- Treat invalid input, partial init, dependency failure, timeout, cancellation, shutdown/restart and user-facing error/recovery states as normal behavior.
- Preserve privacy/data lifecycle: no silent cloud fallback, content logging, secret persistence or destructive migration without explicit contract.
- Preserve build/artifact/runtime semantics from `.engineering/commands.json`; do not introduce a second undocumented run/build/package path.
- When a public/shared boundary changes, inspect every material adapter/consumer and keep policy at its canonical owner.

## Completion semantics

`IMPLEMENTATION_COMPLETE` means the changed behavior and its focused tests are coherent enough to continue/converge. It does **not** require exact-head publication ceremony during `ITERATION`.

`INTEGRATION_READY` additionally requires the 0.9 integration contract: affected durable docs current, target/base refreshed, complete diff reviewed, risk dimensions mapped to required gates, required deterministic evidence executed/routed, and affected E2E selected proportionally.

Use `validate-change` while iterating. Use `preflight-change` only when the coherent vertical outcome is becoming integration/release-ready.

Never weaken a legitimate invariant or test merely to improve velocity; improve stage/gate placement instead.
