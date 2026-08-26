# Contributing

## Development prerequisites

- JDK 17
- Android SDK API 36
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358

Use the committed Gradle wrapper for every local and CI build.

## Local validation

```bash
./gradlew qualityCheck check lintDebug assembleDebug
```

## Formatting and static analysis

```bash
./gradlew spotlessApply
./gradlew detekt
```

Detekt is intentionally executed through its CLI from the root build. The stable Detekt Gradle plugin is not coupled to Android Gradle Plugin 9.

## Dependency locking

Dependency locking is enabled for all configurations. Whenever a dependency is added or intentionally updated, regenerate and review lock state with:

```bash
./gradlew dependencies --write-locks
```

Do not introduce dynamic versions such as `latest.release`, `+` or unbounded ranges.

## Branches and commits

- Branches: `feature/<scope>`, `fix/<scope>`, `chore/<scope>`
- Commits: imperative and scoped when useful
- Do not commit GGUF, GGML or diagnostic export files

## Architectural rules

- Keep product model selection explicit in `AppModelBinding`.
- Do not expose native pointers or `llama.cpp` types outside `backends/llama-cpp`.
- Do not persist prompts or outputs in telemetry by default.
- Add a cache only with a documented key, invalidation policy, size budget and metrics.
- Any native runtime upgrade requires benchmark and sanity-suite comparison.
- Add an ADR for choices that materially constrain public contracts, native source ownership, storage or process boundaries.

## Material ambiguity and failure diagnosis

Resolve requirements from canonical code/contracts/docs/ADRs/consumers/tests before implementation. If two reasonable interpretations still materially change behavior, public contracts, persistence, privacy/security, resource/lifecycle semantics, compatibility, acceptance criteria or meaningful UX, ask the user/owner instead of silently choosing.

When validation fails, classify it as current-change regression, baseline failure, environment/toolchain issue, flaky behavior, stale-base effect or incorrect assumption/contract before editing production code. Fix the owning invariant; do not weaken legitimate gates or repeat symptom patches without a new falsifiable hypothesis.

## Validation depth and execution

Use `scripts/detect_ci_scope.py` through the repository workflow with `auto` as the normal selector:

- `LEAN` — docs/governance/metadata and cheap repository guards;
- `SCOPED` — contained module implementation plus direct consumers/compile/unit/lint;
- `STRONG` — public/shared contracts, Binder/control-plane, persistence, native/JNI, manifest, dependency, R8/ProGuard, packaging/variant or other release-sensitive changes;
- `FULL` — promotion/release, selector/CI/global Gradle/module inventory/toolchain changes, unknown executable paths or explicit full request.

`FULL` is exceptional for ordinary feature PRs. Stronger explicit validation is allowed; silent downgrade below `auto` is forbidden.

Execution capability is separate from depth. Required gates are `AGENT_LOCAL`, `REMOTE_AUTOMATED` or `REAL_ENVIRONMENT`. An automatable deterministic gate must not be delegated to the user solely because the coding agent lacks Android tooling.

## Pre-publication readiness

Use `skills/preflight-change/SKILL.md` before publishing. Refresh the intended `dev` revision, review the complete diff, record exact head/base identity, select the validation profile and classify execution capability.

If selected deterministic gates cannot run agent-local, use `skills/remote-preflight/SKILL.md` and `/preflight` rather than asking the user to run Gradle/R8/Lint/build commands.

Readiness is one of `READY_FOR_CI`, `READY_FOR_REMOTE_PREFLIGHT`, `AUTOMATED_PREFLIGHT_CONFIRMED` or `NOT_READY_FOR_AUTOMATED_PREFLIGHT`. Physical-device/hardware evidence remains separate and may be `PENDING` when the claim requires it.

## Pull-request checks

PRs record exact head/base, selected profile/reason/affected modules, agent-local evidence, remote automated evidence and pending real-environment evidence using `PASS`, `FAIL`, `PENDING` and `N/A`. Promotion to `main` requires `FULL` validation on the exact candidate.
