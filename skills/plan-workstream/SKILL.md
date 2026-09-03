---
name: plan-workstream
description: Plan substantial work as observable vertical outcomes with safe parallel subtasks and early convergence; do not create persistent plans for small coherent changes.
---

# Plan Workstream

Use only when dependencies, parallel ownership or cross-session state genuinely need a durable DAG.

## Rules

- Plan **observable user/system outcomes**, not technical layers as independent slices by default.
- A technical layer is normally a subtask unless it is independently valuable, mergeable and reviewable.
- Parallel development does not imply stacked publication.
- Give parallel subtasks non-conflicting `Owns/writes` boundaries and an explicit convergence point.
- Prefer short-lived agent branches converging early onto one coherent feature/integration branch.
- Use stacked PRs only when each level genuinely needs independent publication/review; pure stack-sync PRs are a process smell.
- Put cheap targeted validation beside each subtask and integration/release evidence at the outcome boundary.
- Track only `READY`, `ACTIVE`, `BLOCKED`, `DONE`; no diary/commit narrative.

## Workflow

1. Find canonical owners and current integrated state.
2. State one outcome-oriented Goal and explicit Non-goals.
3. Record only material invariants.
4. Decompose into the smallest vertical outcomes with observable acceptance criteria.
5. Split each outcome into parallel-safe subtasks where useful, with `Owns/writes` and convergence point.
6. State dependencies as a DAG and name what is executable now.
7. Define ITERATION checks per subtask and INTEGRATION gates per vertical outcome.
8. Declare which durable docs become current at integration.
9. Link the active workstream once from `docs/current-state.md` only when persistent coordination is justified.
10. Delete the workstream after durable truth transfers to canonical owners.

Keep the plan within `.engineering/documentation-policy.json`. If it grows, split by independent outcome/ownership rather than appending history.
