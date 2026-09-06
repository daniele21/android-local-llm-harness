---
name: plan-workstream
description: Plan substantial work as observable vertical outcomes with safe parallel subtasks, early convergence and bounded resume checkpoints.
---

# Plan Workstream

Use only when dependencies, parallel ownership or cross-session state genuinely need a durable DAG.

- Plan observable user/system outcomes, not technical layers as independent slices by default.
- Give parallel subtasks non-conflicting `Owns/writes` boundaries and an explicit convergence point; parallel work does not imply stacked publication.
- Put cheap validation beside each subtask and integration/release evidence at the outcome boundary.
- Track only `READY`, `ACTIVE`, `BLOCKED`, `DONE`; no diary/commit narrative.
- Keep the plan within `.engineering/documentation-policy.json` and delete it after durable truth transfers.

Workflow: find canonical owners/current state; state goal/non-goals/invariants; decompose into smallest observable outcomes; define dependencies, write boundaries and ITERATION/INTEGRATION proof; declare affected durable docs; link the active workstream from `docs/current-state.md` only when persistent coordination is justified.

## Resume checkpoint

For multi-session work keep one compact checkpoint in the existing plan when useful: source head/tree/base identity, confirmed facts, excluded hypotheses with evidence pointers, unresolved questions, deferred release obligations and the single next discriminating action. On resume refresh source/base identity before trusting old evidence. Do not create a second progress document.
