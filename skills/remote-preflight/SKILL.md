---
name: remote-preflight
description: Execute and close the narrowest sufficient deterministic validation through repository-owned remote automation when the current coding agent lacks an equivalent local execution environment, without delegating automatable test work to the user or running full CI by default.
---

# Remote Preflight

Use this Skill when `preflight-change` classifies one or more required deterministic gates as `REMOTE_AUTOMATED`.

The governing rules are:

> Do not turn the user into a CI runner because the current agent lacks a shell, checkout, SDK or platform toolchain.

> Do not turn every small PR into a full repository/release build. Select validation from the actual blast radius.

## 1. Confirm remote execution ownership

Read `.engineering/commands.json` and identify the remote trigger, validation selector, exact target PR/head, canonical gates, result/log surface, timeout/retention and trust restrictions.

If no usable remote path exists for a required automatable gate, report `AUTOMATION_CAPABILITY_GAP`. If blast radius cannot be classified safely, report `VALIDATION_SCOPE_GAP` and fail safe stronger while fixing the selector.

## 2. Resolve profile

Default to `auto`:

- `LEAN` — docs/governance/metadata-only or cheap universal guards;
- `SCOPED` — contained owner/module plus direct consumers;
- `STRONG` — shared-contract/native/JNI/persistence/security/packaging/R8/dependency/variant or other release-sensitive changes;
- `FULL` — promotion/release, selector/global-build/dependency-inventory/toolchain changes, unknown executable paths, or explicit full validation.

A stronger `/preflight strong` or `/preflight full` is allowed. Do not silently downgrade below `auto`.

## 3. Trigger exact-head validation

Verify the PR still targets the intended base, record the exact current head SHA, trigger the declared remote preflight once, and correlate the run with that exact head. Do not reuse results after an edit/rebase/replay/base change.

## 4. Inspect result and logs

Record selected profile, reason, affected modules/components/jobs and every required gate as `PASS`, `FAIL`, `PENDING` or `N/A`.

On failure:

1. inspect the failing job/step/log;
2. classify `CHANGE_REGRESSION`, `BASELINE_FAILURE`, `ENVIRONMENT`, `FLAKY`, `BASE_DRIFT` or `ASSUMPTION`;
3. identify violated invariant and owner;
4. check for parity/scope-selection gaps;
5. form a falsifiable repair hypothesis before editing.

Never suppress R8/lint/tests, add broad keep rules blindly, weaken a legitimate gate or downgrade the profile to escape a failure.

## 5. Repair and retrigger autonomously

Patch the owning cause, run any available cheap local checks, refresh head/base/diff, re-run blast-radius selection and retrigger remote preflight. Do not ask the user to execute the same automatable test between repair attempts.

If the same gate fails after a repair, stop symptom patching and form a new root-cause hypothesis. Escalate to the user only for genuine material ambiguity or `REAL_ENVIRONMENT` evidence.

## 6. Profile quality feedback

If `FULL` runs frequently for contained changes, improve path/dependency/scope mapping rather than accepting full-CI-by-default. If a narrow profile misses a deterministic failure in a materially affected component, strengthen mapping so the same class escalates next time.

## 7. Security requirements

Require trusted requester, exact-head pinning, same-repository PR by default, no production/deployment/signing secrets, read-only/no write credentials while PR code executes, separate reporting permission where needed, and bounded timeout/evidence retention.

## 8. Output

```text
HEAD: <revision>
TARGET: <branch>@<revision>
REMOTE_TRIGGER: <mechanism>
VALIDATION_PROFILE: LEAN|SCOPED|STRONG|FULL
PROFILE_REASON: <reason>
AFFECTED_SCOPE: <modules/components/jobs>
REMOTE_GATES:
  <gate>: PASS|FAIL|PENDING|N/A
FAILURE_CLASS: <class|N/A>
REAL_ENVIRONMENT:
  <gate>: PENDING|PASS|N/A
READINESS: AUTOMATED_PREFLIGHT_CONFIRMED|NOT_READY_FOR_AUTOMATED_PREFLIGHT
```
