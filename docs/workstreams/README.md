# Active workstreams

Status: active
Document type: documentation-governance
Owner: repository
Canonical scope: documentation.workstreams
Read when: creating, locating, updating or finalizing a bounded implementation workstream
Last reviewed: 2026-08-23

This directory is the canonical home for repository-level **temporary implementation workstreams** that must preserve dependency, sequencing or handoff state across pull requests or coding agents.

A workstream belongs here only while it is active. It is not a second project-status ledger and it must not duplicate durable feature, architecture, ADR, runbook or release information.

## Lifecycle

1. Create a bounded workstream only when the work cannot be represented safely by the repository current-state ledger plus a durable owner.
2. Link the workstream from [`../current-state.md`](../current-state.md) or the closest durable domain owner when it is actively relevant.
3. Keep implementation sequencing, dependencies, acceptance gates and unresolved blockers here; move durable behavior/decisions to the owning feature, architecture, ADR, runbook or release document.
4. When the workstream is complete, transfer durable knowledge, remove temporary status references and **delete the workstream by default**.
5. Archive only when the completed material has independent audit, regulatory, release-evidence or historical value beyond Git history.

## Existing legacy plans

Some active Harness sources predate repo-template-sw 0.4 and still use `*-plan.md`, `*-progress.md` or domain-local `workstreams/` paths. They remain valid owners until intentionally consolidated; do not create new legacy-style plan/progress pairs. Any touched legacy workstream should converge toward this lifecycle rather than adding another status document.

Git history owns normal implementation history.