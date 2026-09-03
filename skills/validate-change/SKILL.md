---
name: validate-change
description: Run the cheapest useful validation during iteration, expand by risk at integration, and keep E2E/environment evidence proportional to the actual claim.
---

# Validate Change

Read `.engineering/commands.json`; read `.engineering/e2e.json` only when a complete workflow/environment claim is affected.

## ITERATION — default

Goal: falsify the current edit quickly.

- format/static checks for the touched surface;
- affected compile;
- focused unit/component tests;
- direct contract tests only when the changed boundary needs them.

Do not default to full diff review, durable-doc freshness, remote preflight, R8/package assembly, emulator/device E2E or physical evidence. A draft PR remains ITERATION.

## INTEGRATION

When the vertical outcome is observable and ready to converge, map:

`changed outcome -> risk dimensions -> required gates -> profile shorthand -> executor`.

Add only the gates implied by risk: affected lint/direct consumers, Binder/contract evidence, native-host tests, package/build validation or critical E2E as needed. `LEAN/SCOPED/STRONG/FULL` summarize the selected gates; they are not permission to run unrelated suites.

## RELEASE

Use FULL plus release-critical build/package/E2E and residual environment evidence. Main promotion is release-stage work.

## Harnex fidelity

- Binder/API35 emulator evidence is `simulated_or_emulated`, never ARM64/native/model/thermal evidence.
- `phone-cold-start` normally needs `SCREENSHOTS`; video is not required unless timing/sequence/lifecycle/release acceptance becomes the claim.
- `local-inference-device-lifecycle` retains physical ARM64/GGUF/resource evidence where automation cannot truthfully substitute it.

UI evidence modes are `ASSERTIONS`, `SCREENSHOTS`, `FULL_MEDIA`. UI presence alone does not force video.

## Failure loop

Classify failures as change regression, baseline, environment, flaky, base drift or assumption. Fix the owning invariant; never weaken a legitimate gate to gain speed. Repeated failure requires a new hypothesis.

Unavailable deterministic gates are `REMOTE_AUTOMATED`, not user work. Hand off to `preflight-change` only when the slice becomes INTEGRATION/RELEASE ready.
