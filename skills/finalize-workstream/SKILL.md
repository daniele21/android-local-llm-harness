---
name: finalize-workstream
description: Close a completed active workstream by validating completion, transferring only durable current knowledge to canonical docs/tests, updating repository state, removing temporary planning material and checking for broken/duplicate documentation.
---

# Finalize Workstream

## Principle

Implementation plans are working memory. Code/tests/current durable docs are long-term memory. Git history preserves how the implementation happened.

Completed plans are deleted by default. A workstream is not documentation-complete merely because code/tests are complete: affected durable documentation must describe the system as it exists now.

## Workflow

1. Read the workstream goal, invariants, DAG, acceptance and validation.
2. Confirm every required slice is `DONE` and no acceptance/evidence claim is unresolved. Missing required real-device/hardware evidence keeps the relevant claim pending.
3. Inspect resulting code/contracts/tests rather than trusting the plan narrative.
4. Assess documentation impact from final observable behavior using `docs/README.md` when ownership is unclear.
5. Transfer only durable current truth:
   - core purpose/audience/outcome -> README identity sections;
   - prerequisites/setup/run/public configuration/API/UI/examples -> README usage sections;
   - architecture/ownership -> `docs/architecture.md`;
   - durable non-obvious feature behavior -> existing/new `docs/features/` owner;
   - material durable rationale -> ADR;
   - security/trust/data lifecycle -> `SECURITY.md` and/or owning architecture/feature doc;
   - recurring operational procedure -> existing/new runbook;
   - canonical command semantics -> `.engineering/commands.json`;
   - E2E target/environment/fidelity semantics -> `.engineering/e2e.json`;
   - executable invariant -> tests/tooling when possible.
6. Treat README identity and usage independently. Do not rewrite mission/positioning because a feature/command changed; do update usage when the old path becomes incomplete, wrong or misleading.
7. Do not transfer PR numbers, commit diaries, implementation sequence or resolved temporary blockers into durable docs.
8. Update `docs/current-state.md` only for repository-level integrated/blocker/next truth.
9. Delete the completed workstream by default; preserve it only for independent audit/regulatory/release/historical value.
10. Search for stale links, instructions, examples, configuration claims and feature docs affected by the workstream.
11. Run repository/docs/E2E/agent-context validation and relevant project tests.

## Completion questions

- Can a future agent understand current behavior without the plan?
- Can a new user/developer follow the README's current setup/run/use path successfully?
- If README usage changed, did we avoid rewriting still-valid identity/mission copy?
- Are existing feature docs current for the behavior they describe?
- Are E2E environment/fidelity claims current where the work changed them?
- Is every durable fact in one appropriate canonical owner?
- Is current state smaller and truthful?
- Is the completed plan gone unless a concrete retention reason exists?

A successful finalization reduces active planning/context while leaving durable documentation no less truthful than the implementation.
