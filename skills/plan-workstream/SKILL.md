---
name: plan-workstream
description: Create or reshape a compact active implementation workstream when substantial work needs dependency, parallelism, ownership or progress coordination. Do not use for small coherent changes that need no persistent plan.
---

# Plan Workstream

## Goal

Turn a substantial engineering goal into a bounded execution DAG that coding agents can continue without rereading a narrative history.

## First decide whether a plan is justified

Do not create a workstream when the change can be understood, implemented and validated coherently in one task/PR without persistent coordination.

Create one when at least one is true:

- multiple independently executable slices exist;
- dependencies/blockers must survive across agent sessions;
- multiple agents may work in parallel;
- integration/evidence spans several PRs or environments;
- the work has enough scope that current executable state would otherwise be reconstructed repeatedly.

## Workflow

1. Find the owning architecture/feature sources and current state before planning.
2. State one outcome-oriented Goal and explicit Non-goals.
3. List only invariants that materially constrain implementation.
4. Decompose into the smallest coherent vertical slices with observable acceptance criteria.
5. Give every slice a stable ID and explicit `Owns/writes` boundary.
6. Express dependencies as a DAG. Mark slices parallel only when write ownership is non-conflicting or the integration point is explicit.
7. Use only `READY`, `ACTIVE`, `BLOCKED`, `DONE` states.
8. Name the current executable slice(s); do not make an agent infer them from prose.
9. Put targeted validation beside the slice that needs it and final integration/evidence gates at the appropriate dependency point.
10. Declare durable documentation destinations so completion can transfer current behavior/decisions and delete the plan.
11. Link the workstream once from `docs/current-state.md`.

## Size discipline

Use `docs/workstreams/_template.md`. Keep the workstream under the configured token/line budget. If it grows, split by independently owned domain/workstream rather than appending history.

Do not maintain separate `plan.md`, `progress.md` and `status.md` for the same work. Update state in the DAG table. Completed slices remain as terse `DONE` rows only while the workstream is active; do not append diaries, commit lists or PR narratives.

## Completion standard

The plan is useful when a fresh agent can determine in a small read:

- the intended outcome and exclusions;
- what can execute now;
- what blocks what;
- which paths/contracts each slice owns;
- what may run in parallel;
- how each slice is accepted/validated;
- what durable knowledge must remain after the plan is deleted.
