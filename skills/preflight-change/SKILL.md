---
name: preflight-change
description: Establish exact-head automated-validation readiness by resolving material ambiguity, verifying target-base freshness, reviewing the complete diff, selecting validation depth from blast radius, classifying execution capability and routing every required deterministic gate without turning the user into a test runner.
---

# Preflight Change

Use immediately before publishing/updating a PR. `validate-change` owns iterative validation; this Skill owns final blast-radius selection, execution routing and readiness.

Read `EXECUTION-CAPABILITY-CONTRACT.md` when execution capability matters.

Governing rules:

> Validation depth follows blast radius: use the narrowest profile that proves the changed invariants.

> CI should confirm locally reproducible failures when the agent has equivalent execution capability.

> An automatable deterministic gate must not be delegated to the user merely because the current agent cannot run it locally.

## 1. Resolve ambiguity, base and diff

- Resolve material ambiguity from canonical contracts/code/docs/ADRs/consumers/tests; ask the user only when reasonable alternatives materially change behavior, contracts, persistence, security/privacy, lifecycle, compatibility, acceptance criteria or meaningful UX.
- Record exact feature HEAD and intended `dev` base. Refresh evidence after any edit/rebase/dependency/base movement.
- Review the complete diff for unrelated/generated/private files, weakened tests, duplicated ownership, stale docs/contracts, missed consumers and resource/security/UX drift.

## 2. Select validation depth

Run the project selector from `.engineering/commands.json` using `auto` and record `LEAN | SCOPED | STRONG | FULL`, reason and affected modules/jobs.

Harness guidance:

- `LEAN` — docs/governance/metadata-only and cheap repository guards.
- `SCOPED` — contained module implementation plus affected compile/unit/lint/direct-consumer evidence.
- `STRONG` — shared/public contracts, Binder/control-plane boundaries, persistence, native/JNI, manifest, dependency, R8/ProGuard, packaging/variant or other cross-boundary/release-sensitive behavior.
- `FULL` — promotion/release, selector/CI/global Gradle/module inventory/toolchain changes, unknown executable paths or explicit full request.

Unknown executable scope fails safe stronger. Selector/build-inventory changes force `FULL`. Do not silently downgrade below `auto`; stronger validation is always allowed.

## 3. Classify execution capability

Assign every required gate in the selected profile to:

- `AGENT_LOCAL` — executable by the current agent on exact HEAD;
- `REMOTE_AUTOMATED` — deterministic/automatable but unavailable in the current agent environment;
- `REAL_ENVIRONMENT` — genuinely requires representative hardware, protected authority/external environment or manual evidence.

Gradle, Kotlin compilation, Lint, R8, unit tests and ordinary unsigned Android builds are `REMOTE_AUTOMATED`, not `REAL_ENVIRONMENT`, when ChatGPT lacks Android tooling.

Run all `AGENT_LOCAL` gates. If any selected gate is `REMOTE_AUTOMATED`, status becomes `READY_FOR_REMOTE_PREFLIGHT` and control passes to `skills/remote-preflight/SKILL.md`; do not ask the user to run Gradle.

## 4. Failure discipline

Classify failures as `CHANGE_REGRESSION`, `BASELINE_FAILURE`, `ENVIRONMENT`, `FLAKY`, `BASE_DRIFT` or `ASSUMPTION`. Identify violated invariant and owner before editing. Never suppress/weaken a legitimate gate merely to go green. If the same gate fails after a repair, form a new falsifiable hypothesis before another edit.

Re-run blast-radius selection after material fixes because the repair may broaden scope.

## 5. Command parity

Local and remote validation must invoke the same project-owned commands/scripts/selector semantics. Workflow YAML may orchestrate environment/cache/evidence but must not own a divergent test policy. If remote runs are routinely broader than required, improve the selector rather than accepting full CI by default.

## Output

```text
HEAD: <revision>
TARGET: <branch>@<revision>
AMBIGUITY: PASS|FAIL
BASE_FRESHNESS: PASS|FAIL
FULL_DIFF_REVIEW: PASS|FAIL
VALIDATION_PROFILE: LEAN|SCOPED|STRONG|FULL
PROFILE_REASON: <reason>
EXECUTION_CAPABILITY: local|mixed|remote-only
AGENT_LOCAL:
  <gate>: PASS|FAIL|N/A
REMOTE_AUTOMATED:
  <gate>: PASS|FAIL|PENDING|N/A
REAL_ENVIRONMENT:
  <gate>: PASS|PENDING|N/A
READINESS: READY_FOR_CI|READY_FOR_REMOTE_PREFLIGHT|AUTOMATED_PREFLIGHT_CONFIRMED|NOT_READY_FOR_AUTOMATED_PREFLIGHT
```
