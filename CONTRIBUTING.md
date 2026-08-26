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

## Pre-publication readiness

Use `skills/preflight-change/SKILL.md` before pushing or updating a PR for normal readiness confirmation. Refresh the intended `dev` revision, review the complete diff, record exact head/base identity and run every required locally reproducible deterministic gate selected by blast radius. CI/device/hardware-only evidence must be declared `PENDING`, never inferred.

CI should confirm the project-owned deterministic validation semantics, not become the normal edit-test loop for formatting, static analysis, compilation or host tests.

## Pull-request checks

A change is ready only when formatting, static analysis, compilation, affected unit/contract tests, Android lint and applicable debug/internal builds pass from a clean checkout. The PR must distinguish `PASS`, `FAIL`, `PENDING` and `N/A` evidence and record `READY_FOR_CI` only for the exact validated head/base pair.
