---
name: preflight-change
description: Establish exact-head integration/release readiness, reuse sufficient existing evidence, and route only missing deterministic gates.
---

# Preflight Change

Use from **INTEGRATION** onward, not as the normal edit loop. `validate-change` owns ITERATION.

## 1. Establish candidate identity

- Record exact source HEAD and live intended target/base.
- Resolve material ambiguity.
- Review the complete diff.
- Make every affected durable documentation owner current with the candidate behavior.
- Treat replacement PRs, draft/ready transitions, comments and other collaboration metadata as non-evidence changes by themselves.

## 2. Select evidence

Run the project selector and record:

- delivery stage;
- risk dimensions;
- concrete required gates;
- `LEAN | SCOPED | STRONG | FULL` shorthand and reason;
- affected E2E journey/environment/UI evidence mode when applicable.

Use FULL for release/main promotion and selector/global-build/unknown-scope changes. Do not escalate simply because a feature label sounds important.

## 3. Reuse before rerun

Before triggering remote automation, look for successful evidence matching the current **head + live target base + sufficient profile/gates + material E2E environment/evidence mode**.

Reuse it when still sufficient. Rerun only missing, stale or insufficient gates. A PR identity change alone does not invalidate source evidence; a source edit, material dependency/base change or changed required gate does.

## 4. Execute or route

Classify gates as `AGENT_LOCAL`, `REMOTE_AUTOMATED` or `REAL_ENVIRONMENT`. Run local gates; send automatable unavailable gates to `remote-preflight`; never make the user the fallback Gradle runner.

For Harnex, keep emulator vs ARM64/native/model/resource claims distinct and select `ASSERTIONS | SCREENSHOTS | FULL_MEDIA` from the changed claim.

## Readiness

`AUTOMATED_PREFLIGHT_CONFIRMED` means all selected deterministic automated gates are satisfied by current exact evidence, whether newly executed or reused. Residual physical/device evidence remains separate and blocks only claims that depend on it.

On failure classify the owning cause before editing and reselect risks/gates after material repairs.
