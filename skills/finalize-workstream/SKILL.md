---
name: finalize-workstream
description: Close a completed workstream by validating completion, transferring durable truth and release obligations, then removing temporary planning material.
---

# Finalize Workstream

Implementation plans are working memory; code/tests/current canonical docs are long-term memory. Completed plans are deleted by default.

1. Read goal/invariants/acceptance/checkpoint and confirm every required slice is `DONE`.
2. Inspect resulting code/contracts/tests rather than trusting the plan narrative.
3. Make affected canonical docs current: README usage/identity only where affected, architecture/feature/security/runbook/config/E2E owners as appropriate.
4. Transfer every deferred real-environment/release obligation to its canonical release owner **before** deleting the plan. A required physical check recorded only in the workstream keeps the plan open until transferred.
5. Update `docs/current-state.md` only for repository-level integrated/blocker/next truth.
6. Delete the plan unless independent audit/regulatory/release historical value justifies retention.
7. Search stale links/instructions/configuration claims and run repository/docs/E2E/agent-context validation plus relevant project tests.

A successful finalization leaves current behavior understandable without the plan and does not silently convert deferred evidence into completed evidence.
