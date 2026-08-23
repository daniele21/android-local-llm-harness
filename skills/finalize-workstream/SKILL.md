---
name: finalize-workstream
description: Close a completed active workstream by validating completion, transferring only durable current knowledge to canonical docs/tests, updating repository state, removing temporary planning material and checking for broken/duplicate documentation.
---

# Finalize Workstream

## Principle

Implementation plans are working memory. Code/tests/current durable docs are long-term memory. Git history preserves how the implementation happened.

Completed plans are deleted by default.

## Workflow

1. Read the workstream goal, invariants, DAG, acceptance and validation.
2. Confirm every required slice is `DONE` and no acceptance/evidence claim is unresolved. If real-device/hardware evidence is required but missing, the workstream is not fully complete; keep the relevant state truthful.
3. Inspect the resulting code/contracts/tests rather than trusting the plan's narrative.
4. Extract only knowledge that future maintainers need about the system **as it exists now**:
   - architecture/ownership changes -> `docs/architecture.md`;
   - durable non-obvious feature behavior -> `docs/features/`;
   - material design decision/rationale -> ADR;
   - operational procedure -> existing/new runbook only when genuinely recurring;
   - executable invariant -> tests/tooling when possible.
5. Do not transfer PR numbers, commit diaries, sequence-of-implementation notes or resolved temporary blockers into durable docs.
6. Update `docs/current-state.md` to remove the workstream and expose the next current target/blocker.
7. Delete the completed workstream file by default.
8. Preserve it only when independent audit/regulatory/release/historical value exists; mark it historical and ensure it is not routed as current truth.
9. Search for stale links/references to the removed workstream and update them.
10. Run repository/docs/agent-context validation and relevant project tests.

## Completion questions

- Can a future agent understand current behavior without the plan?
- Is every durable fact in exactly one appropriate canonical owner?
- Did we avoid copying implementation history into current docs?
- Is current state now smaller and truthful?
- Is the completed plan gone unless there is a concrete retention reason?

A successful finalization should normally reduce active documentation/context size.
