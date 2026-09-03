---
name: remote-preflight
description: Reuse sufficient exact validation evidence first, then execute only missing deterministic gates through repository-owned automation.
---

# Remote Preflight

Use only after `preflight-change` identifies `REMOTE_AUTOMATED` gates at INTEGRATION/RELEASE.

## Workflow

1. Read `.engineering/commands.json`; resolve live PR head/base and selector output.
2. Search existing successful evidence for the same head + live base and sufficient profile/required gates/E2E identity.
3. If sufficient evidence exists, reuse it and report the source run; do **not** dispatch another heavy validation run.
4. Otherwise trigger `/preflight auto` or the justified stronger profile once.
5. Inspect result/logs. On failure classify `CHANGE_REGRESSION`, `BASELINE_FAILURE`, `ENVIRONMENT`, `FLAKY`, `BASE_DRIFT` or `ASSUMPTION`; fix the owning invariant and reselect gates.
6. Retrigger only because evidence is missing/stale/insufficient or the source changed.

Do not ask the user to execute automatable Gradle/native/build gates. Do not downgrade the selector or suppress legitimate tests to save time.

Preserve trusted-requester, same-repository, exact-head, least-privilege and secret-free execution rules.

## Evidence validity

Collaboration metadata (new PR number, draft/ready state, comments) does not invalidate equivalent source evidence. Source edits, material live-base/dependency changes, changed required gates, or stronger E2E environment/evidence requirements do.

Report stage, profile, risk dimensions, required gates, reused/new run identity, failures and remaining REAL_ENVIRONMENT gaps.
