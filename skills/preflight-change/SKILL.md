---
name: preflight-change
description: Establish Harnex integration or release readiness using exact candidate/base identity, affected docs, risk-selected gates and equivalent evidence reuse.
---

# Preflight Change

Use when a coherent outcome is ready for `dev` or release; ordinary edits remain ITERATION.

1. State `INTEGRATION` or `RELEASE` and the observable outcome; refresh exact head/tree and intended live base.
2. Review the complete candidate diff for scope, private/generated/debug residue, duplicate ownership, suppressed tests and compatibility/security/resource/UX drift.
3. Make affected durable docs current.
4. Resolve risks -> concrete gates -> profile with the native selector. Record reason, executor (`AGENT_LOCAL`, `REMOTE_AUTOMATED`, `REAL_ENVIRONMENT`) and status for each required gate.
5. For affected complete workflows consult `.engineering/e2e.json`. At integration material UI/UX journeys require `FULL_MEDIA`; emulator proof never establishes physical ARM64 behavior.
6. Reuse trusted successful evidence only when head/tree, material base, required gates/profile and E2E identity remain sufficient. Run only missing/stale/insufficient gates; use `../remote-preflight/SKILL.md` for unavailable deterministic work.
7. On failure use `../validate-change/SKILL.md` diagnosis and re-evaluate invalidated scope.

`INTEGRATION` requires current base/diff/docs, all required automated gates and affected automated critical E2E. Required physical/OEM confirmations remain `DEFERRED_TO_RELEASE`. `RELEASE_READY` additionally requires FULL release evidence and every applicable required real-environment confirmation to PASS.

Return the bounded reporting surface from `.engineering/commands.json`: stage/outcome, head/tree/base, risks/profile, gates with reasons/status, reused/new evidence, affected docs, remaining gaps and next action. Omission of unrelated fields is fine; failed/pending required gates are not.
